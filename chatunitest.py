#!/usr/bin/env python3
import sys, subprocess, time, json, csv, hashlib, shutil, re, os
from pathlib import Path
from collections import defaultdict
from typing import Dict, Optional

LOC_METHOD_META = Path("/app/output/instr/loc-method.meta")
TARGETS_CSV = Path("/app/input/common/targets.csv")

# ============================================================
# >>> ADDED: New configuration flags
# ============================================================
# Which column from targets.csv should supply the ctext parameter (optional)
CTEXT_COLUMN = "constraint_text"

# NEW: Which column from targets.csv supplies offset (optional)
OFFSET_COLUMN = "offset_in_method"

# NEW: Which column from targets.csv supplies methodsig (optional)
METHODSIG_COLUMN = "method_signature"
# Example: org.apache.ibatis.executor.BaseExecutor#query
METHOD_COLUMN = "method"

# ============================================================


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
        for line in proc.stdout:
            print(line, end="")
            f.write(line)
        proc.wait()
        return proc.returncode


def sha1(s: str) -> str:
    return hashlib.sha1(s.encode("utf-8")).hexdigest()[:10]


def read_loc_method_meta(loc_method_meta: Path, global_log: Path) -> dict:
    """
    Read /app/output/instr/loc-method.meta into a dict:
    {
      "<srcpath>:<line>": "full.class.Name#methodName"
    }
    """
    loc_meta = {}
    if not loc_method_meta.exists():
        log(f"[WARN] loc-method.meta not found: {loc_method_meta}", global_log)
        return loc_meta

    for ln in loc_method_meta.read_text(encoding="utf-8", errors="ignore").splitlines():
        ln = ln.strip()
        if not ln or ln.startswith("#"):
            continue
        # expected format: <file>:<line>,<methodId>
        if "," not in ln:
            continue
        left, mid = ln.split(",", 1)
        left = left.strip()
        mid = mid.strip()
        if left and mid:
            loc_meta[left] = mid
    log(f"Loaded {len(loc_meta)} mappings from {loc_method_meta}", global_log)
    return loc_meta


def normalize_target_file(path_str: str) -> str:
    """
    Normalize target file path format to match loc-method.meta keys.
    loc-method.meta usually uses paths like:
      src/main/java/org/foo/Bar.java:123
    """
    path_str = path_str.strip().lstrip("./")
    return path_str


def find_method_for_target(file: str, line: str, loc_meta: dict) -> str:
    """
    Find methodId for file+line from loc_meta.
    Keys in loc_meta are like: src/main/java/.../X.java:123
    """
    file_n = normalize_target_file(file)
    key = f"{file_n}:{line}"
    return loc_meta.get(key, "")


def build_module_to_methods(loc_meta: dict, targets_csv: Path, global_log: Path, out_json: Path) -> Dict[str, list]:
    module_to_methods = defaultdict(list)
    debug_data = []

    with targets_csv.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            file = row["file"].strip()
            line = row["line"].strip()
            module = row["module"].strip()

            # ============================================================
            # >>> CHANGED: Use configurable column name for ctext/offset/methodsig/method
            # ============================================================
            constraint_text = row.get(CTEXT_COLUMN, "").strip()
            offset_raw = row.get(OFFSET_COLUMN, "").strip()
            methodsig = row.get(METHODSIG_COLUMN, "").strip()
            method_full = row.get(METHOD_COLUMN, "").strip()

            offset = None
            if offset_raw:
                try:
                    # requirement: whatever offset value we get from targets.csv, add 1
                    offset = int(offset_raw) + 1
                except ValueError:
                    log(f"[WARN] Bad offset value '{offset_raw}' for {file}:{line} (expected int). Ignoring.", global_log)
                    offset = None

            # Prefer method column if present; otherwise map via loc-method.meta
            mid = method_full if method_full else find_method_for_target(file, line, loc_meta)

            debug_data.append({
                "module": module, "file": file, "line": line,
                "methodId": mid, "constraint_text": constraint_text,
                "offset": offset, "methodsig": methodsig, "method": method_full
            })

            if mid:
                module_to_methods[module].append({
                    "methodId": mid,
                    "line": line,
                    "constraint_text": constraint_text,
                    "offset": offset,
                    "methodsig": methodsig,
                    "method": method_full
                })
            else:
                log(f"[WARN] No method found for {file}:{line}", global_log)

    total = sum(len(v) for v in module_to_methods.values())
    log(f"Mapped {total} methods across {len(module_to_methods)} modules", global_log)

    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(debug_data, indent=2), encoding="utf-8")
    log(f"Wrote mapping debug JSON to: {out_json}", global_log)

    return module_to_methods


def main():
    TOOL_DIR = Path("/app/input/tool").resolve()
    OUTPUT_DIR = Path("/app/output").resolve()
    GLOBAL_LOG = OUTPUT_DIR / "run_chatunitest.log"
    OUT_JSON = OUTPUT_DIR / "targets_method_map.json"

    # read env from prior steps
    api_keys = os.environ.get("API_KEYS", "")
    model = os.environ.get("MODEL", "")
    if not api_keys:
        # fallback: script caller can set via env or leave empty (mvn may still run if plugin config has keys)
        api_keys = ""
    if not model:
        model = "gpt-4o-mini"

    # Maven args
    DAPI_KEYS = f"-DapiKeys={api_keys}" if api_keys else ""
    DMODEL = f"-Dmodel={model}" if model else ""

    log("[INFO] Starting chatunitest.py", GLOBAL_LOG)
    log(f"[INFO] TOOL_DIR={TOOL_DIR}", GLOBAL_LOG)
    log(f"[INFO] TARGETS_CSV={TARGETS_CSV}", GLOBAL_LOG)
    log(f"[INFO] LOC_METHOD_META={LOC_METHOD_META}", GLOBAL_LOG)

    loc_meta = read_loc_method_meta(LOC_METHOD_META, GLOBAL_LOG)
    if not TARGETS_CSV.exists():
        log(f"[ERROR] targets.csv not found: {TARGETS_CSV}", GLOBAL_LOG)
        sys.exit(2)

    module_to_methods = build_module_to_methods(loc_meta, TARGETS_CSV, GLOBAL_LOG, OUT_JSON)

    # Run commands per module/method
    for module, items in module_to_methods.items():
        for item in items:
            mid = item.get("methodId", "")
            line = item.get("line", "")
            ctext = item.get("constraint_text", "")
            ctext_arg = f" -Dctext='{ctext}'" if ctext else ""

            offset = item.get("offset")
            methodsig = item.get("methodsig", "")
            offset_arg = f" -Doffset={offset}" if offset is not None else ""
            methodsig_arg = f" -Dmethodsig='{methodsig}'" if methodsig else ""

            # === Correctly split class & method ===
            if "#" in mid:
                cls, mname = mid.split("#", 1)
            elif "." in mid:
                cls = mid.rsplit(".", 1)[0]
                mname = mid.rsplit(".", 1)[1]
            else:
                cls, mname = mid, ""

            # Build method directory
            method_dir = OUTPUT_DIR / "gentest" / sha1(f"{module}:{mid}:{line}")
            method_dir.mkdir(parents=True, exist_ok=True)
            logfile = method_dir / "chatunitest_method.log"

            cmd = (
                f"source /app/init_env.sh && "
                f"mvn -B io.github.zju-aces-ise:chatunitest-maven-plugin:2.1.1:method "
                f"{DAPI_KEYS} {DMODEL} "
                f"-Durl=https://api.openai.com/v1/chat/completions "
                f"-DonlyTargetLines=true -DphaseType=HITS "
                f"-DselectClass={cls} -DselectMethod={mname} "
                f"-Dlines={line}{ctext_arg}{offset_arg}{methodsig_arg} -Dtemperature=0"
            )

            module_dir = TOOL_DIR if module in ["", "."] else TOOL_DIR / module

            log(f"\n[RUN] Executing in: {module_dir}", GLOBAL_LOG)
            log(f"[RUN] Command: {cmd}\n", GLOBAL_LOG)
            print(f"\n>>> Running in {module_dir}\n{cmd}\n")

            rc = run_silent(cmd, logfile, cwd=module_dir)
            if rc != 0:
                log(f"[WARN] Command failed with rc={rc} for methodId={mid}", GLOBAL_LOG)

    log("[INFO] Done.", GLOBAL_LOG)


if __name__ == "__main__":
    main()
