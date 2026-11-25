package demo.jni;

import module build.jenesis;
import module java.base;

public class JniModule implements BuildExecutorModule {

    private static final String HEADERS = "headers", LIBRARY = "library";

    private final String library;

    public JniModule(SequencedMap<String, String> properties) {
        library = properties.getOrDefault("library", "native");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep(HEADERS, new Javac(ProcessHandler.Factory.of()).headers(true), inherited.sequencedKeySet());
        executor.addStep(LIBRARY, new Library(library), Stream.concat(
                Stream.of(HEADERS),
                inherited.sequencedKeySet().stream()));
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(HEADERS) ? Optional.empty() : Optional.of(path);
    }

    private static class Library extends EnvironmentalProcessBuildStep {

        private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");

        private final String library;

        private Library(String library) {
            super("cc", ProcessHandler.OfProcess.ofCommand(WINDOWS ? "gcc" : "cc"));
            this.library = library;
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            BuildStepArgument sources = arguments.get("../inputs/c");
            if (sources == null || sources.removed()) {
                throw new IllegalStateException("No C sources: bind them with @c=<folder> in plugin-jni.properties");
            }
            List<String> files;
            try (Stream<Path> walk = Files.walk(sources.folder())) {
                files = walk.filter(file -> file.toString().endsWith(".c")).map(Path::toString).sorted().toList();
            }
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT), platform = WINDOWS
                    ? "windows"
                    : os.startsWith("mac") ? "macos" : "linux";
            Path include = Path.of(System.getProperty("java.home"), "include"),
                    target = Files.createDirectories(context.next()
                            .resolve("classes/native/" + platform + "-" + System.getProperty("os.arch")))
                            .resolve(System.mapLibraryName(library));
            List<String> commands = new ArrayList<>(List.of("-shared"));
            if (!WINDOWS) {
                commands.add("-fPIC");
            }
            commands.addAll(List.of(
                    "-I", include.toString(),
                    "-I", include.resolve(WINDOWS ? "win32" : platform.equals("macos") ? "darwin" : "linux").toString(),
                    "-I", arguments.get(HEADERS).folder().resolve(Javac.HEADERS).toString(),
                    "-o", target.toString()));
            commands.addAll(files);
            return CompletableFuture.completedStage(commands);
        }
    }
}
