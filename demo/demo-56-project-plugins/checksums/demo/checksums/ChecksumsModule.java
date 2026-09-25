package demo.checksums;

import module java.base;
import module build.jenesis;

public class ChecksumsModule implements BuildExecutorModule {

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("checksums", new Checksums(), inherited.sequencedKeySet());
    }

    private record Checksums() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                String tree = argument.getKey().substring(argument.getKey().lastIndexOf('/') + 1);
                if (!List.of("maven", "modular").contains(tree) || argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder();
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path staged : files.filter(Files::isRegularFile).toList()) {
                        byte[] digest;
                        try {
                            digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(staged));
                        } catch (NoSuchAlgorithmException e) {
                            throw new IllegalStateException(e);
                        }
                        Path sum = context.next().resolve(tree).resolve(folder.relativize(staged) + ".sha256");
                        Files.createDirectories(sum.getParent());
                        Files.writeString(sum, HexFormat.of().formatHex(digest));
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
