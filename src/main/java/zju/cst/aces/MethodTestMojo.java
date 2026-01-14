package zju.cst.aces;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import zju.cst.aces.api.Project;
import zju.cst.aces.api.Task;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ProjectImpl;
import zju.cst.aces.api.impl.RunnerImpl;
import zju.cst.aces.logger.MavenLogger;
import zju.cst.aces.parser.ProjectParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Mojo(name = "method")
public class MethodTestMojo extends AbstractMojo {
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    public MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    public MavenProject project;

    @Parameter(property = "selectClass", required = true)
    public String selectClass;

    @Parameter(property = "selectMethod", required = true)
    public String selectMethod;

    @Parameter(property = "testOutput")
    public File testOutput;

    @Parameter(defaultValue = "/tmp/chatunitest-info", property = "tmpOutput")
    public File tmpOutput;

    @Parameter(property = "promptPath")
    public File promptPath;

    @Parameter(property = "examplePath", defaultValue = "${project.basedir}/exampleUsage.json")
    public File examplePath;

    @Parameter(property = "url", defaultValue = "https://api.gptsapi.net/v1/chat/completions")
    public String url;

    @Parameter(property = "model", defaultValue = "gpt-3.5-turbo")
    public String model;

    @Parameter(property = "apiKeys", required = true)
    public String[] apiKeys;

    @Parameter(property = "stopWhenSuccess", defaultValue = "true")
    public boolean stopWhenSuccess;

    @Parameter(property = "noExecution", defaultValue = "false")
    public boolean noExecution;

    @Parameter(alias = "thread", property = "thread", defaultValue = "true")
    public boolean enableMultithreading;

    @Parameter(alias = "ruleRepair", property = "ruleRepair", defaultValue = "true")
    public boolean enableRuleRepair;

    @Parameter(alias = "obfuscate", property = "obfuscate", defaultValue = "false")
    public boolean enableObfuscate;

    @Parameter(alias = "merge", property = "merge", defaultValue = "true")
    public boolean enableMerge;

    @Parameter(property = "obfuscateGroupIds")
    public String[] obfuscateGroupIds;

    @Parameter(property = "maxThreads", defaultValue = "0")
    public int maxThreads;

    @Parameter(property = "testNumber", defaultValue = "5")
    public int testNumber;

    @Parameter(property = "maxRounds", defaultValue = "5")
    public int maxRounds;

    @Parameter(property = "maxPromptTokens", defaultValue = "-1")
    public int maxPromptTokens;

    @Parameter(property = "minErrorTokens", defaultValue = "500")
    public int minErrorTokens;

    @Parameter(property = "maxResponseTokens", defaultValue = "1024")
    public int maxResponseTokens;

    @Parameter(property = "sleepTime", defaultValue = "0")
    public int sleepTime;

    @Parameter(property = "dependencyDepth", defaultValue = "1")
    public int dependencyDepth;

    @Parameter(property = "temperature", defaultValue = "0.5")
    public Double temperature;

    @Parameter(property = "topP", defaultValue = "1")
    public int topP;

    @Parameter(property = "frequencyPenalty", defaultValue = "0")
    public int frequencyPenalty;

    @Parameter(property = "presencePenalty", defaultValue = "0")
    public int presencePenalty;

    @Parameter(property = "proxy", defaultValue = "null:-1")
    public String proxy;

    @Parameter(property = "phaseType", defaultValue = "CHATUNITEST")
    public String phaseType;

    @Parameter(property = "sampleSize", defaultValue = "10")
    public int sampleSize;

    // --- minimal additions for HITS prompts ---
    @Parameter(property = "lines", defaultValue = "-1")
    public int lines;

    @Parameter(property = "onlyTargetLines", defaultValue = "false")
    public boolean onlyTargetLines;

    @Parameter(property = "fullFM", defaultValue = "false")
    public boolean fullFM;

    @Parameter(property = "ctext")
    private String ctext;

    @Parameter(property = "offset")
    private Integer offset;

    @Parameter(property = "methodsig")
    private String methodsig;

    // ------------------------------------------

    @Component(hint = "default")
    public DependencyGraphBuilder dependencyGraphBuilder;

    public static Log log;
    public Config config;

    @Override
    public void execute() throws MojoExecutionException {
        init();
        try {
            if (selectClass == null || selectClass.trim().isEmpty()) {
                throw new MojoExecutionException("selectClass is required.");
            }
            if (selectMethod == null || selectMethod.trim().isEmpty()) {
                throw new MojoExecutionException("selectMethod is required.");
            }
            new Task(config, new RunnerImpl(config)).startMethodTask(selectClass, selectMethod);
        } catch (Exception e) {
            log.error("Error during ChatUniTest execution: " + e.getMessage(), e);
            throw new MojoExecutionException("chatunitest:method failed", e);
        }
    }

    public void init() throws MojoExecutionException {
        log = getLog();
        MavenLogger mLogger = new MavenLogger(log);

        File effectivePromptDir = promptPath;
        if ("HITS".equalsIgnoreCase(phaseType)) {
            try {
                effectivePromptDir = prepareHitsPromptDir(promptPath, log, lines, onlyTargetLines, fullFM, this.project, this.selectClass, this.ctext, this.offset,this.methodsig);
            } catch (IOException ex) {
                throw new MojoExecutionException("Failed to prepare HITS prompts", ex);
            }
        }

        Project myProject = new ProjectImpl(project, listClassPaths(project, dependencyGraphBuilder));
        Config.ConfigBuilder builder = new Config.ConfigBuilder(myProject)
                .logger(mLogger)
                .promptPath(effectivePromptDir)
                .examplePath(examplePath.toPath())
                .apiKeys(apiKeys)
                .enableMultithreading(enableMultithreading)
                .enableRuleRepair(enableRuleRepair)
                .tmpOutput(tmpOutput.toPath())
                .testOutput(testOutput == null ? null : testOutput.toPath())
                .stopWhenSuccess(stopWhenSuccess)
                .noExecution(noExecution)
                .enableObfuscate(enableObfuscate)
                .enableMerge(enableMerge)
                .obfuscateGroupIds(obfuscateGroupIds)
                .maxThreads(maxThreads)
                .testNumber(testNumber)
                .maxRounds(maxRounds)
                .sleepTime(sleepTime)
                .dependencyDepth(dependencyDepth)
                .model(model)
                .maxResponseTokens(maxResponseTokens)
                .maxPromptTokens(maxPromptTokens)
                .minErrorTokens(minErrorTokens)
                .url(url)
                .temperature(temperature)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .proxy(proxy)
                .phaseType(phaseType)
                .sampleSize(sampleSize);

        config = builder.build();
        config.setPluginSign(phaseType);
        config.print();
    }

    public static List<String> listClassPaths(MavenProject project, DependencyGraphBuilder dependencyGraphBuilder) {
        List<String> classPaths = new ArrayList<>();
        if (project.getPackaging().equals("jar")) {
            Path artifactPath = Paths.get(project.getBuild().getDirectory())
                    .resolve(project.getBuild().getFinalName() + ".jar");
            if (!artifactPath.toFile().exists()) {
                throw new RuntimeException("In TestCompiler.listClassPaths: " + artifactPath + " does not exist. Run mvn install first.");
            }
            classPaths.add(artifactPath.toString());
        }
        try {
            classPaths.addAll(project.getCompileClasspathElements());
            Class<?> clazz = project.getClass();
            Field privateField = clazz.getDeclaredField("projectBuilderConfiguration");
            privateField.setAccessible(true);
            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest((DefaultProjectBuildingRequest) privateField.get(project));
            buildingRequest.setProject(project);
            DependencyNode root = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
            Set<DependencyNode> depSet = new HashSet<>();
            zju.cst.aces.parser.ProjectParser.walkDep(root, depSet);
            for (DependencyNode dep : depSet) {
                if (dep.getArtifact().getFile() != null) {
                    classPaths.add(dep.getArtifact().getFile().getAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return classPaths;
    }

    private static File prepareHitsPromptDir(File srcPromptDir,
                                             Log log,
                                             int lines,
                                             boolean onlyTargetLines,
                                             boolean fullFM,
                                             MavenProject project,
                                             String selectClass,
                                             String constraintText,
                                             Integer offset,
                                             String methodSig) throws IOException {
        Path tmpDir = Files.createTempDirectory("chatunitest-prompts-");
        File dest = tmpDir.toFile();

        List<String> promptFiles = Arrays.asList(
                "hits_gen.ftl",
                "hits_gen_slice.ftl",
                "hits_repair.ftl",
                "hits_system_gen.ftl",
                "hits_system_repair.ftl"
        );

        if (srcPromptDir != null && srcPromptDir.isDirectory()) {
            copyDir(srcPromptDir.toPath(), dest.toPath());
        } else {
            for (String name : promptFiles) {
                try (InputStream in = MethodTestMojo.class.getClassLoader()
                        .getResourceAsStream("prompt/" + name)) {
                    if (in == null) continue;
                    Path out = dest.toPath().resolve(name);
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        Map<String, String> replaceMap = new HashMap<>();
        String codeLine = readLineOfClass(project, selectClass, lines);
        replaceMap.put("${lines_to_test}", codeLine);
        //replaceMap.put("${constraint_text}", constraintText == null ? "" : constraintText);
        replaceMap.put("${only_target_lines}", String.valueOf(onlyTargetLines));
        String fullCode = readWholeClass(project, selectClass);   // new helper (below)
        String annotated = fullCode;

        if (fullCode != null && !fullCode.isEmpty() && methodSig != null && offset != null) {
            annotated = annotateMethodAtOffset(fullCode, methodSig, offset); // your helper
        }

        replaceMap.put("${full_fm}", annotated == null ? "" : annotated);

        if (constraintText != null) {
            replaceMap.put("${constraint_text}", constraintText);
        }

        if (offset != null) {
            replaceMap.put("${offset}", offset.toString());
        }

        if (methodSig != null) {
            replaceMap.put("${methodsig}", methodSig);
        }


        inject(dest.toPath().resolve("hits_gen.ftl"), replaceMap);
        inject(dest.toPath().resolve("hits_gen_slice.ftl"), replaceMap);
        inject(dest.toPath().resolve("hits_repair.ftl"), replaceMap);
        inject(dest.toPath().resolve("hits_system_repair.ftl"), replaceMap);

        log.info("Prompt path >>> " + dest.getAbsolutePath());
        return dest;
    }

    private static void inject(Path file, Map<String, String> kv) throws IOException {
        if (!Files.exists(file)) return;
        String s = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (Map.Entry<String, String> e : kv.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        Files.write(file, s.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyDir(Path src, Path dst) throws IOException {
        if (!Files.exists(src)) return;
        Files.walk(src).forEach(p -> {
            Path rel = src.relativize(p);
            Path out = dst.resolve(rel);
            try {
                if (Files.isDirectory(p)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(p, out, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }
    // --- helper: read the exact source line of a fully-qualified class ---
    private static String readLineOfClass(MavenProject project, String fqcn, int line) {
        try {
            if (fqcn == null || line < 1) return "";
            String rel = fqcn.replace('.', '/') + ".java";
            java.nio.file.Path src = project.getBasedir().toPath()
                    .resolve("src/main/java").resolve(rel);
            if (!java.nio.file.Files.exists(src)) {
                src = project.getBasedir().toPath()
                        .resolve("src/test/java").resolve(rel);
            }
            if (!java.nio.file.Files.exists(src)) return "";
            java.util.List<String> all = java.nio.file.Files.readAllLines(
                    src, java.nio.charset.StandardCharsets.UTF_8);
            return (line <= all.size()) ? all.get(line - 1) : "";
        } catch (Exception e) {
            return "";
        }
    }
    private static class ParsedSig {
        final String name;
        final java.util.List<String> paramTypes; // normalized simple names
        ParsedSig(String name, java.util.List<String> paramTypes) {
            this.name = name;
            this.paramTypes = paramTypes;
        }
    }

    private static ParsedSig parseMethodSig(String methodsig) {
        // methodsig: query(MappedStatement,Object,RowBounds,...)
        int lp = methodsig.indexOf('(');
        int rp = methodsig.lastIndexOf(')');
        if (lp < 0 || rp < lp) throw new IllegalArgumentException("Bad methodsig: " + methodsig);

        String name = methodsig.substring(0, lp).trim();
        String inside = methodsig.substring(lp + 1, rp).trim();

        java.util.List<String> types = new java.util.ArrayList<>();
        if (!inside.isEmpty()) {
            for (String t : inside.split(",")) {
                types.add(normalizeType(t));
            }
        }
        return new ParsedSig(name, types);
    }

    private static String normalizeType(String t) {
        // normalize things like "java.lang.String", "List<String>", "@Ann final Foo...", "Foo..." varargs
        t = t.trim();
        t = t.replace("...", "[]"); // treat varargs as array
        // remove annotations and modifiers words often seen in params
        t = t.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
        t = t.replaceAll("\\bfinal\\b\\s*", "");
        // strip generics
        t = t.replaceAll("<[^>]*>", "");
        t = t.trim();
        // take simple name
        int lastDot = t.lastIndexOf('.');
        if (lastDot >= 0) t = t.substring(lastDot + 1);
        return t.trim();
    }
    private static String annotateMethodAtOffset(String fullCode, String methodsig, Integer offset) {
        if (fullCode == null || methodsig == null || offset == null) return fullCode;
        if (offset <= 0) return fullCode;

        ParsedSig sig = parseMethodSig(methodsig);

        // Find candidate method declarations with same name.
        // This is intentionally permissive; we’ll verify params afterwards.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?s)(?:public|protected|private|static|final|synchronized|native|abstract|\\s)+" +
                        ".*?\\b" + java.util.regex.Pattern.quote(sig.name) + "\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[^\\{]+)?\\{"
        );
        java.util.regex.Matcher m = p.matcher(fullCode);

        while (m.find()) {
            int headerStart = m.start();
            int braceOpen = fullCode.indexOf('{', m.end() - 1);
            if (braceOpen < 0) continue;

            String paramList = m.group(1);
            java.util.List<String> declTypes = extractParamTypesFromDeclaration(paramList);

            if (!sameTypes(sig.paramTypes, declTypes)) continue;

            // Extract full method text using brace matching
            int methodEnd = findMatchingBrace(fullCode, braceOpen);
            if (methodEnd < 0) continue;

            String methodText = fullCode.substring(headerStart, methodEnd + 1);
            String annotated = insertCommentInsideMethod(methodText, offset);

            // Replace in fullCode (first matching exact method)
            return fullCode.substring(0, headerStart) + annotated + fullCode.substring(methodEnd + 1);
        }

        // If not found, return unchanged
        return fullCode;
    }

    private static java.util.List<String> extractParamTypesFromDeclaration(String paramList) {
        java.util.List<String> types = new java.util.ArrayList<>();
        String trimmed = paramList.trim();
        if (trimmed.isEmpty()) return types;

        // Split by commas (good enough for typical Java params; generics already stripped later)
        String[] parts = trimmed.split(",");
        for (String part : parts) {
            String s = part.trim();
            if (s.isEmpty()) continue;

            // Remove generics to avoid commas inside <...> causing issues in rare cases
            s = s.replaceAll("<[^>]*>", "");

            // Parameter tokens: [annotations/modifiers] Type Name
            // We take everything except the last token as "type"
            String[] toks = s.trim().split("\\s+");
            if (toks.length == 1) {
                // Strange, but treat it as type-only
                types.add(normalizeType(toks[0]));
            } else {
                StringBuilder type = new StringBuilder();
                for (int i = 0; i < toks.length - 1; i++) {
                    type.append(toks[i]).append(" ");
                }
                types.add(normalizeType(type.toString()));
            }
        }
        return types;
    }

    private static boolean sameTypes(java.util.List<String> a, java.util.List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    private static int findMatchingBrace(String s, int openBraceIdx) {
        int depth = 0;
        for (int i = openBraceIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    private static String insertCommentInsideMethod(String methodText, int offset) {
        int braceOpen = methodText.indexOf('{');
        if (braceOpen < 0) return methodText;

        int bodyStart = braceOpen + 1; // right after '{'
        String header = methodText.substring(0, bodyStart);
        String bodyAndClose = methodText.substring(bodyStart);

        // Split body into lines
        String[] lines = bodyAndClose.split("\n", -1);

        // Count lines “inside the method” starting from first line after '{'
        // We’ll annotate the line at index (offset-1) if it exists and is not just the closing brace region.
        int targetIdx = offset - 1;
        if (targetIdx < 0 || targetIdx >= lines.length) return methodText;

        lines[targetIdx] = "//This is line " + offset + "\n" + lines[targetIdx];

        String newBody = String.join("\n", lines);
        return header + newBody;
    }
    static String readWholeClass(MavenProject project, String fqcn) {
        try {
            if (fqcn == null) return "";
            String rel = fqcn.replace('.', '/') + ".java";
            Path src = project.getBasedir().toPath().resolve("src/main/java").resolve(rel);
            if (!Files.exists(src)) {
                src = project.getBasedir().toPath().resolve("src/test/java").resolve(rel);
            }
            if (!Files.exists(src)) return "";
            return Files.readString(src, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }




}