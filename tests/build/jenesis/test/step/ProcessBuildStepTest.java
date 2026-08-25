package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProcessBuildStepTest {

    @BeforeEach
    @AfterEach
    public void clear() {
        System.clearProperty("jenesis.print.process");
        System.clearProperty("jenesis.print.probe");
        System.clearProperty("jenesis.process.concurrency");
    }

    @TempDir
    private Path root;

    @Test
    public void a_step_merges_the_process_properties_of_each_of_its_commands() throws IOException {
        Path folder = Files.createDirectories(root.resolve("argument/process")).getParent();
        Files.writeString(folder.resolve("process/java.properties"), "-Xmx=512m\n-Dshared=java\n");
        Files.writeString(folder.resolve("process/test.properties"), "-Dshared=test\n-Dextra=test\n");
        Path next = Files.createDirectory(root.resolve("next")), supplement = Files.createDirectory(root.resolve("supplement"));
        AtomicReference<SequencedMap<String, SequencedMap<String, String>>> captured = new AtomicReference<>();
        ProcessBuildStep step = new ProcessBuildStep("java", ProcessHandler.OfProcess.ofJavaHome("bin/java"), false) {
            @Override
            protected List<String> commands() {
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
    public void the_command_specific_property_enables_streaming() {
        System.setProperty("jenesis.print.probe", "true");
        assertThat(new Probe().streams()).isTrue();
    }

    @Test
    public void the_generic_property_enables_streaming() {
        System.setProperty("jenesis.print.process", "true");
        assertThat(new Probe().streams()).isTrue();
    }

    @Test
    public void the_command_specific_property_takes_precedence_over_the_generic_one() {
        System.setProperty("jenesis.print.process", "true");
        System.setProperty("jenesis.print.probe", "false");
        assertThat(new Probe().streams()).isFalse();
    }

    @Test
    public void an_explicit_value_overrides_the_resolved_property() {
        assertThat(new Probe(true).streams()).isTrue();
        System.setProperty("jenesis.print.process", "true");
        assertThat(new Probe(false).streams()).isFalse();
    }

    @Test
    public void limits_the_processes_running_at_once() throws Exception {
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        Semaphore permits = new Semaphore(1);
        run(() -> new Gated(counting(running, peak), permits));
        assertThat(peak).hasValue(1);
    }

    @Test
    public void shares_the_limit_of_the_property_between_steps() throws Exception {
        System.setProperty("jenesis.process.concurrency", "2");
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        run(() -> new Gated(counting(running, peak)));
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
        System.setProperty("jenesis.process.concurrency", "-1");
        assertThatThrownBy(Probe::new)
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
            super("gated", ProcessHandler.OfTool.of(provider), false);
        }

        private Gated(ToolProvider provider, Semaphore permits) {
            super("gated", ProcessHandler.OfTool.of(provider), false, permits);
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

        private Probe() {
            super("probe", arguments -> HANDLER);
        }

        private Probe(boolean verbose) {
            super("probe", arguments -> HANDLER, verbose);
        }

        private boolean streams() {
            PrintStream original = System.out;
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            try {
                return tee(Runnable::run, HANDLER) != null;
            } finally {
                System.setOut(original);
            }
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
