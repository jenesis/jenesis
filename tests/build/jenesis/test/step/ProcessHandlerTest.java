package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessHandlerTest {

    @TempDir
    private Path root;

    @Test
    public void interrupting_a_forked_process_destroys_it_and_restores_the_interrupt_flag() throws Exception {
        Path source = root.resolve("Sleeper.java");
        Files.writeString(source, """
                public class Sleeper {
                    public static void main(String[] args) throws InterruptedException {
                        Thread.sleep(600_000);
                    }
                }
                """);
        Path output = root.resolve("output"), error = root.resolve("error");
        ProcessHandler handler = ProcessHandler.OfProcess.ofJavaHome("bin/java")
                .apply(List.of(source.toString()));

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                handler.execute(output, error, null);
            } catch (Throwable t) {
                thrown.set(t);
                interruptRestored.set(Thread.currentThread().isInterrupted());
            } finally {
                finished.countDown();
            }
        });
        worker.start();
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(1_000);
        worker.interrupt();
        assertThat(finished.await(30, TimeUnit.SECONDS))
                .as("interrupting the worker tears the forked process down promptly")
                .isTrue();
        assertThat(thrown.get()).isInstanceOf(RuntimeException.class);
        assertThat(thrown.get().getCause()).isInstanceOf(InterruptedException.class);
        assertThat(interruptRestored.get())
                .as("the interrupt flag is restored before rethrowing")
                .isTrue();
    }

    @Test
    public void teeing_a_forked_process_writes_the_files_and_streams_each_line() throws Exception {
        Path source = root.resolve("Chatty.java");
        Files.writeString(source, """
                public class Chatty {
                    public static void main(String[] args) {
                        System.out.println("out-one");
                        System.err.println("err-one");
                        System.out.println("out-two");
                    }
                }
                """);
        Path output = root.resolve("output"), error = root.resolve("error");
        List<String> outLines = new CopyOnWriteArrayList<>(), errLines = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ProcessHandler handler = ProcessHandler.OfProcess.ofJavaHome("bin/java").apply(List.of(source.toString()));
            assertThat(handler.execute(output,
                    error,
                    new ProcessHandler.Tee(executor, outLines::add, errLines::add))).isZero();
        } finally {
            executor.shutdown();
        }
        assertThat(Files.readString(output)).contains("out-one").contains("out-two");
        assertThat(Files.readString(error)).contains("err-one");
        assertThat(outLines).contains("out-one", "out-two");
        assertThat(errLines).contains("err-one");
    }

    @Test
    public void a_forked_process_is_handed_the_platform_variables_and_nothing_else() {
        ProcessHandler.OfProcess handler = ProcessHandler.OfProcess.ofJavaHome("bin/java").apply(List.of());
        Set<String> platform = Set.of("PATH", "HOME", "LANG", "TMPDIR",
                "SYSTEMROOT", "SYSTEMDRIVE", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP", "USERPROFILE",
                "PROGRAMFILES", "PROGRAMFILES(X86)", "PROGRAMW6432", "PROGRAMDATA", "APPDATA", "LOCALAPPDATA");
        assertThat(handler.environment().keySet())
                .as("a variable of the build that is no fact of the platform never reaches a forked process")
                .allSatisfy(name -> assertThat(name.startsWith("LC_")
                        || platform.contains(name.toUpperCase(Locale.ROOT))).as(name).isTrue());
    }

    @Test
    public void a_forked_process_receives_the_environment_it_is_handed() throws Exception {
        Path source = root.resolve("Variable.java");
        Files.writeString(source, """
                public class Variable {
                    public static void main(String[] args) {
                        System.out.println(System.getenv("SAMPLE_VARIABLE"));
                    }
                }
                """);
        Path output = root.resolve("output"), error = root.resolve("error");
        ProcessHandler.OfProcess handler = ProcessHandler.OfProcess.ofJavaHome("bin/java").apply(List.of(source.toString()));
        SortedMap<String, String> environment = new TreeMap<>(handler.environment());
        environment.put("SAMPLE_VARIABLE", "sample");
        assertThat(handler.environment(environment).execute(output, error, null)).isZero();
        assertThat(Files.readAllLines(output)).containsExactly("sample");
    }

    @Test
    public void teeing_a_tool_writes_the_files_and_streams_each_line() throws Exception {
        ToolProvider tool = new ToolProvider() {
            @Override
            public String name() {
                return "chatty";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                out.println("out-one");
                err.println("err-one");
                out.println("out-two");
                return 0;
            }
        };
        Path output = root.resolve("output"), error = root.resolve("error");
        List<String> outLines = new CopyOnWriteArrayList<>(), errLines = new CopyOnWriteArrayList<>();
        ProcessHandler handler = ProcessHandler.OfTool.of(tool).apply(List.of());
        assertThat(handler.execute(output,
                error,
                new ProcessHandler.Tee(Runnable::run, outLines::add, errLines::add))).isZero();
        assertThat(Files.readString(output)).contains("out-one").contains("out-two");
        assertThat(Files.readString(error)).contains("err-one");
        assertThat(outLines).containsExactly("out-one", "out-two");
        assertThat(errLines).containsExactly("err-one");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void a_tool_writes_its_output_in_utf_8_whatever_the_native_encoding(boolean teed) throws Exception {
        ToolProvider tool = new ToolProvider() {
            @Override
            public String name() {
                return "quoting";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                err.println("@Foo(\"\u201Cx\u201D\")");
                for (int index = 0; index < 200; index++) {
                    err.println("line " + index);
                }
                return 1;
            }
        };
        Path output = root.resolve("output"), error = root.resolve("error");
        List<String> errLines = new CopyOnWriteArrayList<>();
        ProcessHandler handler = ProcessHandler.OfTool.of(tool).apply(List.of());
        assertThat(handler.execute(output,
                error,
                teed ? new ProcessHandler.Tee(Runnable::run, _ -> { }, errLines::add) : null)).isEqualTo(1);
        assertThat(Files.readAllLines(error, StandardCharsets.UTF_8))
                .as("every character the tool prints is kept, as UTF-8 holds them all")
                .hasSize(201)
                .startsWith("@Foo(\"\u201Cx\u201D\")")
                .endsWith("line 199");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void a_forked_jvm_is_told_to_print_in_utf_8_and_captured_exactly(boolean teed) throws Exception {
        assertThat(accented(List.of(), teed))
                .as("whatever the platform's encoding, a JVM the build forks prints every character")
                .containsExactly("caf\u00e9");
    }

    @Test
    public void a_forked_jvm_printing_in_an_encoding_of_its_own_is_read_in_that_one() throws Exception {
        assertThat(accented(List.of("-Dstdout.encoding=ISO-8859-1"), false))
                .as("an encoding a process file names is the one the output is read in")
                .containsExactly("caf\u00e9");
    }

    private List<String> accented(List<String> options, boolean teed) throws Exception {
        Path source = root.resolve("Accented.java");
        Files.writeString(source, """
                public class Accented {
                    public static void main(String[] args) {
                        System.out.println("caf\\u00e9");
                    }
                }
                """);
        Path output = root.resolve("output"), error = root.resolve("error");
        List<String> arguments = new ArrayList<>(options);
        arguments.add(source.toString());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ProcessHandler handler = ProcessHandler.OfProcess.ofJavaHome("bin/java").apply(arguments);
            assertThat(handler.execute(output,
                    error,
                    teed ? new ProcessHandler.Tee(executor, _ -> { }, _ -> { }) : null)).isZero();
        } finally {
            executor.shutdown();
        }
        return Files.readAllLines(output, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void a_forked_program_writing_what_the_native_encoding_cannot_decode_is_still_captured(boolean teed)
            throws Exception {
        Path source = root.resolve("Raw.java");
        Files.writeString(source, """
                public class Raw {
                    public static void main(String[] args) throws Exception {
                        System.out.write(new byte[] {(byte) 0xFF, (byte) 0xFE, ' ', 'o', 'k', '\\n'});
                        System.out.write("after\\n".getBytes());
                        System.out.flush();
                    }
                }
                """);
        Path output = root.resolve("output"), error = root.resolve("error");
        List<String> outLines = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ProcessHandler handler = ProcessHandler.OfProcess.of(List.of(Path.of(System.getProperty("java.home"),
                    "bin",
                    File.separatorChar == '\\' ? "java.exe" : "java").toString())).apply(List.of(source.toString()));
            assertThat(handler.execute(output,
                    error,
                    teed ? new ProcessHandler.Tee(executor, outLines::add, _ -> { }) : null))
                    .as("a program not known to be a JVM is read in the platform's encoding, and what that cannot"
                            + " decode never fails the build")
                    .isZero();
        } finally {
            executor.shutdown();
        }
        assertThat(Files.readAllLines(output, StandardCharsets.UTF_8))
                .as("a byte the platform's encoding cannot decode is replaced, and the file stays UTF-8")
                .hasSize(2)
                .endsWith("after");
        if (teed) {
            assertThat(outLines).hasSize(2).last().isEqualTo("after");
            assertThat(outLines.getFirst()).endsWith(" ok");
        }
    }

    @Test
    public void a_forked_process_reads_the_argument_file_it_is_handed() throws Exception {
        Path source = root.resolve("Echo.java");
        Files.writeString(source, """
                public class Echo {
                    public static void main(String[] args) {
                        System.out.println(String.join("|", args));
                    }
                }
                """);
        Path file = root.resolve("echo.args"), output = root.resolve("output"), error = root.resolve("error");
        ProcessHandler handler = ProcessHandler.OfProcess.ofJavaHome("bin/java").apply(List.of(
                "@" + ProcessBuildStep.argumentFile(file, List.of(source.toString(), "a b", "c\\d"))));
        assertThat(handler.external()).isTrue();
        assertThat(handler.execute(output, error, null)).isZero();
        assertThat(Files.readString(output).strip()).isEqualTo("a b|c\\d");
    }

    @Test
    public void a_tool_runs_within_the_build() {
        assertThat(ProcessHandler.OfTool.of("jar").apply(List.of("--version")).external()).isFalse();
    }

    @Test
    public void concurrent_image_tool_runs_all_succeed() throws Exception {
        List<Callable<Integer>> linkers = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Path image = root.resolve("image-" + index);
            Path output = root.resolve("output-" + index), error = root.resolve("error-" + index);
            ProcessHandler handler = ProcessHandler.OfTool.of("jlink").apply(List.of(
                    "--add-modules", "java.base",
                    "--output", image.toString()));
            linkers.add(() -> handler.execute(output, error, null));
        }
        List<Integer> codes = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(linkers.size())) {
            for (Future<Integer> linked : executor.invokeAll(linkers)) {
                codes.add(linked.get());
            }
        }
        assertThat(codes).containsOnly(0);
    }
}
