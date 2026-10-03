package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

import build.jenesis.Environment;
import build.jenesis.SequencedProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProcessBuildStepTest {

    @TempDir
    private Path root;

    @Test
    public void a_step_merges_the_process_properties_of_each_of_its_configurations() throws IOException {
        Path folder = Files.createDirectories(root.resolve("argument/process")).getParent();
        Files.writeString(folder.resolve("process/java.properties"), "-Xmx=512m\n-Dshared=java\n");
        Files.writeString(folder.resolve("process/test.properties"), "-Dshared=test\n-Dextra=test\n");
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        AtomicReference<SequencedMap<String, SequencedMap<String, String>>> captured = new AtomicReference<>();
        ProcessBuildStep step = new ProcessBuildStep("java", ProcessHandler.OfProcess.ofJavaHome("bin/java")) {
            @Override
            protected List<String> configurations() {
                return List.of("java", "test");
            }

            @Override
            protected CompletionStage<List<String>> process(Executor executor,
                                                            BuildStepContext context,
                                                            SequencedMap<String, BuildStepArgument> arguments,
                                                            SequencedMap<String, SequencedMap<String, String>> properties) {
                captured.set(properties);
                return CompletableFuture.completedStage(null);
            }
        };
        step.apply(Runnable::run,
                new BuildStepContext(null, next, supplement),
                new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(folder, Map.of())))).toCompletableFuture().join();
        assertThat(captured.get().get("argument")).containsExactly(
                Map.entry("-Xmx", "512m"),
                Map.entry("-Dshared", "test"),
                Map.entry("-Dextra", "test"));
    }

    @Test
    public void a_staged_program_reads_no_process_configuration() throws IOException {
        Path folder = Files.createDirectories(root.resolve("argument/process")).getParent();
        Files.writeString(folder.resolve("process/protoc.properties"), "-Xmx=512m\n");
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        AtomicReference<SequencedMap<String, SequencedMap<String, String>>> captured = new AtomicReference<>();
        ProcessBuildStep step = new ProcessBuildStep("protoc", ProcessHandler.OfProcess.ofStaged()) {
            @Override
            protected CompletionStage<List<String>> process(Executor executor,
                                                            BuildStepContext context,
                                                            SequencedMap<String, BuildStepArgument> arguments,
                                                            SequencedMap<String, SequencedMap<String, String>> properties) {
                captured.set(properties);
                return CompletableFuture.completedStage(null);
            }
        };
        step.apply(Runnable::run,
                new BuildStepContext(null, next, supplement),
                new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(folder, Map.of())))).toCompletableFuture().join();
        assertThat(captured.get().get("argument"))
                .as("a step that stages its own program must not have options prepended before it")
                .isEmpty();
    }

    @Test
    public void the_command_specific_setting_enables_streaming() {
        List<String> printed = new ArrayList<>();
        assertThat(new Probe(new Environment(Map.of("print.probe", "true"), printed::add, printed::add)).streams())
                .isTrue();
        assertThat(printed)
                .as("the lines go to the output the run was given, never to the stream of the JVM")
                .isNotEmpty();
    }

    @Test
    public void the_generic_setting_enables_streaming() {
        assertThat(new Probe(new Environment(Map.of("print.process", "true"))).streams()).isTrue();
    }

    @Test
    public void the_command_specific_setting_takes_precedence_over_the_generic_one() {
        assertThat(new Probe(new Environment(Map.of("print.process", "true", "print.probe", "false"))).streams())
                .isFalse();
    }

    @Test
    public void an_explicit_consumer_overrides_the_resolved_setting() {
        assertThat(new Probe((_, _) -> {
        }).streams())
                .as("a consumer supplied by the caller is what the lines are handed to")
                .isTrue();
        assertThat(new Probe((BiConsumer<Boolean, String>) null).streams())
                .as("no consumer means the lines go nowhere, whatever the setting says")
                .isFalse();
    }

    @Test
    public void limits_the_processes_running_at_once() throws Exception {
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        Semaphore permits = new Semaphore(1);
        run(() -> new Gated(counting(running, peak), permits));
        assertThat(peak).hasValue(1);
    }

    @Test
    public void shares_the_limit_of_the_setting_between_steps() throws Exception {
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        run(() -> new Gated(counting(running, peak), new Environment(Map.of("process.concurrency", "2"))));
        assertThat(peak).hasValueLessThanOrEqualTo(2);
        assertThat(peak).hasValueGreaterThan(0);
    }

    @Test
    public void runs_every_process_at_once_without_a_limit() throws Exception {
        CountDownLatch started = new CountDownLatch(4);
        run(() -> new Gated(new ToolProvider() {
            @Override
            public String name() {
                return "gated";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                started.countDown();
                try {
                    if (!started.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Processes did not run concurrently");
                    }
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return 0;
            }
        }));
    }

    @Test
    public void rejects_a_negative_limit() {
        assertThatThrownBy(() -> new Probe(new Environment(Map.of("process.concurrency", "-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    private void run(Supplier<ProcessBuildStep> steps) throws Exception {
        List<CompletionStage<BuildStepResult>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 4; index++) {
                Path folder = Files.createDirectories(root.resolve(Integer.toString(index)));
                results.add(steps.get().apply(executor,
                        new BuildStepContext(null,
                                Files.createDirectory(folder.resolve("next")),
                                Files.createDirectory(folder.resolve("supplement"))),
                        new LinkedHashMap<>()));
            }
            for (CompletionStage<BuildStepResult> result : results) {
                assertThat(result.toCompletableFuture().join().next()).isTrue();
            }
        }
    }

    private static ToolProvider counting(AtomicInteger running, AtomicInteger peak) {
        return new ToolProvider() {
            @Override
            public String name() {
                return "gated";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } finally {
                    running.decrementAndGet();
                }
                return 0;
            }
        };
    }

    @Test
    public void inlines_the_first_and_the_last_lines_of_a_long_failure_and_names_the_file_holding_them_all()
            throws IOException {
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        Loud step = new Loud(250, new Environment(Map.of("print.lines", "10")));
        assertThatThrownBy(() -> step.apply(Runnable::run, new BuildStepContext(null, next, supplement), new LinkedHashMap<>())
                .toCompletableFuture()
                .join())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Output - the first 4 and the last 6 of 250 lines, all of them in "
                        + supplement.resolve("output").toAbsolutePath().normalize() + ":\nline 0\nline 1\nline 2\nline 3\n[...]\nline 244\n")
                .hasMessageEndingWith("line 248\nline 249")
                .satisfies(thrown -> assertThat(thrown.getMessage())
                        .as("the middle of a long output is in the file, not in the failure")
                        .doesNotContain("line 100\n")
                        .contains("Error - the first 4 and the last 6 of 250 lines"));
        assertThat(supplement.resolve("output")).content().contains("line 100");
    }

    @Test
    public void inlines_a_short_failure_whole() throws IOException {
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        Loud step = new Loud(3, Environment.NONE);
        assertThatThrownBy(() -> step.apply(Runnable::run, new BuildStepContext(null, next, supplement), new LinkedHashMap<>())
                .toCompletableFuture()
                .join())
                .rootCause()
                .hasMessage("Unexpected exit code: 1\nTo reproduce, execute in " + Path.of("").toAbsolutePath()
                        + ":\n loud\n\nOutput:\nline 0\nline 1\nline 2\n\nError:\nline 0\nline 1\nline 2");
    }

    @Test
    public void quotes_an_argument_a_shell_would_split_in_the_command_that_reproduces_a_failure() throws IOException {
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        Loud step = new Loud(0, Environment.NONE, List.of("-d", "a folder", "", "say \"hi\""));
        assertThatThrownBy(() -> step.apply(Runnable::run, new BuildStepContext(null, next, supplement), new LinkedHashMap<>())
                .toCompletableFuture()
                .join())
                .rootCause()
                .as("the command is pasted as it stands, in the folder the tool ran in")
                .hasMessage("Unexpected exit code: 1\nTo reproduce, execute in " + Path.of("").toAbsolutePath()
                        + ":\n loud -d \"a folder\" \"\" \"say \\\"hi\\\"\"");
    }

    @Test
    public void inlines_a_long_failure_whole_when_asked_to() throws IOException {
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        Loud step = new Loud(250, new Environment(Map.of("print.lines", "0")));
        assertThatThrownBy(() -> step.apply(Runnable::run, new BuildStepContext(null, next, supplement), new LinkedHashMap<>())
                .toCompletableFuture()
                .join())
                .rootCause()
                .hasMessageContaining("\nline 100\n")
                .hasMessageNotContaining("[...]");
    }

    @Test
    public void refuses_a_negative_number_of_lines() {
        assertThatThrownBy(() -> ProcessBuildStep.Terms.ofEnvironment(new Environment(Map.of("print.lines", "-1")), "loud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.print.lines");
    }

    private static final class Loud extends ProcessBuildStep {

        private final List<String> arguments;

        private Loud(int lines, Environment environment) {
            this(lines, environment, List.of());
        }

        private Loud(int lines, Environment environment, List<String> arguments) {
            super("loud", ProcessHandler.OfTool.of(new ToolProvider() {
                @Override
                public String name() {
                    return "loud";
                }

                @Override
                public int run(PrintWriter out, PrintWriter err, String... arguments) {
                    for (int line = 0; line < lines; line++) {
                        out.println("line " + line);
                        err.println("line " + line);
                    }
                    return 1;
                }
            }), Terms.ofEnvironment(environment, "loud"));
            this.arguments = arguments;
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties) {
            return CompletableFuture.completedStage(this.arguments);
        }
    }

    private static final class Gated extends ProcessBuildStep {

        private Gated(ToolProvider provider) {
            this(provider, Environment.NONE);
        }

        private Gated(ToolProvider provider, Environment environment) {
            super("gated", ProcessHandler.OfTool.of(provider), Terms.ofEnvironment(environment, "gated"));
        }

        private Gated(ToolProvider provider, Semaphore permits) {
            super("gated", ProcessHandler.OfTool.of(provider), new Terms(null, permits, null));
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties) {
            return CompletableFuture.completedStage(List.of());
        }
    }

    private static final class Probe extends ProcessBuildStep {

        private static final ProcessHandler HANDLER = ProcessHandler.OfTool.of(new ToolProvider() {
            @Override
            public String name() {
                return "probe";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                return 0;
            }
        }).apply(List.of());

        private Probe(Environment environment) {
            super("probe", arguments -> HANDLER, Terms.ofEnvironment(environment, "probe"));
        }

        private Probe(BiConsumer<Boolean, String> printing) {
            super("probe", arguments -> HANDLER, Terms.of("probe").printing(printing));
        }

        private boolean streams() {
            return tee(Runnable::run, HANDLER) != null;
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties) {
            return CompletableFuture.completedStage(List.of());
        }
    }
}
