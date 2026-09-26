package demo.complete;

import module java.base;
import module build.jenesis;

public class CompleteModule implements BuildExecutorModule {

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("complete", new Complete(), inherited.sequencedKeySet());
    }

    private record Complete() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (!argument.getKey().endsWith("/maven") || argument.getValue().removed()) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(argument.getValue().folder())) {
                    for (Path jar : files.filter(file -> file.toString().endsWith(".jar")).toList()) {
                        if (!Files.isRegularFile(jar.resolveSibling(jar.getFileName() + ".sha256"))) {
                            throw new IllegalStateException(jar.getFileName() + " is staged without a checksum - add"
                                    + " checksums+stage/transform to jenesis.plugins.properties, or switch it back on");
                        }
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
