package build.jenesis.step;

import module java.base;
import module java.xml;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.SequencedProperties;
import org.xml.sax.Attributes;

public abstract class ProcessBuildStep implements BuildStep {

    public static final String PROCESS = "process/", ENVIRONMENT = "environment/";
    protected static final Charset NATIVE_ENCODING = nativeEncoding();
    private static final ConcurrentMap<Integer, Semaphore> PERMITS = new ConcurrentHashMap<>();
    private static final Set<String> ARGUMENT_FILES = Set.of("jar", "javac", "javadoc", "jdeps", "jlink", "jmod", "jpackage");
    private static final Pattern UNQUOTED = Pattern.compile("[A-Za-z0-9_@%+=:,./-]+");
    private static final int TAIL_LINES = 200, TAIL_BYTES = 256 * 1024;

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

    public record Terms(BiConsumer<Boolean, String> printing,
                        Semaphore permits,
                        Consumer<String> announcing,
                        Consumer<String> reporting) {

        public static Terms of(String command) {
            return of(command, false);
        }

        public static Terms of(String command, boolean printing) {
            return ofEnvironment(Environment.NONE, command, printing).reporting(null);
        }

        public static Terms ofEnvironment(Environment environment, String command) {
            return ofEnvironment(environment, command, false);
        }

        public static Terms ofEnvironment(Environment environment,
                                   String command,
                                   boolean printing) {
            int concurrency = environment.number("process.concurrency", Runtime.getRuntime().availableProcessors());
            if (concurrency < 0) {
                throw new IllegalArgumentException("Process concurrency must not be negative: " + concurrency);
            }
            boolean streamed = environment.flag("print." + command,
                    environment.flag("print.process", printing));
            Consumer<String> out = environment.out();
            Palette palette = Palette.ofEnvironment(environment);
            return new Terms(streamed
                    ? (error, line) -> out.accept((error ? palette.error() : palette.output())
                            + command + " >>>> " + line + palette.reset())
                    : null,
                    concurrency == 0 ? null : PERMITS.computeIfAbsent(concurrency, Semaphore::new),
                    environment.flag("print.command")
                            ? executed -> out.accept("%s%-11s%s %s".formatted(
                                    palette.info(), "[EXECUTED]", palette.reset(), executed))
                            : null,
                    environment.flag("print.findings", true)
                            ? found -> out.accept("%s%-11s%s %s".formatted(
                                    palette.warning(), "[FINDINGS]", palette.reset(), found))
                            : null);
        }

        public Terms printing(BiConsumer<Boolean, String> printing) {
            return new Terms(printing, permits, announcing, reporting);
        }

        public Terms reporting(Consumer<String> reporting) {
            return new Terms(printing, permits, announcing, reporting);
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

    protected ProcessHandler handler(BuildStepContext context, List<String> commands) throws IOException {
        return factory.apply(commands);
    }

    protected ProcessHandler environment(ProcessHandler handler, SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> variables = variables(arguments);
        if (!variables.isEmpty()) {
            throw new IllegalStateException("An environment file hands " + variables.keySet() + " to " + command
                    + ", which takes no environment: only a program the build runs in a process of its own does"
                    + " - a forked JVM, the test run, PIT and native-image");
        }
        return handler;
    }

    protected SequencedMap<String, String> variables(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> variables = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (String name : configurations()) {
                Path file = argument.folder().resolve(ENVIRONMENT + name + ".properties");
                if (Files.exists(file)) {
                    SequencedProperties.ofFiles(file).forEachProperty(variables::put);
                }
            }
        }
        return variables;
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

    protected static int findings(Path report, String finding) throws IOException {
        if (!Files.isRegularFile(report)) {
            return -1;
        }
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            AtomicInteger findings = new AtomicInteger();
            factory.newSAXParser().parse(report.toFile(), new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
                    if (qualifiedName.equals(finding)) {
                        findings.incrementAndGet();
                    }
                }
            });
            return findings.get();
        } catch (SAXException _) {
            return -1;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    protected boolean reported(int code,
                               BuildStepContext context,
                               Path report,
                               int findings,
                               boolean judged,
                               boolean strict,
                               String setting) {
        if (findings < 0) {
            return code == 0;
        }
        String found = command + " found " + findings + (findings == 1 ? " finding" : " findings");
        if (strict && findings > 0 && (code != 0 || !judged)) {
            throw new IllegalStateException(found + ", reported in " + report
                    + ", and fails the build on them as jenesis." + setting
                    + " is set: fix them, or set it to false to report them without failing");
        }
        if (strict && code != 0) {
            return false;
        }
        Consumer<String> reporting = terms.reporting();
        if (findings > 0 && reporting != null) {
            Path step = context.next().getParent();
            String name = step == null ? "" : step.getFileName().toString();
            reporting.accept(found + ", reported in " + (name.endsWith(BuildExecutor.NEXT)
                    ? step.resolveSibling(name.substring(0, name.length() - BuildExecutor.NEXT.length()))
                            .resolve(step.relativize(report))
                    : report));
        }
        return true;
    }

    protected String diagnosis(BuildStepContext context) throws IOException {
        return "";
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
                ProcessHandler handler = environment(handler(context, commands), arguments);
                Files.writeString(context.supplement().resolve("command"), String.join(" ", handler.commands()));
                ProcessHandler.Tee tee = tee(executor, handler);
                Consumer<String> announcing = terms.announcing();
                if (announcing != null) {
                    announcing.accept(String.join(" ", handler.commands()));
                }
                executor.execute(() -> {
                    worker.set(Thread.currentThread());
                    try {
                        int exitCode = execute(handler, output, error, tee);
                        if (acceptableExitCode(exitCode, executor, context, arguments)) {
                            future.complete(new BuildStepResult(true));
                        } else {
                            throw new IllegalStateException("Unexpected exit code: " + exitCode + "\n"
                                    + diagnosis(context)
                                    + "To reproduce, execute:\n "
                                    + reproduction(context.supplement().resolve("reproduce.args"), handler.commands())
                                    + tail("Output", output)
                                    + tail("Error", error));
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

    protected static String tail(String label, Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        long size = Files.size(file);
        byte[] bytes;
        try (InputStream stream = Files.newInputStream(file)) {
            stream.skipNBytes(Math.max(0, size - TAIL_BYTES));
            bytes = stream.readNBytes(TAIL_BYTES);
        }
        String text = new String(bytes, NATIVE_ENCODING);
        if (text.isBlank()) {
            return "";
        }
        List<String> lines = text.lines().toList();
        boolean cut = size > bytes.length;
        if (cut && lines.size() > 1) {
            lines = lines.subList(1, lines.size());
        }
        if (lines.size() > TAIL_LINES) {
            lines = lines.subList(lines.size() - TAIL_LINES, lines.size());
            cut = true;
        }
        return "\n\n" + label
                + (cut ? ", its last " + lines.size() + " lines - " + file + " holds all of it" : "")
                + ":\n" + String.join("\n", lines) + "\n";
    }

    protected String reproduction(Path file, List<String> commands) throws IOException {
        List<String> moved = new ArrayList<>(), kept = new ArrayList<>();
        for (String argument : commands.subList(1, commands.size())) {
            (argument.startsWith("@") ? kept : moved).add(argument);
        }
        if (!ARGUMENT_FILES.contains(command) || moved.isEmpty()) {
            return shell(commands);
        }
        List<String> line = new ArrayList<>();
        line.add(commands.getFirst());
        line.add("@" + argumentFile(file, moved));
        line.addAll(kept);
        return shell(line);
    }

    protected static String shell(List<String> words) {
        return words.stream()
                .map(word -> UNQUOTED.matcher(word).matches() ? word : "'" + word.replace("'", "'\\''") + "'")
                .collect(Collectors.joining(" "));
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
