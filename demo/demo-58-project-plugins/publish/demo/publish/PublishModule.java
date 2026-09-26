package demo.publish;

import module java.base;
import module build.jenesis;

public class PublishModule implements BuildExecutorModule {

    private final String directory;

    public PublishModule() {
        this(Collections.emptyNavigableMap());
    }

    public PublishModule(SequencedMap<String, String> properties) {
        directory = properties.get("directory");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        if (directory == null) {
            throw new IllegalArgumentException("The plugin publish names no folder to publish to - add"
                    + " publish.directory=<folder> to jenesis.plugins.arguments.properties");
        }
        executor.addStep("publish", new Publish(directory), inherited.sequencedKeySet());
    }

    private record Publish(String directory) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path target = Path.of(directory);
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (!argument.getKey().endsWith("stage/modular") || argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder();
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path copy = target.resolve(folder.relativize(file).toString());
                        Files.createDirectories(copy.getParent());
                        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
