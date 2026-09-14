package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public abstract class ProcessBuildStep implements BuildStep {

    public static final String PROCESS = "process/";

    protected static final Charset NATIVE_ENCODING = nativeEncoding();

    private static final ConcurrentMap<Integer, Semaphore> PERMITS = new ConcurrentHashMap<>();

    static {
        if (System.getProperty("java.home") == null) {
            String home = System.getenv("JAVA_HOME");
            if (home == null) {
                throw new IllegalStateException("Neither java.home or JAVA_HOME available");
            }
            System.setProperty("java.home", home);
        }
    }

    protected final transient Function<List<String>, ? extends ProcessHandler> factory;
    private final String command;
    protected final transient BiConsumer<Boolean, String> printing;
    protected final transient Consumer<String> announcing;
    private final transient Semaphore permits;

    protected ProcessBuildStep(String command, Function<List<String>, ? extends ProcessHandler> factory) {
        this(command, factory, printing(command));
    }

    protected ProcessBuildStep(String command,
                               Function<List<String>, ? extends ProcessHandler> factory,
                               BiConsumer<Boolean, String> printing) {
        int concurrency = Integer.getInteger("jenesis.process.concurrency", 0);
        if (concurrency < 0) {
            throw new IllegalArgumentException("Process concurrency must not be negative: " + concurrency);
        }
        this(command, factory, printing, concurrency == 0
                ? null
                : PERMITS.computeIfAbsent(concurrency, Semaphore::new));
    }

    protected ProcessBuildStep(String command,
                               Function<List<String>, ? extends ProcessHandler> factory,
                               BiConsumer<Boolean, String> printing,
                               Semaphore permits) {
        this.command = command;
        this.factory = factory;
        this.printing = printing;
        this.announcing = SequencedProperties.systemFlag("jenesis.print.command") ? System.out::println : null;
        this.permits = permits;
    }

    private static Charset nativeEncoding() {
        String name = System.getProperty("native.encoding");
        if (name == null) {
            return Charset.defaultCharset();
        }
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException _) {
            return Charset.defaultCharset();
        }
    }

    public static BiConsumer<Boolean, String> printing(String command) {
        return SequencedProperties.systemFlag("jenesis.print." + command,
                SequencedProperties.systemFlag("jenesis.print.process"))
                ? (error, line) -> System.out.println(paint(error ? 131 : 244, command + " >>>> " + line))
                : null;
    }

    protected List<String> configurations() {
        return factory instanceof ProcessHandler.Staged ? List.of() : List.of(command);
    }

    protected int execute(ProcessHandler handler, Path output, Path error, ProcessHandler.Tee tee)
            throws IOException, InterruptedException {
        if (permits == null) {
            return handler.execute(output, error, tee);
        }
        permits.acquire();
        try {
            return handler.execute(output, error, tee);
        } finally {
            permits.release();
        }
    }

    protected ProcessHandler.Tee tee(Executor executor, ProcessHandler handler) {
        if (printing == null) {
            return null;
        }
        printing.accept(false, String.join(" ", handler.commands()));
        return new ProcessHandler.Tee(executor,
                line -> printing.accept(false, line),
                line -> printing.accept(true, line));
    }

    private static String paint(int code, String text) {
        return "\033[38;5;" + code + "m" + text + BuildExecutorCallback.RESET;
    }

    protected abstract CompletionStage<List<String>> process(Executor executor,
                                                             BuildStepContext context,
                                                             SequencedMap<String, BuildStepArgument> arguments,
                                                             SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException;

    protected SequencedMap<String, SequencedMap<String, String>> properties(
            SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, SequencedMap<String, String>> properties = new LinkedHashMap<>();
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().removed()) {
                continue;
            }
            SequencedMap<String, String> folderMap = new LinkedHashMap<>();
            for (String name : configurations()) {
                Path file = entry.getValue().folder().resolve(PROCESS + name + ".properties");
                if (Files.exists(file)) {
                    SequencedProperties.ofFiles(file).forEachProperty(folderMap::put);
                }
            }
            properties.put(entry.getKey(), folderMap);
        }
        return properties;
    }

    protected static List<String> prepended(SequencedMap<String, SequencedMap<String, String>> properties) {
        List<String> prepended = new ArrayList<>();
        for (SequencedMap<String, String> folderMap : properties.values()) {
            for (Map.Entry<String, String> entry : folderMap.entrySet()) {
                for (String value : entry.getValue().split("\n")) {
                    prepended.add(entry.getKey());
                    if (!value.isEmpty()) {
                        prepended.add(value);
                    }
                }
            }
        }
        return prepended;
    }

    public boolean acceptableExitCode(int code,
                                      Executor executor,
                                      BuildStepContext context,
                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        return code == 0;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, SequencedMap<String, String>> properties = properties(arguments);
        AtomicReference<Thread> worker = new AtomicReference<>();
        CompletableFuture<BuildStepResult> result = process(executor, context, arguments, properties).thenComposeAsync(processed -> {
            if (processed == null) {
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }
            CompletableFuture<BuildStepResult> future = new CompletableFuture<>();
            try {
                List<String> commands = prepended(properties);
                commands.addAll(processed);
                Path output = context.supplement().resolve("output"), error = context.supplement().resolve("error");
                ProcessHandler handler = factory.apply(commands);
                Files.writeString(context.supplement().resolve("command"), String.join(" ", handler.commands()));
                ProcessHandler.Tee tee = tee(executor, handler);
                if (announcing != null) {
                    announcing.accept("%s%-11s%s %s".formatted(
                            BuildExecutorCallback.YELLOW,
                            "[EXECUTED]",
                            BuildExecutorCallback.RESET,
                            String.join(" ", handler.commands())));
                }
                executor.execute(() -> {
                    worker.set(Thread.currentThread());
                    try {
                        int exitCode = execute(handler, output, error, tee);
                        if (acceptableExitCode(exitCode, executor, context, arguments)) {
                            future.complete(new BuildStepResult(true));
                        } else {
                            String outputString = Files.exists(output) ? new String(Files.readAllBytes(output), NATIVE_ENCODING) : "";
                            String errorString = Files.exists(error) ? new String(Files.readAllBytes(error), NATIVE_ENCODING) : "";
                            throw new IllegalStateException("Unexpected exit code: " + exitCode + "\n"
                                    + "To reproduce, execute:\n " + String.join(" ", handler.commands())
                                    + (outputString.isBlank() ? "" : ("\n\nOutput:\n" + outputString))
                                    + (errorString.isBlank() ? "" : ("\n\nError:\n" + errorString)));
                        }
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    } finally {
                        worker.set(null);
                    }
                });
                return future;
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }).toCompletableFuture();
        result.whenComplete((_, throwable) -> {
            if (throwable != null) {
                Thread running = worker.get();
                if (running != null && running != Thread.currentThread()) {
                    running.interrupt();
                }
            }
        });
        return result;
    }

    public static List<String> argumentFile(Path file, SequencedMap<String, String> options) throws IOException {
        StringBuilder args = new StringBuilder();
        options.forEach((option, value) -> {
            if (value != null && !value.isEmpty()) {
                args.append(option)
                        .append("\n\"")
                        .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
                        .append("\"\n");
            }
        });
        if (args.isEmpty()) {
            return List.of();
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, args.toString());
        return List.of("@" + file);
    }
}
