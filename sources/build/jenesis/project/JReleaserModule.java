package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JReleaserModule implements BuildExecutorModule {

    public static final String VARIABLES = "jreleaser.properties";

    private static final String ENVIRONMENT = "environment", EXECUTE = "execute";

    private static final List<String> CONFIGURATIONS = List.of(
            "jreleaser.yml", "jreleaser.yaml", "jreleaser.toml", "jreleaser.json");

    private final Path root;
    private final Path configuration;
    private final String version;

    public JReleaserModule(Path root, Path configuration, String version) {
        this.root = root;
        this.configuration = configuration;
        this.version = version;
    }

    public static Path configured(Path root) {
        String explicit = System.getProperty("jenesis.jreleaser.config");
        if (explicit != null && !explicit.isBlank()) {
            Path candidate = root.resolve(explicit.trim());
            if (!Files.isRegularFile(candidate)) {
                throw new IllegalArgumentException("No JReleaser configuration at " + candidate);
            }
            return candidate;
        }
        for (String name : CONFIGURATIONS) {
            Path candidate = root.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(ENVIRONMENT, new Environment(version), inherited.sequencedKeySet());
        SequencedSet<String> inputs = new LinkedHashSet<>();
        inputs.add(ENVIRONMENT);
        inputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(EXECUTE, new Execute(root, configuration), inputs);
    }

    private record Environment(String version) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties variables = new SequencedProperties();
            if (version != null && !version.isEmpty()) {
                variables.setProperty("JRELEASER_PROJECT_VERSION", version);
            }
            variables.store(context.next().resolve(VARIABLES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Execute extends ProcessBuildStep {

        private final Path root;
        private final Path configuration;
        private final String command;
        private final boolean dryRun;

        private Execute(Path root, Path configuration) {
            this(ProcessHandler.OfProcess.ofCommand(System.getProperty("jenesis.jreleaser.executable", "jreleaser")),
                    root,
                    configuration,
                    System.getProperty("jenesis.jreleaser.command", "full-release"),
                    Boolean.parseBoolean(System.getProperty("jenesis.jreleaser.dryRun", "true")),
                    Boolean.parseBoolean(System.getProperty("jenesis.print.jreleaser", "true")));
        }

        private Execute(Function<List<String>, ? extends ProcessHandler> factory,
                        Path root,
                        Path configuration,
                        String command,
                        boolean dryRun,
                        boolean verbose) {
            super("jreleaser", factory, verbose);
            this.root = root;
            this.configuration = configuration;
            this.command = command;
            this.dryRun = dryRun;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties) {
            List<String> commands = new ArrayList<>();
            commands.add(command);
            commands.add("--basedir");
            commands.add(root.toAbsolutePath().normalize().toString());
            commands.add("--config-file");
            commands.add(configuration.toAbsolutePath().normalize().toString());
            commands.add("--output-directory");
            commands.add(context.next().toAbsolutePath().normalize().toString());
            if (dryRun) {
                commands.add("--dry-run");
            }
            return CompletableFuture.completedStage(commands);
        }
    }
}
