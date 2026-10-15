package demo.publish;

import module build.jenesis;
import module java.base;

public class PublishModule implements BuildExecutorModule {

    private final String directory;
    private final boolean publish;
    private final Consumer<String> out;

    public PublishModule(SequencedMap<String, String> properties, boolean publish, Consumer<String> out) {
        directory = properties.get("directory");
        this.publish = publish;
        this.out = out;
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        if (directory == null) {
            throw new IllegalArgumentException("The plugin publish names no folder to publish to - add"
                    + " directory=<folder> to build.jenesis/plugin-publish.properties");
        }
        executor.addStep("publish", new Publish(directory, publish, out), inherited.sequencedKeySet());
    }

    private static final class Publish implements BuildStep {

        private final String directory;
        private final boolean publish;
        private final transient Consumer<String> out;

        private Publish(String directory, boolean publish, Consumer<String> out) {
            this.directory = directory;
            this.publish = publish;
            this.out = out;
        }

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
                        if (!publish) {
                            if (out != null) {
                                out.accept("Would publish " + copy);
                            }
                            continue;
                        }
                        Files.createDirectories(copy.getParent());
                        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
