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
