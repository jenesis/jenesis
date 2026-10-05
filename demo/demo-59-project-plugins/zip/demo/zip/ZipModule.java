package demo.zip;

import module java.base;
import module build.jenesis;

public class ZipModule implements BuildExecutorModule {

    private final String name;

    public ZipModule() {
        this(Collections.emptyNavigableMap());
    }

    public ZipModule(SequencedMap<String, String> properties) {
        name = properties.getOrDefault("name", "distribution");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("zip", new Zip(name), inherited.sequencedKeySet());
    }

    private record Zip(String name) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, Path> jars = new TreeMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
                if (Files.isDirectory(artifacts)) {
                    try (Stream<Path> files = Files.list(artifacts)) {
                        files.filter(file -> file.toString().endsWith(".jar"))
                                .forEach(file -> jars.putIfAbsent(name + ".jar", file));
                    }
                }
                for (Path file : Dependencies.select(argument.folder(), "main", "runtime")) {
                    jars.putIfAbsent(file.getFileName().toString(), file);
                }
            }
            Path zip = Files.createDirectories(context.next().resolve("packages")).resolve(name + ".zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (Map.Entry<String, Path> jar : jars.entrySet()) {
                    ZipEntry entry = new ZipEntry("lib/" + jar.getKey());
                    entry.setTimeLocal(LocalDateTime.of(1980, 2, 1, 0, 0));
                    out.putNextEntry(entry);
                    Files.copy(jar.getValue(), out);
                    out.closeEntry();
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
