package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Output;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class PmdModule implements BuildExecutorModule {

    public static final String CHECK = "check";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "net.sourceforge.pmd", MAVEN_ARTIFACT = "pmd-dist";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String configFile;
    private final boolean strict;
    private final ProcessBuildStep.Terms terms;

    public PmdModule(Map<String, Repository> repositories,
                     Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "pmd",
             "pmd.xml",
             false,
             ProcessBuildStep.Terms.of("pmd"));
    }

    public static PmdModule ofKeys(Function<String, String> keys,
                                   Output output,
                                   Map<String, Repository> repositories,
                                   Map<String, Resolver> resolvers) {
        return new PmdModule(Dependencies.ofKeys(keys, output, repositories, resolvers),
                null,
                "pmd",
                "pmd.xml",
                false,
                ProcessBuildStep.Terms.ofKeys(keys, output, "pmd"));
    }

    private PmdModule(Dependencies dependencies,
                      Pinning pinning,
                      String tool,
                      String configFile,
                      boolean strict,
                      ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.configFile = configFile;
        this.strict = strict;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, "pmd.xml");
    }

    public PmdModule pinning(Pinning pinning) {
        return new PmdModule(dependencies, pinning, tool, configFile, strict, terms);
    }

    public PmdModule tool(String tool) {
        return new PmdModule(dependencies, pinning, tool, configFile, strict, terms);
    }

    public PmdModule configFile(String configFile) {
        return new PmdModule(dependencies, pinning, tool, configFile, strict, terms);
    }

    public PmdModule strict(boolean strict) {
        return new PmdModule(dependencies, pinning, tool, configFile, strict, terms);
    }

    public PmdModule printing(BiConsumer<Boolean, String> printing) {
        return new PmdModule(dependencies, pinning, tool, configFile, strict, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(tool), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> checkInputs = new LinkedHashSet<>();
        checkInputs.add(DEPENDENCIES);
        checkInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(CHECK, new Check(terms, tool, configFile, strict), checkInputs);
    }

    private record Requires(String tool) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(tool + "/runtime/maven/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Check extends ProcessBuildStep {

        private final String tool;
        private final String configFile;
        private final boolean strict;

        private Check(ProcessBuildStep.Terms terms,
                      String tool,
                      String configFile,
                      boolean strict) {
            super("pmd", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.configFile = configFile;
            this.strict = strict;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) {
            return !strict || code == 0;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), roots = new ArrayList<>();
            boolean hasJava = false;
            Path config = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                Path candidate = argument.folder().resolve(configFile);
                if (Files.isRegularFile(candidate)) {
                    config = candidate;
                }
                Path sources = argument.folder().resolve(Bind.SOURCES);
                if (Files.isDirectory(sources)) {
                    roots.add(sources.toString());
                    try (Stream<Path> walk = Files.walk(sources)) {
                        hasJava = hasJava || walk.anyMatch(file -> file.toString().endsWith(".java"));
                    }
                }
            }
            if (!hasJava) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No PMD jars resolved upstream of the PMD step");
            }
            if (config == null) {
                throw new IllegalStateException("No " + configFile + " found among the inputs of the PMD step");
            }
            Path report = Files.createDirectories(context.next().resolve(BuildStep.REPORTS + "pmd")).resolve("pmd-report.xml");
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "net.sourceforge.pmd.cli.PmdCli", "check",
                    "--no-cache",
                    "-R", config.toString(),
                    "-f", "xml",
                    "-r", report.toString()));
            for (String root : roots) {
                commands.add("-d");
                commands.add(root);
            }
            return CompletableFuture.completedStage(commands);
        }
    }
}
