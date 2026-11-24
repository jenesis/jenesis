package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class Aot implements BuildStep {

    public static final String AOT = "aot/";
    private static final Duration ENDING = Duration.ofSeconds(10);

    private final List<String> arguments;
    private final String main;
    private final Duration timeout;
    private final boolean image;

    public Aot() {
        this(List.of(), null, null, false);
    }

    private Aot(List<String> arguments, String main, Duration timeout, boolean image) {
        this.arguments = arguments;
        this.main = main;
        this.timeout = timeout;
        this.image = image;
    }

    public Aot arguments(List<String> arguments) {
        return new Aot(arguments, main, timeout, image);
    }

    public Aot main(String main) {
        return new Aot(arguments, main, timeout, image);
    }

    public Aot timeout(Duration timeout) {
        return new Aot(arguments, main, timeout, image);
    }

    public Aot image(boolean image) {
        return new Aot(arguments, main, timeout, image);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Path runtime = null;
        String module = null, declared = null, unnamed = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(JLink.RUNTIME);
            if (runtime == null && Files.isDirectory(candidate)) {
                runtime = candidate;
            }
            if (!Layers.membership(argument.folder()).isEmpty()) {
                throw new IllegalStateException("aot.properties trains an AOT cache for an application of one module"
                        + " layer, but this one declares further layers, which the training run would not be handed:"
                        + " remove aot.properties, or set -Djenesis.package.aot=false");
            }
            Path linking = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, "jlink"),
                    packaging = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, "jpackage");
            if (module == null && linking != null) {
                module = SequencedProperties.ofFiles(linking).value("--add-modules");
            }
            if (packaging != null) {
                SequencedProperties launched = SequencedProperties.ofFiles(packaging);
                String named = launched.value("--module");
                if (declared == null && named != null) {
                    declared = named.substring(named.indexOf('/') + 1);
                }
                if (unnamed == null) {
                    unnamed = launched.value("--main-class");
                }
            }
        }
        if (unnamed != null) {
            throw new IllegalStateException("aot.properties trains an AOT cache with the runtime image that jlink"
                    + " links, which holds named modules alone, but " + unnamed + " is launched from the class path:"
                    + " declare its module in a module-info.java and build with the modular layout, or remove"
                    + " aot.properties");
        }
        String launched = main == null ? declared : main;
        if (runtime == null || module == null || launched == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Path java = runtime.toAbsolutePath().resolve("bin").resolve("java.exe"),
                cache = Files.createDirectory(context.next().resolve(AOT)).toAbsolutePath().resolve(module + ".aot"),
                directory = Files.createDirectory(context.supplement().resolve("directory")),
                output = context.supplement().resolve("output"),
                error = context.supplement().resolve("error"),
                linked = runtime;
        List<String> commands = new ArrayList<>(List.of(
                (Files.isRegularFile(java) ? java : java.resolveSibling("java")).toString(),
                "-XX:AOTCacheOutput=" + cache));
        commands.addAll(Bundle.javaOptions(arguments));
        commands.add("-m");
        commands.add(module + "/" + launched);
        commands.addAll(this.arguments);
        String executed = ProcessBuildStep.shell(commands), trained = module + "/" + launched;
        Files.writeString(context.supplement().resolve("command"), executed);
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder builder = new ProcessBuilder(commands)
                        .directory(directory.toFile())
                        .redirectOutput(output.toFile())
                        .redirectError(error.toFile());
                builder.environment().clear();
                builder.environment().putAll(ProcessHandler.OfProcess.ofStaged().apply(commands).environment());
                Process process = ProcessHandler.OfProcess.start(builder);
                process.getOutputStream().close();
                try {
                    if (timeout != null && !process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                        try {
                            ProcessHandler.OfProcess.ofJavaHome("bin/jcmd")
                                    .apply(List.of(Long.toString(process.pid()), "AOT.end_recording"))
                                    .execute(context.supplement().resolve("jcmd.output"),
                                            context.supplement().resolve("jcmd.error"),
                                            null);
                        } catch (IllegalStateException _) {
                        }
                        process.destroy();
                        if (!process.waitFor(ENDING.toMillis(), TimeUnit.MILLISECONDS)) {
                            process.destroyForcibly().waitFor();
                        }
                    } else if (process.waitFor() != 0) {
                        throw new IllegalStateException("The training run of " + trained + " ended with exit code "
                                + process.exitValue() + ", so its AOT cache is not kept: " + output + " and " + error
                                + " hold what it printed\nTo reproduce, execute:\n " + executed);
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while training " + trained, e);
                }
                if (!Files.isRegularFile(cache) || Files.size(cache) == 0) {
                    throw new IllegalStateException("The training run of " + trained + " wrote no AOT cache"
                            + (timeout == null ? "" : " when it was ended after " + timeout) + ": " + output + " and "
                            + error + " hold what it printed\nTo reproduce, execute:\n " + executed);
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(cache.getParent())) {
                    for (Path file : files) {
                        if (!file.equals(cache)) {
                            Files.delete(file);
                        }
                    }
                }
                if (image) {
                    Path target = context.next().resolve(JLink.RUNTIME);
                    Files.walkFileTree(linked, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path folder, BasicFileAttributes attributes)
                                throws IOException {
                            Files.createDirectories(target.resolve(linked.relativize(folder).toString()));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                                throws IOException {
                            BuildStep.linkOrCopy(target.resolve(linked.relativize(file).toString()), file);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    BuildStep.linkOrCopy(target.resolve("lib").resolve(cache.getFileName().toString()), cache);
                }
                return new BuildStepResult(true);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, executor);
    }
}
