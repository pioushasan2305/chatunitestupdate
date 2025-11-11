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

@Mojo(name = "class")
public class ClassTestMojo extends AbstractMojo {
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    public MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    public MavenProject project;

    @Parameter(property = "selectClass", required = true)
    public String selectClass;

    @Parameter(property = "selectMethod")
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
            new Task(config, new RunnerImpl(config)).startClassTask(selectClass);
        } catch (Exception e) {
            log.error("Error during ChatUniTest execution: " + e.getMessage(), e);
            throw new MojoExecutionException("chatunitest:class failed", e);
        }
    }

    public void init() throws MojoExecutionException {
        log = getLog();
        MavenLogger mLogger = new MavenLogger(log);

        File effectivePromptDir = promptPath;
        if ("HITS".equalsIgnoreCase(phaseType)) {
            try {
                effectivePromptDir = effectivePromptDir = prepareHitsPromptDir(promptPath, log, lines, onlyTargetLines, fullFM, this.project, this.selectClass, this.ctext);
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
            ProjectParser.walkDep(root, depSet);
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
                                             String constraintText) throws IOException {
        Path tmpDir = Files.createTempDirectory("chatunitest-prompts-");
        File dest = tmpDir.toFile();

        List<String> promptFiles = Arrays.asList(
                "hits_gen.ftl",
                "hits_gen_slice.ftl",
                "hits_repair.ftl",
                "hits_system_gen.ftl",      // untouched logically
                "hits_system_repair.ftl"
        );

        if (srcPromptDir != null && srcPromptDir.isDirectory()) {
            copyDir(srcPromptDir.toPath(), dest.toPath());
        } else {
            for (String name : promptFiles) {
                try (InputStream in = ClassTestMojo.class.getClassLoader()
                        .getResourceAsStream("prompt/" + name)) {
                    if (in == null) continue;
                    Path out = dest.toPath().resolve(name);
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        Map<String, String> replaceMap = new HashMap<>();
        // Always replace to avoid FreeMarker missing vars
        String codeLine = readLineOfClass(project, selectClass, lines);
        replaceMap.put("${lines_to_test}", codeLine);
        replaceMap.put("${constraint_text}", constraintText == null ? "" : constraintText);
        replaceMap.put("${only_target_lines}", String.valueOf(onlyTargetLines));
        replaceMap.put("${full_fm}", String.valueOf(fullFM));

        inject(dest.toPath().resolve("hits_gen.ftl"), replaceMap);
        inject(dest.toPath().resolve("hits_gen_slice.ftl"), replaceMap);
        inject(dest.toPath().resolve("hits_repair.ftl"), replaceMap);
        inject(dest.toPath().resolve("hits_system_repair.ftl"), replaceMap);
        // Do not touch hits_system_gen.ftl (it doesn't have lines_to_test)

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
}