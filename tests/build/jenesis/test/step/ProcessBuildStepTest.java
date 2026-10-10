package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.step.EnvironmentalProcessBuildStep;
import build.jenesis.step.Findings;
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
    public void a_forked_program_receives_the_variables_of_each_of_its_configurations() throws IOException {
        Path folder = Files.createDirectories(root.resolve("argument/environment")).getParent();
        Files.writeString(folder.resolve("environment/java.properties"),
                "SAMPLE_LITERAL=java\nSAMPLE_SHARED=java\nSAMPLE_UNSET_IN_THE_BUILD\n");
        Files.writeString(folder.resolve("environment/test.properties"), "SAMPLE_SHARED=test\n");
        Path source = root.resolve("Variables.java");
        Files.writeString(source, """
                public class Variables {
                    public static void main(String[] args) {
                        for (String name : args) {
                            System.out.println(name + "=" + System.getenv(name));
                        }
                    }
                }
                """);
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        new Program("java",
                ProcessHandler.OfProcess.ofJavaHome("bin/java"),
                List.of("java", "test"),
                List.of(source.toString(), "SAMPLE_LITERAL", "SAMPLE_SHARED", "SAMPLE_UNSET_IN_THE_BUILD"))
                .apply(Runnable::run,
                        new BuildStepContext(null, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(folder, Map.of()))))
                .toCompletableFuture()
                .join();
        assertThat(Files.readAllLines(supplement.resolve("output"))).containsExactly(
                "SAMPLE_LITERAL=java",
                "SAMPLE_SHARED=test",
                "SAMPLE_UNSET_IN_THE_BUILD=null");
    }

    @Test
    public void a_failure_names_a_reproduction_quoted_for_a_shell() throws IOException {
        Path source = root.resolve("Failing.java");
        Files.writeString(source, """
                public class Failing {
                    public static void main(String[] args) {
                        System.exit(3);
                    }
                }
                """);
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        assertThatThrownBy(() -> new Program("java",
                ProcessHandler.OfProcess.ofJavaHome("bin/java"),
                List.of(),
                List.of(source.toString(), "-Xplugin:Sample -XepExcludedPaths:.*/(generated)/.*", "it's"))
                .apply(Runnable::run, new BuildStepContext(null, next, supplement), new LinkedHashMap<>())
                .toCompletableFuture()
                .join())
                .rootCause()
                .as("an argument holding spaces, parentheses or a quote stays one word when pasted into a shell")
                .hasMessageContaining(" '-Xplugin:Sample -XepExcludedPaths:.*/(generated)/.*' 'it'\\''s'");
    }

    @Test
    public void a_failure_prints_the_tail_of_a_long_output_and_names_the_file_that_keeps_all_of_it() throws IOException {
        Path source = root.resolve("Verbose.java");
        Files.writeString(source, """
                public class Verbose {
                    public static void main(String[] args) {
                        for (int line = 1; line <= 5000; line++) {
                            System.out.println("printed line " + line);
                        }
                        System.err.println("the reason it failed");
                        System.exit(1);
                    }
                }
                """);
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        assertThatThrownBy(() -> new Program("java",
                ProcessHandler.OfProcess.ofJavaHome("bin/java"),
                List.of(),
                List.of(source.toString()))
                .apply(Runnable::run, new BuildStepContext(null, next, supplement), new LinkedHashMap<>())
                .toCompletableFuture()
                .join())
                .rootCause()
                .as("a failing step prints a bounded tail of what it wrote, and where the rest of it is")
                .hasMessageContaining("printed line 5000\n")
                .hasMessageContaining("printed line 4801\n")
                .hasMessageNotContaining("printed line 4800\n")
                .hasMessageContaining(supplement.resolve("output").toString())
                .hasMessageContaining("the reason it failed")
                .hasMessageNotContaining(supplement.resolve("error").toString());
        assertThat(Files.readAllLines(supplement.resolve("output")))
                .as("the supplement keeps the whole output")
                .hasSize(5000);
    }

    @Test
    public void a_jdk_tool_refuses_an_environment_even_when_forked() throws IOException {
        Path folder = Files.createDirectories(root.resolve("argument/environment")).getParent();
        Files.writeString(folder.resolve("environment/javac.properties"), "SAMPLE=value\n");
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        ProcessBuildStep step = new ProcessBuildStep("javac", ProcessHandler.OfProcess.ofJavaHome("bin/javac")) {
            @Override
            protected CompletionStage<List<String>> process(Executor executor,
                                                            BuildStepContext context,
                                                            SequencedMap<String, BuildStepArgument> arguments,
                                                            SequencedMap<String, SequencedMap<String, String>> properties) {
                return CompletableFuture.completedStage(List.of("--version"));
            }
        };
        assertThatThrownBy(() -> step.apply(Runnable::run,
                new BuildStepContext(null, next, supplement),
                new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(folder, Map.of())))).toCompletableFuture().join())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[SAMPLE] to javac, which takes no environment");
    }

    @Test
    public void a_program_run_through_the_tools_api_refuses_an_environment() throws IOException {
        Path folder = Files.createDirectories(root.resolve("argument/environment")).getParent();
        Files.writeString(folder.resolve("environment/probe.properties"), "SAMPLE=value\n");
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        ToolProvider tool = new ToolProvider() {
            @Override
            public String name() {
                return "probe";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                return 0;
            }
        };
        assertThatThrownBy(() -> new Program("probe", ProcessHandler.OfTool.of(tool), List.of("probe"), List.of())
                .apply(Runnable::run,
                        new BuildStepContext(null, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(folder, Map.of()))))
                .toCompletableFuture()
                .join())
                .as("a tool sharing the build's JVM cannot be handed variables of its own")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[SAMPLE] to probe, which runs through the Tools API");
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
    public void bounds_the_processes_running_at_once_to_the_processor_count_by_default() {
        assertThat(ProcessBuildStep.Terms.ofEnvironment(Environment.NONE, "probe").permits())
                .as("an unset limit is one process per processor, so a build of many modules forks no more JVMs than it can run")
                .isNotNull()
                .isSameAs(ProcessBuildStep.Terms.ofEnvironment(new Environment(Map.of("process.concurrency",
                        Integer.toString(Runtime.getRuntime().availableProcessors()))), "probe").permits());
        assertThat(ProcessBuildStep.Terms.ofEnvironment(new Environment(Map.of("process.concurrency", "0")), "probe")
                .permits())
                .as("0 is unbounded")
                .isNull();
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
        }, new Environment(Map.of("process.concurrency", "0"))));
    }

    @Test
    public void rejects_a_negative_limit() {
        assertThatThrownBy(() -> new Probe(new Environment(Map.of("process.concurrency", "-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    public void a_linter_that_found_something_prints_its_findings_and_where_its_report_lands() throws IOException {
        List<String> printed = new ArrayList<>();
        BuildStepResult result = lint(new Linter(new Environment(Map.of()).out(printed::add), 0, false, false));
        assertThat(result.next()).isTrue();
        assertThat(printed).singleElement().asString()
                .contains("[FINDINGS]")
                .as("the line names the folder the report lands in once the step completes")
                .contains("linter found 2 findings, reported in " + root.resolve("check").resolve("output").resolve("report.xml"));
    }

    @Test
    public void a_report_only_linter_accepts_the_violations_its_exit_code_reports() throws IOException {
        List<String> printed = new ArrayList<>();
        assertThat(lint(new Linter(new Environment(Map.of()).out(printed::add), 1, true, false)).next()).isTrue();
        assertThat(printed).singleElement().asString().contains("linter found 2 findings");
    }

    @Test
    public void a_strict_linter_fails_the_build_on_the_violations_its_exit_code_reports() {
        assertThatThrownBy(() -> lint(new Linter(new Environment(Map.of()).out(_ -> { }), 1, true, true)))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("linter found 2 findings, reported in " + root.resolve("check~").resolve("output").resolve("report.xml"))
                .hasMessageContaining("jenesis.source.linter=strict");
    }

    @Test
    public void a_strict_linter_accepts_the_findings_its_exit_code_does_not_count_as_violations() throws IOException {
        List<String> printed = new ArrayList<>();
        assertThat(lint(new Linter(new Environment(Map.of()).out(printed::add), 0, true, true)).next()).isTrue();
        assertThat(printed).singleElement().asString().contains("linter found 2 findings");
    }

    @Test
    public void a_strict_linter_whose_exit_code_judges_nothing_fails_on_any_finding_of_its_report() {
        assertThatThrownBy(() -> lint(new Linter(new Environment(Map.of()).out(_ -> { }), 0, false, true)))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("linter found 2 findings");
    }

    @Test
    public void the_findings_line_is_left_out_without_an_environment_or_when_switched_off() throws IOException {
        List<String> printed = new ArrayList<>();
        assertThat(lint(new Linter(new Environment(Map.of("print.findings", "false")).out(printed::add), 0, false, false)).next())
                .isTrue();
        assertThat(printed).isEmpty();
        assertThat(new ProcessBuildStep.Terms().reporting())
                .as("a step built without an environment prints nothing")
                .isNull();
    }

    private BuildStepResult lint(Linter linter) throws IOException {
        Path step = Files.createDirectory(root.resolve("check~"));
        return linter.apply(Runnable::run,
                new BuildStepContext(null,
                        Files.createDirectory(step.resolve("output")),
                        Files.createDirectory(step.resolve("supplement"))),
                new LinkedHashMap<>()).toCompletableFuture().join();
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

    private record Reporting(int code) implements ToolProvider {

        @Override
        public String name() {
            return "linter";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... arguments) {
            try {
                Files.writeString(Path.of(arguments[0]), """
                        <checkstyle><file name="Sample.java"><error/><error/></file></checkstyle>
                        """);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return code;
        }
    }

    private static final class Linter extends ProcessBuildStep {

        private final boolean judged, strict;

        private Linter(Environment environment, int code, boolean judged, boolean strict) {
            super("linter", ProcessHandler.OfTool.of(new Reporting(code)), Terms.ofEnvironment(environment, "linter"));
            this.judged = judged;
            this.strict = strict;
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties) {
            return CompletableFuture.completedStage(List.of(context.next().resolve("report.xml").toString()));
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Path report = context.next().resolve("report.xml");
            return Findings.ofXml("linter", report, "error")
                    .acceptable(code, context, judged, strict, "source.linter", terms.reporting());
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
            super("gated", ProcessHandler.OfTool.of(provider), new Terms().permits(permits));
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
            super("probe", arguments -> HANDLER, new Terms().printing(printing));
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

    private static class Program extends EnvironmentalProcessBuildStep {

        private final List<String> configurations, processed;

        private Program(String command,
                        Function<List<String>, ? extends ProcessHandler> factory,
                        List<String> configurations,
                        List<String> processed) {
            super(command, factory);
            this.configurations = configurations;
            this.processed = processed;
        }

        @Override
        protected List<String> configurations() {
            return configurations;
        }

        @Override
        protected CompletionStage<List<String>> process(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments,
                                                        SequencedMap<String, SequencedMap<String, String>> properties) {
            return CompletableFuture.completedStage(processed);
        }
    }
}
