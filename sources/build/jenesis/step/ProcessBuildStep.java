package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
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
    protected final transient Terms terms;

    protected ProcessBuildStep(String command, Function<List<String>, ? extends ProcessHandler> factory) {
        this(command, factory, Terms.of(command));
    }

    protected ProcessBuildStep(String command,
                               Function<List<String>, ? extends ProcessHandler> factory,
                               Terms terms) {
        this.command = command;
        this.factory = factory;
        this.terms = terms;
    }

    public record Terms(BiConsumer<Boolean, String> printing, Semaphore permits, Consumer<String> announcing) {

        public static Terms of(String command) {
            return ofEnvironment(Environment.NONE, command, false);
        }

        public static Terms of(String command, boolean printing) {
            return ofEnvironment(Environment.NONE, command, printing);
        }

        public static Terms ofEnvironment(Environment environment, String command) {
            return ofEnvironment(environment, command, false);
        }

        public static Terms ofEnvironment(Environment environment,
                                   String command,
                                   boolean printing) {
            int concurrency = environment.number("process.concurrency", 0);
            if (concurrency < 0) {
                throw new IllegalArgumentException("Process concurrency must not be negative: " + concurrency);
            }
            boolean streamed = environment.flag("print." + command,
                    environment.flag("print.process", printing));
            return new Terms(streamed
                    ? (error, line) -> environment.out().accept("\033[38;5;" + (error ? 131 : 244) + "m"
                            + command + " >>>> " + line + BuildExecutorCallback.RESET)
                    : null,
                    concurrency == 0 ? null : PERMITS.computeIfAbsent(concurrency, Semaphore::new),
                    environment.flag("print.command") ? environment.out() : null);
        }

        public Terms printing(BiConsumer<Boolean, String> printing) {
            return new Terms(printing, permits, announcing);
        }
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

    protected List<String> configurations() {
        return factory instanceof ProcessHandler.Staged ? List.of() : List.of(command);
    }

    protected int execute(ProcessHandler handler, Path output, Path error, ProcessHandler.Tee tee)
            throws IOException, InterruptedException {
        Semaphore permits = terms.permits();
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
        BiConsumer<Boolean, String> printing = terms.printing();
        if (printing == null) {
            return null;
        }
        printing.accept(false, String.join(" ", handler.commands()));
        return new ProcessHandler.Tee(executor,
                line -> printing.accept(false, line),
                line -> printing.accept(true, line));
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
                Consumer<String> announcing = terms.announcing();
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
        List<String> arguments = new ArrayList<>();
        options.forEach((option, value) -> {
            if (value != null && !value.isEmpty()) {
                arguments.add(option);
                arguments.add(value);
            }
        });
        if (arguments.isEmpty()) {
            return List.of();
        }
        return List.of("@" + argumentFile(file, arguments));
    }

    public static Path argumentFile(Path file, List<String> arguments) throws IOException {
        StringBuilder args = new StringBuilder();
        for (String argument : arguments) {
            args.append('"')
                    .append(argument.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"\n");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, args.toString());
        return file;
    }
}
