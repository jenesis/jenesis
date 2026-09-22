package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Output;
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
    private final String executable;
    private final String command;
    private final boolean dryRun;
    private final ProcessBuildStep.Terms terms;

    public JReleaserModule(Path root, Path configuration, String version) {
        this(root, configuration, version, "jreleaser", "full-release", true,
                ProcessBuildStep.Terms.of("jreleaser", true));
    }

    public static JReleaserModule ofKeys(Function<String, String> keys,
                                         Output output,
                                         Path root,
                                         Path configuration,
                                         String version) {
        return new JReleaserModule(root,
                configuration,
                version,
                SequencedProperties.getProperty(keys, "jreleaser.executable", "jreleaser"),
                SequencedProperties.getProperty(keys, "jreleaser.command", "full-release"),
                SequencedProperties.flag(keys, "jreleaser.dryRun", true),
                ProcessBuildStep.Terms.ofKeys(keys, output, "jreleaser", true));
    }

    private JReleaserModule(Path root,
                            Path configuration,
                            String version,
                            String executable,
                            String command,
                            boolean dryRun,
                            ProcessBuildStep.Terms terms) {
        this.root = root;
        this.configuration = configuration;
        this.version = version;
        this.executable = executable;
        this.command = command;
        this.dryRun = dryRun;
        this.terms = terms;
    }

    public JReleaserModule configuration(Path configuration) {
        return new JReleaserModule(root, configuration, version, executable, command, dryRun, terms);
    }

    public JReleaserModule executable(String executable) {
        return new JReleaserModule(root, configuration, version, executable, command, dryRun, terms);
    }

    public JReleaserModule command(String command) {
        return new JReleaserModule(root, configuration, version, executable, command, dryRun, terms);
    }

    public JReleaserModule dryRun(boolean dryRun) {
        return new JReleaserModule(root, configuration, version, executable, command, dryRun, terms);
    }

    public JReleaserModule printing(BiConsumer<Boolean, String> printing) {
        return new JReleaserModule(root, configuration, version, executable, command, dryRun, terms.printing(printing));
    }

    public static Path configured(Function<String, String> keys, Path root) {
        String explicit = SequencedProperties.getProperty(keys, "jreleaser.config");
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
        buildExecutor.addStep(EXECUTE,
                new Execute(ProcessHandler.OfProcess.ofCommand(executable),
                        root,
                        configuration,
                        command,
                        dryRun,
                        terms),
                inputs);
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

        private Execute(Function<List<String>, ? extends ProcessHandler> factory,
                        Path root,
                        Path configuration,
                        String command,
                        boolean dryRun,
                        ProcessBuildStep.Terms terms) {
            super("jreleaser", factory, terms);
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
