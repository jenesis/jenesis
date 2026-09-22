package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.FormatBuildStep;
import build.jenesis.step.ProcessBuildStep;

public class ScalafmtFormatModule implements BuildExecutorModule {

    public static final String FORMAT = "format";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "org.scalameta", MAVEN_ARTIFACT = "scalafmt-cli_2.13";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String group;
    private final String configFile;
    private final boolean verify;
    private final ProcessBuildStep.Terms terms;

    public ScalafmtFormatModule(Map<String, Repository> repositories,
                                Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "scalafmt-format",
             ".scalafmt.conf",
             false,
             ProcessBuildStep.Terms.of("scalafmt-format"));
    }

    public static ScalafmtFormatModule ofEnvironment(Environment environment,
                                                     Map<String, Repository> repositories,
                                                     Map<String, Resolver> resolvers) {
        return new ScalafmtFormatModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "scalafmt-format",
                ".scalafmt.conf",
                false,
                ProcessBuildStep.Terms.ofEnvironment(environment, "scalafmt-format"));
    }

    private ScalafmtFormatModule(Dependencies dependencies,
                                 Pinning pinning,
                                 String group,
                                 String configFile,
                                 boolean verify,
                                 ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.group = group;
        this.configFile = configFile;
        this.verify = verify;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, ".scalafmt.conf");
    }

    public ScalafmtFormatModule pinning(Pinning pinning) {
        return new ScalafmtFormatModule(dependencies, pinning, group, configFile, verify, terms);
    }

    public ScalafmtFormatModule group(String group) {
        return new ScalafmtFormatModule(dependencies, pinning, group, configFile, verify, terms);
    }

    public ScalafmtFormatModule configFile(String configFile) {
        return new ScalafmtFormatModule(dependencies, pinning, group, configFile, verify, terms);
    }

    public ScalafmtFormatModule verify(boolean verify) {
        return new ScalafmtFormatModule(dependencies, pinning, group, configFile, verify, terms);
    }

    public ScalafmtFormatModule printing(BiConsumer<Boolean, String> printing) {
        return new ScalafmtFormatModule(dependencies, pinning, group, configFile, verify, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(group), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(group),
                resolveInputs);
        SequencedSet<String> formatInputs = new LinkedHashSet<>();
        formatInputs.add(DEPENDENCIES);
        formatInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(FORMAT, new Format(terms, group, configFile, verify), formatInputs);
    }

    private record Requires(String group) implements BuildStep {

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
            requires.setProperty(group + "/runtime/maven/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Format extends FormatBuildStep {

        private final String configFile;

        private Format(ProcessBuildStep.Terms terms, String group, String configFile, boolean verify) {
            super("scalafmt-format", group, verify, terms);
            this.configFile = configFile;
        }

        @Override
        protected boolean isFormattable(Path file) {
            return file.toString().endsWith(".scala");
        }

        @Override
        protected Path config(Path folder) {
            Path candidate = folder.resolve(configFile);
            return Files.isRegularFile(candidate) ? candidate : null;
        }

        @Override
        protected List<String> command(List<String> jars, Path config, List<String> files, boolean verify) {
            if (config == null) {
                throw new IllegalStateException("No " + configFile + " found among the inputs of the scalafmt step");
            }
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "org.scalafmt.cli.Cli",
                    "--config", config.toString()));
            if (verify) {
                commands.add("--test");
            }
            commands.addAll(files);
            return commands;
        }
    }
}
