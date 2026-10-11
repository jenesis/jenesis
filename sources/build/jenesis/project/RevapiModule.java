package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.Json;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Findings;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class RevapiModule implements BuildExecutorModule {

    public static final String COMPARE = "compare", CONFIGURATION = "revapi.properties",
            BASELINE = "baseline", SEVERITY = "severity", ANALYSIS = "configuration";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String SETTING = "artifact.revapi", REPORT = BuildStep.REPORTS + "revapi/revapi.json";
    private static final String MAVEN_GROUP = "org.revapi";
    private static final List<String> EXTENSIONS = List.of("revapi-java", "revapi-basic-features", "revapi-reporter-json");
    private static final Map<String, String> SEVERITIES = Map.of("equivalent", "EQUIVALENT",
            "non-breaking", "NON_BREAKING",
            "potentially-breaking", "POTENTIALLY_BREAKING",
            "breaking", "BREAKING");
    private static final String LAUNCHER = "RevapiCheck.java", SOURCE = """
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.databind.node.ArrayNode;
            import com.fasterxml.jackson.databind.node.ObjectNode;
            import org.revapi.API;
            import org.revapi.AnalysisContext;
            import org.revapi.AnalysisResult;
            import org.revapi.Revapi;
            import org.revapi.base.FileArchive;

            void main(String[] arguments) throws Exception {
                ObjectMapper mapper = new ObjectMapper();
                ArrayNode configuration = mapper.createArrayNode();
                ObjectNode reporter = configuration.addObject();
                reporter.put("extension", "revapi.reporter.json");
                reporter.putObject("configuration")
                        .put("minSeverity", arguments[1])
                        .put("output", arguments[0])
                        .put("indent", true)
                        .put("keepEmptyFile", true);
                List<FileArchive> support = Stream.of(arguments)
                        .skip(5)
                        .map(name -> new FileArchive(new File(name)))
                        .toList();
                Revapi revapi = Revapi.builder().withAllExtensionsFromThreadContextClassLoader().build();
                AnalysisContext.Builder context = AnalysisContext.builder(revapi)
                        .withOldAPI(API.of(new FileArchive(new File(arguments[3]))).supportedBy(support).build())
                        .withNewAPI(API.of(new FileArchive(new File(arguments[4]))).supportedBy(support).build())
                        .withConfiguration(configuration);
                if (!arguments[2].equals("-")) {
                    try (InputStream in = Files.newInputStream(Path.of(arguments[2]))) {
                        context.mergeConfigurationFromJSONStream(in);
                    }
                }
                try (AnalysisResult result = revapi.analyze(context.build())) {
                    result.throwIfFailed();
                }
            }
            """;

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final String baseline;
    private final String severity;
    private final String analysis;
    private final boolean strict;
    private final ProcessBuildStep.Terms terms;

    public RevapiModule(Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
                null,
                "revapi",
                "main",
                null,
                "POTENTIALLY_BREAKING",
                null,
                false,
                new ProcessBuildStep.Terms());
    }

    public static RevapiModule ofEnvironment(Environment environment,
                                             Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers) {
        return new RevapiModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "revapi",
                "main",
                null,
                "POTENTIALLY_BREAKING",
                null,
                Enforcement.ofEnvironment(environment, SETTING) == Enforcement.STRICT,
                ProcessBuildStep.Terms.ofEnvironment(environment, "revapi"));
    }

    private RevapiModule(Dependencies dependencies,
                         Pinning pinning,
                         String tool,
                         String group,
                         String baseline,
                         String severity,
                         String analysis,
                         boolean strict,
                         ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.baseline = baseline;
        this.severity = severity;
        this.analysis = analysis;
        this.strict = strict;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, CONFIGURATION);
    }

    public RevapiModule pinning(Pinning pinning) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule tool(String tool) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule group(String group) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule baseline(String baseline) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule severity(String severity) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule analysis(String analysis) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule strict(boolean strict) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict, terms);
    }

    public RevapiModule printing(BiConsumer<Boolean, String> printing) {
        return new RevapiModule(dependencies, pinning, tool, group, baseline, severity, analysis, strict,
                terms.printing(printing));
    }

    public RevapiModule configuration(Path file, SequencedProperties configuration) {
        for (String key : configuration.stringPropertyNames()) {
            if (!key.equals(BASELINE) && !key.equals(SEVERITY) && !key.equals(ANALYSIS)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes " + BASELINE
                        + "=<groupId>/<artifactId>[/<version>], " + SEVERITY + "=<"
                        + String.join("|", new TreeSet<>(SEVERITIES.keySet())) + "> and " + ANALYSIS
                        + "=<a JSON file of revapi's own configuration beside it>");
            }
        }
        String level = configuration.value(SEVERITY, "potentially-breaking");
        if (!SEVERITIES.containsKey(level)) {
            throw new IllegalArgumentException(file + " names the severity " + level + ", but takes one of "
                    + new TreeSet<>(SEVERITIES.keySet()));
        }
        String json = null, name = configuration.value(ANALYSIS);
        if (name != null) {
            Path sibling = Path.of(name).normalize();
            if (sibling.isAbsolute() || sibling.startsWith("..")) {
                throw new IllegalArgumentException(file + " names the configuration " + name
                        + ", which is not beside or below it - place the file in the folder of " + CONFIGURATION);
            }
            Path resolved = file.resolveSibling(sibling);
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException(file + " names the configuration " + name
                        + ", which does not exist beside it");
            }
            try {
                json = Files.readString(resolved);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the revapi configuration " + resolved, e);
            }
            if (!(Json.parse(json) instanceof List<?>)) {
                throw new IllegalArgumentException(resolved + " is no JSON array - write revapi's configuration as"
                        + " an array of {\"extension\": ..., \"configuration\": ...} objects");
            }
        }
        return baseline(configuration.value(BASELINE)).severity(SEVERITIES.get(level)).analysis(json);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED,
                new Baseline(tool, baseline, EXTENSIONS.stream().map(artifact -> MAVEN_GROUP + "/" + artifact).toList(),
                        CONFIGURATION),
                inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> compareInputs = new LinkedHashSet<>();
        compareInputs.add(DEPENDENCIES);
        compareInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(COMPARE, new Compare(terms, tool, group, severity, analysis, strict), compareInputs);
    }

    private static class Compare extends ProcessBuildStep {

        private final String tool;
        private final String group;
        private final String severity;
        private final String analysis;
        private final boolean strict;

        private Compare(ProcessBuildStep.Terms terms,
                        String tool,
                        String group,
                        String severity,
                        String analysis,
                        boolean strict) {
            super("revapi", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.group = group;
            this.severity = severity;
            this.analysis = analysis;
            this.strict = strict;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Path report = context.next().resolve(REPORT);
            if (code != 0 || !Files.isRegularFile(report)) {
                return false;
            }
            return new Findings("revapi", report, Json.parse(Files.readString(report)) instanceof List<?> found
                    ? found.size()
                    : -1).acceptable(code, context, false, strict, SETTING, terms.reporting());
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), baselines = new ArrayList<>(), support = new ArrayList<>();
            Path artifact = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "baseline")) {
                    baselines.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    support.add(jar.toString());
                }
                Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
                if (Files.isDirectory(artifacts)) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts, "*.jar")) {
                        for (Path file : files) {
                            artifact = file;
                        }
                    }
                }
            }
            if (artifact == null) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No revapi jars resolved upstream of the revapi step");
            }
            if (baselines.size() != 1) {
                throw new IllegalStateException("Expected exactly one baseline artifact upstream of the revapi step: "
                        + baselines);
            }
            Path report = context.next().resolve(REPORT);
            Files.createDirectories(report.getParent());
            Path launcher = Files.writeString(context.supplement().resolve(LAUNCHER), SOURCE);
            String configuration = analysis == null
                    ? "-"
                    : Files.writeString(context.supplement().resolve("revapi-configuration.json"), analysis).toString();
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    launcher.toString(),
                    report.toString(),
                    severity,
                    configuration,
                    baselines.getFirst(),
                    artifact.toString()));
            commands.addAll(support);
            return CompletableFuture.completedStage(commands);
        }
    }
}
