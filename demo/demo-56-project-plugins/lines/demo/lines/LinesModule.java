package demo.lines;

import module java.base;
import module build.jenesis;

public class LinesModule implements BuildExecutorModule {

    private final String file;

    public LinesModule() {
        this(Collections.emptyNavigableMap());
    }

    public LinesModule(SequencedMap<String, String> properties) {
        file = properties.getOrDefault("file", "LINES");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("lines", new Lines(file), inherited.sequencedKeySet());
    }

    private record Lines(String file) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            BuildStepArgument sources = arguments.get("../inputs/sources");
            SequencedMap<String, Long> counted = new TreeMap<>();
            if (sources != null && !sources.removed()) {
                try (Stream<Path> files = Files.walk(sources.folder())) {
                    for (Path source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                        try (Stream<String> lines = Files.lines(source)) {
                            counted.put(sources.folder().relativize(source).toString().replace(File.separatorChar, '/'),
                                    lines.count());
                        }
                    }
                }
            }
            Path target = Path.of(file);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, counted.entrySet().stream().map(entry -> entry.getValue() + " " + entry.getKey()).toList());
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
