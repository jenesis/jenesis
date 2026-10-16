package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.step.ProcessHandler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class WarmJavaTest {

    @TempDir
    private Path classes, run;

    private int index;

    @BeforeEach
    public void compile() throws IOException {
        Path source = Files.writeString(classes.resolve("Counter.java"), """
                public class Counter {
                    private static int count;
                    public static void main(String[] arguments) {
                        count++;
                        System.out.println(count + " " + String.join(",", arguments));
                        if (arguments.length > 0 && arguments[0].equals("exit")) {
                            System.exit(3);
                        }
                        if (arguments.length > 0 && arguments[0].equals("fail")) {
                            throw new IllegalStateException("failed on purpose");
                        }
                        System.err.println("error " + count);
                    }
                }
                """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,
                null,
                null,
                "-d",
                classes.toString(),
                source.toString())).isZero();
    }

    @Test
    public void runs_the_main_method_of_a_tool_again_in_the_jvm_it_ran_in_before() throws IOException {
        assertThat(run(true, "-cp", classes.toString(), "Counter", "a")).isEqualTo("0:1 a");
        assertThat(run(true, "-cp", classes.toString(), "Counter", "b"))
                .as("the tool's static state shows that it ran in the JVM of the run before")
                .isEqualTo("0:2 b");
        assertThat(run.resolve("error-1.txt")).content().contains("error 2");
    }

    @Test
    public void runs_a_tool_again_in_the_jvm_it_ran_in_when_its_jar_is_linked_at_another_path() throws IOException {
        Path jar = jar(Files.createDirectories(run.resolve("first")));
        Path linked = Files.createLink(Files.createDirectories(run.resolve("second")).resolve("counter.jar"), jar);
        assertThat(run(true, "-cp", jar.toString(), "Counter", "a")).isEqualTo("0:1 a");
        assertThat(run(true, "-cp", linked.toString(), "Counter", "b"))
                .as("the same jar at another path, as every module of a build resolves a tool, reuses the JVM")
                .isEqualTo("0:2 b");
    }

    @Test
    public void runs_a_tool_again_in_the_jvm_it_ran_in_after_the_path_it_started_from_is_gone() throws IOException {
        Path jar = jar(Files.createDirectories(run.resolve("removed")));
        Path linked = Files.createLink(Files.createDirectories(run.resolve("kept")).resolve("counter.jar"), jar);
        assertThat(run(true, "-cp", jar.toString(), "Counter", "a")).isEqualTo("0:1 a");
        Files.delete(jar);
        assertThat(run(true, "-cp", linked.toString(), "Counter", "b"))
                .as("the JVM reads its class path from links of its own")
                .isEqualTo("0:2 b");
    }

    @Test
    public void ends_a_call_with_the_code_a_main_method_returns() throws IOException {
        Path dedicated = compiled("coded",
                "Coded", """
                public class Coded {
                    public static int main(String[] arguments) {
                        System.out.println("coded");
                        return Integer.parseInt(arguments[0]);
                    }
                }
                """);
        assertThat(run(true, "-cp", dedicated.toString(), "Coded", "4")).isEqualTo("4:coded");
        assertThat(run(true, "-cp", dedicated.toString(), "Coded", "0"))
                .as("returning a code keeps the JVM for the next call")
                .isEqualTo("0:coded");
    }

    @Test
    public void ends_a_call_in_a_jvm_of_its_own_with_the_code_a_main_method_returns() throws IOException {
        Path dedicated = compiled("forked",
                "Coded", """
                public class Coded {
                    public static int main(String[] arguments) {
                        System.out.println("coded");
                        return Integer.parseInt(arguments[0]);
                    }
                }
                """);
        assertThat(run(false, "-cp", dedicated.toString(), "Coded", "4"))
                .as("a code returned rather than passed to System.exit means the same in a JVM of its own")
                .isEqualTo("4:coded");
    }

    @Test
    @Timeout(60)
    public void ends_a_jvm_of_its_own_when_the_main_method_returns_while_a_thread_it_started_runs_on() throws IOException {
        Path dedicated = compiled("lingering",
                "Lingering", """
                public class Lingering {
                    public static void main(String[] arguments) {
                        Thread.ofPlatform().start(() -> {
                            try {
                                Thread.sleep(Long.MAX_VALUE);
                            } catch (InterruptedException _) {
                            }
                        });
                        System.out.println("returned");
                    }
                }
                """);
        assertThat(run(false, "-cp", dedicated.toString(), "Lingering"))
                .as("a thread a tool leaves running does not keep its JVM alive once the call ended")
                .isEqualTo("0:returned");
    }

    @Test
    public void writes_a_driver_once_to_a_folder_of_its_own_that_a_jvm_runs_it_from() throws IOException {
        Path driver = ProcessHandler.OfProcess.driver(Driver.class);
        assertThat(driver.resolve(Driver.class.getName().replace('.', '/') + ".class")).exists();
        assertThat(driver.resolve(Driver.Nested.class.getName().replace('.', '/') + ".class"))
                .as("a class the driver nests travels with it")
                .exists();
        assertThat(ProcessHandler.OfProcess.driver(Driver.class))
                .as("the same class files are written once, so a warm JVM is handed the same folder")
                .isEqualTo(driver);
        assertThat(run(false, "-cp", driver.toString(), Driver.class.getName(), "7")).isEqualTo("7:driven 7");
    }

    @Test
    public void trains_an_ahead_of_time_cache_on_the_first_call_in_a_jvm_of_its_own_and_reads_it_on_the_next()
            throws IOException {
        Path aot = run.resolve("aot");
        assertThat(forked(aot, "-cp", classes.toString(), "Counter", "a")).isEqualTo("0:1 a");
        Path cache = caches(aot).getFirst();
        FileTime trained = Files.getLastModifiedTime(cache);
        assertThat(cache.getParent())
                .as("the class path is linked into a folder of its own, a folder of classes packed as a jar")
                .isDirectoryContaining(file -> file.getFileName().toString().endsWith(".jar"));
        assertThat(forked(aot, "-cp", classes.toString(), "Counter", "b")).isEqualTo("0:1 b");
        assertThat(caches(aot))
                .as("the second call reads the cache the first one trained rather than training another")
                .containsExactly(cache);
        assertThat(Files.getLastModifiedTime(cache)).isEqualTo(trained);
        assertThat(forked(aot, "-cp", classes.toString(), "Counter", "exit"))
                .as("a code a tool exits with is its code under a cache as well")
                .isEqualTo("3:1 exit");
    }

    @Test
    public void keeps_a_jvm_warm_rather_than_caching_it_when_both_are_asked_for() throws IOException {
        Path aot = run.resolve("aot");
        Path output = run.resolve("warm-output.txt"), error = run.resolve("warm-error.txt");
        List<String> printed = new ArrayList<>();
        assertThat(ProcessHandler.OfProcess.ofJavaMain()
                .apply(List.of("-cp", classes.toString(), "Counter", "a"))
                .aot(aot)
                .execute(output, error, new ProcessHandler.Tee(Thread.ofVirtual()::start, printed::add, _ -> { })))
                .isZero();
        assertThat(aot).as("a JVM kept warm outlives the build, which a cache trained at its end would not serve")
                .doesNotExist();
    }

    public static final class Driver {

        public static int main(String[] arguments) {
            return new Nested().code(arguments[0]);
        }

        private static final class Nested {

            private int code(String argument) {
                System.out.println("driven " + argument);
                return Integer.parseInt(argument);
            }
        }
    }

    @Test
    public void runs_every_main_method_in_a_jvm_of_its_own_when_not_warm() throws IOException {
        assertThat(run(false, "-cp", classes.toString(), "Counter", "a")).isEqualTo("0:1 a");
        assertThat(run(false, "-cp", classes.toString(), "Counter", "b")).isEqualTo("0:1 b");
    }

    @Test
    public void reports_the_code_a_tool_exits_with_and_runs_the_next_in_a_fresh_jvm() throws IOException {
        Path dedicated = Files.createDirectories(run.resolve("exit"));
        Files.copy(classes.resolve("Counter.class"), dedicated.resolve("Counter.class"));
        assertThat(run(true, "-cp", dedicated.toString(), "Counter", "a")).isEqualTo("0:1 a");
        assertThat(run(true, "-cp", dedicated.toString(), "Counter", "exit")).isEqualTo("3:2 exit");
        assertThat(run(true, "-cp", dedicated.toString(), "Counter", "b"))
                .as("a tool that exits takes its JVM with it")
                .isEqualTo("0:1 b");
    }

    @Test
    public void reports_a_tool_that_throws_as_a_failure_with_its_stack_trace() throws IOException {
        Path dedicated = Files.createDirectories(run.resolve("fail"));
        Files.copy(classes.resolve("Counter.class"), dedicated.resolve("Counter.class"));
        assertThat(run(true, "-cp", dedicated.toString(), "Counter", "fail")).isEqualTo("1:1 fail");
        assertThat(run.resolve("error-0.txt")).content().contains("failed on purpose");
    }

    @Test
    public void compiles_a_launcher_source_once_and_runs_its_main_method() throws IOException {
        Path launcher = Files.writeString(run.resolve("Launcher.java"), """
                void main(String[] arguments) {
                    System.out.println("launched " + String.join(",", arguments) + " " + new Counter().getClass().getName());
                }
                """);
        assertThat(run(true, "-cp", classes.toString(), launcher.toString(), "a")).isEqualTo("0:launched a Counter");
        assertThat(run(true, "-cp", classes.toString(), launcher.toString(), "b")).isEqualTo("0:launched b Counter");
    }

    @Test
    public void runs_a_launcher_source_in_a_jvm_of_its_own_when_not_warm() throws IOException {
        Path launcher = Files.writeString(run.resolve("Launcher.java"), """
                void main(String[] arguments) {
                    System.out.println("launched " + String.join(",", arguments) + " " + new Counter().getClass().getName());
                }
                """);
        assertThat(run(false, "-cp", classes.toString(), launcher.toString(), "a")).isEqualTo("0:launched a Counter");
    }

    @Test
    public void runs_a_command_line_it_cannot_run_warm_in_a_jvm_of_its_own() throws IOException {
        assertThat(run(true, "--add-modules", "java.sql", "-cp", classes.toString(), "Counter", "a")).isEqualTo("0:1 a");
        assertThat(run(true, "--add-modules", "java.sql", "-cp", classes.toString(), "Counter", "b"))
                .as("an option with a separate value is not read, so the tool is forked")
                .isEqualTo("0:1 b");
    }

    @Test
    public void runs_javac_that_its_options_fork_again_in_the_jvm_it_ran_in_before_when_warm() throws Exception {
        Path processor = Files.writeString(classes.resolve("Pid.java"), """
                import javax.annotation.processing.*;
                import javax.lang.model.SourceVersion;
                import javax.lang.model.element.TypeElement;
                import java.util.Set;
                @SupportedAnnotationTypes("*")
                public class Pid extends AbstractProcessor {
                    @Override
                    public SourceVersion getSupportedSourceVersion() {
                        return SourceVersion.latestSupported();
                    }
                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
                        if (round.processingOver()) {
                            try {
                                Thread.sleep(1_000L);
                            } catch (InterruptedException e) {
                                throw new IllegalStateException(e);
                            }
                            System.out.println("pid " + ProcessHandle.current().pid());
                        }
                        return false;
                    }
                }
                """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,
                null,
                null,
                "-d",
                classes.toString(),
                processor.toString())).isZero();
        Path source = Files.writeString(run.resolve("Sample.java"), "class Sample { }");

        assertThat(javac(true, source, 0)).as("the warm JVM ran both compilations").isEqualTo(javac(true, source, 1));
        assertThat(javac(false, source, 2)).as("without warmth, every compilation forks").isNotEqualTo(javac(false, source, 3));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> javac(true, source, 4)),
                    second = executor.submit(() -> javac(true, source, 5));
            assertThat(first.get())
                    .as("the warm JVM runs two compilations side by side, each printing only what it printed itself")
                    .isEqualTo(second.get());
        }
    }

    @Test
    public void runs_the_main_methods_of_shared_calls_side_by_side_in_one_jvm() throws Exception {
        Path source = Files.writeString(classes.resolve("Sleeper.java"), """
                public class Sleeper {
                    public static void main(String[] arguments) throws InterruptedException {
                        Thread.sleep(1_000L);
                        System.out.println(arguments[0] + " " + ProcessHandle.current().pid());
                    }
                }
                """);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,
                null,
                null,
                "-d",
                classes.toString(),
                source.toString())).isZero();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> sleeper(true, "first", 6)),
                    second = executor.submit(() -> sleeper(true, "second", 7));
            assertThat(first.get()).startsWith("first ");
            assertThat(second.get()).startsWith("second ");
            assertThat(first.get().substring("first ".length()))
                    .as("shared calls run side by side in one JVM, each printing only what it printed itself")
                    .isEqualTo(second.get().substring("second ".length()));
            Future<String> third = executor.submit(() -> sleeper(false, "third", 8)),
                    fourth = executor.submit(() -> sleeper(false, "fourth", 9));
            assertThat(third.get().substring("third ".length()))
                    .as("calls that are not shared each take a JVM of their own")
                    .isNotEqualTo(fourth.get().substring("fourth ".length()));
        }
    }

    private String sleeper(boolean shared, String name, int index) throws IOException {
        Path output = run.resolve("sleeper-output-" + index + ".txt"), error = run.resolve("sleeper-error-" + index + ".txt");
        List<String> printed = new ArrayList<>();
        int code = ProcessHandler.OfProcess.ofJavaMain()
                .apply(List.of("-cp", classes.toString(), "Sleeper", name))
                .shared(shared)
                .execute(output, error, new ProcessHandler.Tee(Thread.ofVirtual()::start, printed::add, _ -> { }));
        assertThat(code).isZero();
        assertThat(printed).singleElement();
        return printed.getFirst();
    }

    private String javac(boolean daemon, Path source, int index) throws IOException {
        Path output = run.resolve("javac-output-" + index + ".txt"), error = run.resolve("javac-error-" + index + ".txt");
        List<String> printed = new ArrayList<>();
        int code = ProcessHandler.OfProcess.ofJavaHome("bin/javac")
                .apply(List.of("-J-Xshare:auto",
                        "-proc:only",
                        "-processorpath",
                        classes.toString(),
                        "-processor",
                        "Pid",
                        source.toString()))
                .daemon(daemon)
                .execute(output, error, new ProcessHandler.Tee(Thread.ofVirtual()::start, printed::add, _ -> { }));
        assertThat(code).as("javac compiles %s", source).isZero();
        assertThat(printed).singleElement().asString().startsWith("pid ");
        return printed.getFirst();
    }

    private String forked(Path aot, String... arguments) throws IOException {
        Path output = run.resolve("output-" + index + ".txt"), error = run.resolve("error-" + index + ".txt");
        index++;
        List<String> printed = new ArrayList<>();
        int code = ProcessHandler.OfProcess.ofJavaMain()
                .apply(List.of(arguments))
                .daemon(false)
                .aot(aot)
                .execute(output, error, new ProcessHandler.Tee(Thread.ofVirtual()::start, printed::add, _ -> { }));
        return code + ":" + String.join("|", printed);
    }

    private static List<Path> caches(Path aot) throws IOException {
        try (Stream<Path> files = Files.walk(aot)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".aot")).toList();
        }
    }

    private Path compiled(String folder, String type, String code) throws IOException {
        Path target = Files.createDirectories(run.resolve(folder)),
                source = Files.writeString(Files.createDirectories(run.resolve(folder + "-source")).resolve(type + ".java"),
                        code);
        assertThat(ToolProvider.getSystemJavaCompiler().run(null,
                null,
                null,
                "-d",
                target.toString(),
                source.toString())).isZero();
        return target;
    }

    private Path jar(Path folder) throws IOException {
        Path jar = folder.resolve("counter.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("Counter.class"));
            output.write(Files.readAllBytes(classes.resolve("Counter.class")));
            output.closeEntry();
            output.putNextEntry(new JarEntry("run.txt"));
            output.write(run.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private String run(boolean daemon, String... arguments) throws IOException {
        Path output = run.resolve("output-" + index + ".txt"), error = run.resolve("error-" + index + ".txt");
        index++;
        List<String> printed = new ArrayList<>();
        int code = ProcessHandler.OfProcess.ofJavaMain()
                .apply(List.of(arguments))
                .daemon(daemon)
                .execute(output, error, new ProcessHandler.Tee(Thread.ofVirtual()::start, printed::add, _ -> { }));
        assertThat(Files.readAllLines(output)).containsExactlyElementsOf(printed);
        return code + ":" + String.join("|", printed);
    }
}
