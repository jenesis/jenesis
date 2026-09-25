package demo.checksums;

import module java.base;
import module build.jenesis;

public class ChecksumsModule implements BuildExecutorModule {

    private final String file;

    public ChecksumsModule() {
        this(Collections.emptyNavigableMap());
    }

    public ChecksumsModule(SequencedMap<String, String> properties) {
        file = properties.getOrDefault("file", "SHA256SUMS");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("checksums", new Checksums(file), inherited.sequencedKeySet());
    }

    private record Checksums(String file) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, String> sums = new TreeMap<>();
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                int stage = argument.getKey().indexOf("stage/");
                if (stage == -1 || argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder();
                String prefix = argument.getKey().substring(stage + "stage/".length());
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path staged : files.filter(Files::isRegularFile).toList()) {
                        byte[] digest;
                        try {
                            digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(staged));
                        } catch (NoSuchAlgorithmException e) {
                            throw new IllegalStateException(e);
                        }
                        sums.put(prefix + "/" + folder.relativize(staged).toString().replace(File.separatorChar, '/'),
                                HexFormat.of().formatHex(digest));
                    }
                }
            }
            Path target = Path.of(file);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, sums.entrySet().stream().map(sum -> sum.getValue() + "  " + sum.getKey()).toList());
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
