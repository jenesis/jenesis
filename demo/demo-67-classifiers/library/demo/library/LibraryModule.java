package demo.library;

import module build.jenesis;
import module java.base;

public class LibraryModule implements BuildExecutorModule {

    private final String platform;

    public LibraryModule(SequencedMap<String, String> properties) {
        platform = properties.getOrDefault("platform", "default");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("compile", new Compile(platform));
    }

    private record Compile(String platform) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path folder = Files.createDirectories(context.next().resolve("library").resolve(platform));
            Files.writeString(folder.resolve("library.txt"), "a library a plugin built for " + platform);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
