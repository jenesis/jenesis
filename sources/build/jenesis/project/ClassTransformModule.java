package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.step.Versions;

public record ClassTransformModule(SequencedMap<String, BuildExecutorModule> transforms) implements BuildExecutorModule {

    public static final String PLUGIN = "plugin", CLASSES = "classes";

    public ClassTransformModule {
        if (transforms.isEmpty()) {
            throw new IllegalArgumentException("A class transformation needs at least one transform - name one, or"
                    + " leave the transformer of the toolchain unset");
        }
        transforms = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(transforms));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        String previous = inherited.firstEntry().getKey();
        List<String> inputs = inherited.sequencedKeySet().stream().skip(1).toList();
        for (Map.Entry<String, BuildExecutorModule> entry : transforms.entrySet()) {
            BuildExecutorModule plugin = entry.getValue();
            buildExecutor.addModule(entry.getKey(), (nested, handed) -> {
                nested.addModule(PLUGIN, plugin, handed.sequencedKeySet());
                nested.addStep(CLASSES, new Overlay(), handed.firstEntry().getKey(), PLUGIN);
            }, Stream.concat(Stream.of(previous), inputs.stream()));
            previous = entry.getKey();
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(transforms.lastEntry().getKey() + "/" + CLASSES) ? Optional.of("") : Optional.empty();
    }

    private record Overlay() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path target = Files.createDirectory(context.next().resolve(BuildStep.CLASSES));
            Map<Path, String> writers = new HashMap<>();
            boolean handed = true;
            for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
                BuildStepArgument argument = entry.getValue();
                if (argument.removed()) {
                    continue;
                }
                boolean transformed = !handed;
                handed = false;
                Path manifest = argument.folder().resolve(Versions.MANIFEST);
                if (Files.exists(manifest)) {
                    place(context.next().resolve(Versions.MANIFEST), manifest, Path.of(Versions.MANIFEST),
                            transformed, entry.getKey(), writers);
                }
                Path source = argument.folder().resolve(BuildStep.CLASSES);
                if (!Files.isDirectory(source)) {
                    continue;
                }
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(target.resolve(source.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relative = source.relativize(file);
                        place(target.resolve(relative), file, Path.of(BuildStep.CLASSES).resolve(relative),
                                transformed, entry.getKey(), writers);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static void place(Path destination,
                                  Path file,
                                  Path name,
                                  boolean transformed,
                                  String writer,
                                  Map<Path, String> writers) throws IOException {
            if (transformed) {
                String previous = writers.putIfAbsent(name, writer);
                if (previous != null) {
                    throw new IllegalStateException(name.toString().replace(File.separatorChar, '/')
                            + " is written by both " + previous + " and " + writer + " - a transform writes each"
                            + " file once, so let one of its steps write it");
                }
                Files.deleteIfExists(destination);
            }
            BuildStep.linkOrCopy(destination, file);
        }
    }
}
