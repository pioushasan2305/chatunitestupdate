#!/usr/bin/env python3
import sys, subprocess, time, json, csv, hashlib, shutil, re, os
from pathlib import Path
from collections import defaultdict
from typing import Dict, Optional

LOC_METHOD_META = Path("/app/output/instr/loc-method.meta")
TARGETS_CSV = Path("/app/input/common/targets.csv")


def log(msg: str, global_log: Path):
    """Simplified log without timestamps or step headers."""
    print(msg)
    global_log.parent.mkdir(parents=True, exist_ok=True)
    with global_log.open("a", encoding="utf-8") as f:
        f.write(msg + "\n")


def run_silent(cmd: str, logfile: Path, cwd: Optional[Path] = None) -> int:
    """
    Run a shell command, showing live output in terminal while also writing it to logfile.
    """
    logfile.parent.mkdir(parents=True, exist_ok=True)
    with logfile.open("w", encoding="utf-8") as f:
        proc = subprocess.Popen(
            cmd,
            shell=True,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            executable="/bin/bash"
        )
        # Stream output live and write to log file
        for line in proc.stdout:
            print(line, end="")  # live output to terminal
            f.write(line)
        proc.wait()
        return proc.returncode


def sha1_key(s: str) -> str:
    return hashlib.sha1(s.encode("utf-8")).hexdigest()


def zip_and_move(src_dir: Path, dest_zip: Path, global_log: Path):
    if not src_dir.exists():
        log(f"Skip zipping {src_dir} (not found)", global_log)
        return
    log(f"Zipping {src_dir} → {dest_zip}", global_log)
    tmp_zip = dest_zip.with_suffix("")
    shutil.make_archive(str(tmp_zip), "zip", str(src_dir))
    dest_zip.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(tmp_zip) + ".zip", dest_zip)
    log(f"Created {dest_zip.name}", global_log)


def find_method_for_target(file: str, line: str, loc_meta: dict) -> Optional[str]:
    target_key = f"{file}#{line}"
    best_match, best_len = None, 0
    for loc, meta in loc_meta.items():
        if target_key.endswith(loc) and len(loc) > best_len:
            best_match = meta.get("methodId")
            best_len = len(loc)
    return best_match


def build_module_to_methods(loc_meta: dict, targets_csv: Path, global_log: Path, out_json: Path) -> Dict[str, list]:
    module_to_methods = defaultdict(list)
    debug_data = []

    with targets_csv.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            file = row["file"].strip()
            line = row["line"].strip()
            module = row["module"].strip()
            constraint_text = row.get("constraint_text", "").strip()
            mid = find_method_for_target(file, line, loc_meta)
            debug_data.append({"module": module, "file": file, "line": line, "methodId": mid, "constraint_text": constraint_text})
            if mid:
                module_to_methods[module].append({
                    "methodId": mid,
                    "line": line,
                    "constraint_text": constraint_text
                })
            else:
                log(f"[WARN] No method found for {file}:{line}", global_log)

    total = sum(len(v) for v in module_to_methods.values())
    log(f"Mapped {total} methods across {len(module_to_methods)} modules", global_log)

    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(debug_data, indent=2), encoding="utf-8")
    log(f"Wrote mapping JSON to {out_json}", global_log)
    return module_to_methods


def get_project_name() -> str:
    with open("/app/.project.meta", encoding="utf-8") as f:
        for line in f:
            if line.startswith("project_name="):
                project_full = line.strip().split("=", 1)[1]
                break
        return re.sub(r"-\d+(\.\d+)*([-a-zA-Z0-9]*)?$", "", project_full)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 run_tool.py <tool_name_with_version>")
        sys.exit(1)

    TOOL_FULL = sys.argv[1]
    TOOL_BASE = TOOL_FULL.split("-")[0]

    OUTPUT_DIR = Path(f"/app/output/gentest/{TOOL_FULL}")
    GLOBAL_LOG = Path(f"/app/output/gentest/{TOOL_FULL}.log")
    LOG_ROOT = Path(f"/app/log/gentest/{TOOL_FULL}")
    TOOL_DIR = Path(f"/app/{TOOL_FULL}")

    # === API and Model vars ===
    DAPI_KEYS = f"-DapiKeys='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'"
    DMODEL = "-Dmodel=gpt-4o"

    start_total = time.time()
    log(f"{TOOL_FULL.upper()} pipeline started", GLOBAL_LOG)

    # --- CLEAN ---
    subprocess.call(["bash", "/app/input/common/clean.sh", "gentest", TOOL_FULL])
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    LOG_ROOT.mkdir(parents=True, exist_ok=True)
    GLOBAL_LOG.touch(exist_ok=True)

    # --- BUILD MODULE→METHOD MAP ---
    if not LOC_METHOD_META.exists():
        log("ERROR: loc-method.meta not found!", GLOBAL_LOG)
        return
    loc_meta = json.loads(LOC_METHOD_META.read_text(encoding="utf-8"))
    mapping_json = OUTPUT_DIR / "module_method_map.json"
    module_to_methods = build_module_to_methods(loc_meta, TARGETS_CSV, GLOBAL_LOG, mapping_json)

    # --- RUN PIPELINE ---
    for module, methods in module_to_methods.items():
        log(f"=== MODULE: {module} ({len(methods)} methods) ===", GLOBAL_LOG)

        info_root = Path(f"/tmp/chatunitest-info/{get_project_name()}/{module}/build")
        info_root.mkdir(parents=True, exist_ok=True)
        log(f"Ensured build dir: {info_root}", GLOBAL_LOG)

        install_log = OUTPUT_DIR / f"{module}-install.log"
        rc = run_silent(
            f"source /app/init_env.sh && mvn -B $MVN_BASE_OPTIONS install -pl {module} -am -DskipTests",
            install_log, cwd=TOOL_DIR,
        )
        status = "OK" if rc == 0 else "FAIL"
        log(f"[{status}] Installed module {module}", GLOBAL_LOG)

        gen_total = 0.0
        for item in methods:
            mid = item["methodId"]
            line = item["line"]
            ctext = item["constraint_text"]

            key = sha1_key(mid)
            method_dir = LOG_ROOT / key
            method_dir.mkdir(parents=True, exist_ok=True)
            (method_dir / "method.txt").write_text(mid + "\n", encoding="utf-8")
            method_log = method_dir / f"{TOOL_FULL}.log"

            # === Correctly split class & method ===
            if "#" in mid:
                cls, mname = mid.split("#", 1)
            elif "." in mid:
                cls = mid.rsplit(".", 1)[0]
                mname = mid.rsplit(".", 1)[1]
            else:
                cls, mname = mid, ""

            cmd = (
                f"source /app/init_env.sh && "
                f"mvn -B io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1:method "
                f"{DAPI_KEYS} {DMODEL} "
                f"-Durl=https://api.openai.com/v1/chat/completions "
                f"-DonlyTargetLines=true -DphaseType=HITS "
                f"-DselectClass={cls} -DselectMethod={mname} "
                f"-Dlines={line} -Dctext='{ctext}' -Dtemperature=0"
            )

            module_dir = TOOL_DIR if module in ["", "."] else TOOL_DIR / module

            # === Visible logs for current command ===
            log(f"\n[RUN] Executing in: {module_dir}", GLOBAL_LOG)
            log(f"[RUN] Command: {cmd}\n", GLOBAL_LOG)
            print(f"\n>>> Running ChatUniTest in: {module_dir}")
            print(f">>> Command: {cmd}\n")

            t0 = time.time()
            rc = run_silent(cmd, method_log, cwd=module_dir)
            gen_total += time.time() - t0

            status = "OK" if rc == 0 else "FAIL"
            log(f"[{status}] {mid} → {method_log}", GLOBAL_LOG)
            time.sleep(60)

        module_dur = int(gen_total)
        log(f"Module {module} generation time: {module_dur//60}m {module_dur%60}s", GLOBAL_LOG)

    tmp_info = Path("/tmp/chatunitest-info")
    info_zip = OUTPUT_DIR / "chatunitest-info.zip"
    runtime_zip = OUTPUT_DIR / "runtimelog.zip"
    zip_and_move(tmp_info, info_zip, GLOBAL_LOG)
    zip_and_move(LOG_ROOT, runtime_zip, GLOBAL_LOG)
    if tmp_info.exists():
        shutil.rmtree(tmp_info, ignore_errors=True)
        log(f"Cleaned {tmp_info}", GLOBAL_LOG)

    total_dur = int(time.time() - start_total)
    log(f"{TOOL_FULL.upper()} pipeline complete", GLOBAL_LOG)
    log(f"Total runtime: {total_dur//3600:02d}:{(total_dur%3600)//60:02d}:{total_dur%60:02d}", GLOBAL_LOG)


if __name__ == "__main__":
    main()
