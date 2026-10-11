package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JApiCmpModule implements BuildExecutorModule {

    public static final String COMPARE = "compare";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "com.github.siom79.japicmp", MAVEN_ARTIFACT = "japicmp";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final SequencedProperties config;
    private final ProcessBuildStep.Terms terms;

    public JApiCmpModule(Map<String, Repository> repositories,
                         Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "japicmp",
             "main",
             new SequencedProperties(),
             new ProcessBuildStep.Terms());
    }

    public static JApiCmpModule ofEnvironment(Environment environment,
                                              Map<String, Repository> repositories,
                                              Map<String, Resolver> resolvers) {
        return new JApiCmpModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "japicmp",
                "main",
                new SequencedProperties(),
                ProcessBuildStep.Terms.ofEnvironment(environment, "japicmp"));
    }

    private JApiCmpModule(Dependencies dependencies,
                          Pinning pinning,
                          String tool,
                          String group,
                          SequencedProperties config,
                          ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.config = config;
        this.terms = terms;
    }

    public JApiCmpModule pinning(Pinning pinning) {
        return new JApiCmpModule(dependencies, pinning, tool, group, config, terms);
    }

    public JApiCmpModule tool(String tool) {
        return new JApiCmpModule(dependencies, pinning, tool, group, config, terms);
    }

    public JApiCmpModule group(String group) {
        return new JApiCmpModule(dependencies, pinning, tool, group, config, terms);
    }

    public JApiCmpModule config(SequencedProperties config) {
        return new JApiCmpModule(dependencies, pinning, tool, group, config, terms);
    }

    public JApiCmpModule printing(BiConsumer<Boolean, String> printing) {
        return new JApiCmpModule(dependencies, pinning, tool, group, config, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED,
                new Baseline(tool, config.value("baseline"), List.of(MAVEN_GROUP + "/" + MAVEN_ARTIFACT), "japicmp.properties"),
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
        buildExecutor.addStep(COMPARE, new Compare(terms, tool, group, config), compareInputs);
    }

    private static class Compare extends ProcessBuildStep {

        private final String tool;
        private final String group;
        private final SequencedProperties config;

        private Compare(ProcessBuildStep.Terms terms,
                        String tool,
                        String group,
                        SequencedProperties config) {
            super("japicmp", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.group = group;
            this.config = config;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), baselines = new ArrayList<>(), classPath = new ArrayList<>();
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
                    classPath.add(jar.toString());
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
                throw new IllegalStateException("No japicmp jars resolved upstream of the japicmp step");
            }
            if (baselines.size() != 1) {
                throw new IllegalStateException("Expected exactly one baseline artifact upstream of the japicmp step: "
                        + baselines);
            }
            Path report = Files.createDirectories(context.next().resolve(BuildStep.REPORTS + "japicmp"));
            List<String> commands = new ArrayList<>(), options = new ArrayList<>();
            for (String option : prepended(properties)) {
                if (option.startsWith("-J")) {
                    commands.add(option.substring(2));
                } else if (option.startsWith("-D")) {
                    commands.add(option);
                } else {
                    options.add(option);
                }
            }
            properties.values().forEach(Map::clear);
            commands.addAll(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "japicmp.JApiCmp",
                    "-o", baselines.getFirst(),
                    "-n", artifact.toString()));
            List<String> formats = config.entries("format");
            for (String format : formats == null ? List.of("xml") : formats) {
                switch (format) {
                    case "xml" -> {
                        commands.add("--xml-file");
                        commands.add(report.resolve("japicmp-report.xml").toString());
                    }
                    case "html" -> {
                        commands.add("--html-file");
                        commands.add(report.resolve("japicmp-report.html").toString());
                    }
                    default -> throw new IllegalArgumentException("Unknown japicmp format: "
                            + format
                            + " (expected one of [html, xml])");
                }
            }
            String access = config.value("access");
            if (access != null) {
                commands.add("-a");
                commands.add(access);
            }
            List<String> included = config.entries("include"), excluded = config.entries("exclude");
            if (included != null) {
                commands.add("-i");
                commands.add(String.join(";", included));
            }
            if (excluded != null) {
                commands.add("-e");
                commands.add(String.join(";", excluded));
            }
            if (!classPath.isEmpty()) {
                String joined = String.join(File.pathSeparator, classPath);
                commands.add("--old-classpath");
                commands.add(joined);
                commands.add("--new-classpath");
                commands.add(joined);
            }
            if (config.flag("ignore-missing-classes", true)) {
                commands.add("--ignore-missing-classes");
            }
            if (config.flag("only-incompatible")) {
                commands.add("-b");
            }
            if (config.flag("only-modified")) {
                commands.add("-m");
            }
            if (config.flag("semantic-versioning")) {
                commands.add("-s");
            }
            for (String failure : List.of("binary-incompatibility",
                    "source-incompatibility",
                    "modifications",
                    "semantic-incompatibility")) {
                if (config.flag("error-on-" + failure)) {
                    commands.add("--error-on-" + failure);
                }
            }
            commands.addAll(options);
            return CompletableFuture.completedStage(commands);
        }
    }
}
