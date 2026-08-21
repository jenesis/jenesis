package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildExecutorConcurrencyTest implements Serializable {

    private static CountDownLatch started;

    @TempDir
    private Path root, source;

    private BuildExecutor executor(int concurrency) throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                concurrency);
    }

    private static BuildStep counting(AtomicInteger running, AtomicInteger peak, int millis) {
        return (_, context, _) -> {
            int now = running.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(millis);
                Files.writeString(context.next().resolve("done"), Integer.toString(now));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } finally {
                running.decrementAndGet();
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        };
    }

    @Test
    public void limits_the_steps_running_at_once() throws IOException {
        Files.writeString(source.resolve("file"), "foo");
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        BuildExecutor buildExecutor = executor(2);
        buildExecutor.addSource("source", source);
        for (int index = 0; index < 6; index++) {
            buildExecutor.addStep("step" + index, counting(running, peak, 100), "source");
        }
        Map<String, ?> build = buildExecutor.execute();
        assertThat(build).containsKeys("step0", "step1", "step2", "step3", "step4", "step5");
        assertThat(peak).hasValueLessThanOrEqualTo(2);
        assertThat(peak).hasValueGreaterThan(0);
    }

    @Test
    public void limits_across_nested_modules() throws IOException {
        Files.writeString(source.resolve("file"), "foo");
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        BuildExecutor buildExecutor = executor(1);
        buildExecutor.addSource("source", source);
        for (int module = 0; module < 3; module++) {
            buildExecutor.addModule("module" + module, (nested, _) -> {
                for (int index = 0; index < 2; index++) {
                    nested.addStep("step" + index, counting(running, peak, 50), BuildExecutorModule.PREVIOUS + "source");
                }
            }, "source");
        }
        buildExecutor.execute();
        assertThat(peak).hasValue(1);
    }

    @Test
    public void runs_everything_at_once_without_a_limit() throws IOException {
        Files.writeString(source.resolve("file"), "foo");
        started = new CountDownLatch(4);
        BuildExecutor buildExecutor = executor(0);
        buildExecutor.addSource("source", source);
        for (int index = 0; index < 4; index++) {
            buildExecutor.addStep("step" + index, (_, _, _) -> {
                started.countDown();
                try {
                    if (!started.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Steps did not run concurrently");
                    }
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }, "source");
        }
        assertThat(buildExecutor.execute()).containsKeys("step0", "step1", "step2", "step3");
    }

    @Test
    public void rejects_a_negative_limit() {
        assertThatThrownBy(() -> executor(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void reads_the_limit_from_the_property_and_overrides_it_with_the_wither() {
        String previous = System.getProperty("jenesis.executor.concurrency");
        try {
            System.clearProperty("jenesis.executor.concurrency");
            assertThat(new BuildExecutor.Configuration().concurrency()).isEqualTo(0);
            System.setProperty("jenesis.executor.concurrency", "3");
            assertThat(new BuildExecutor.Configuration().concurrency()).isEqualTo(3);
            assertThat(new BuildExecutor.Configuration().concurrency(5).concurrency()).isEqualTo(5);
        } finally {
            if (previous == null) {
                System.clearProperty("jenesis.executor.concurrency");
            } else {
                System.setProperty("jenesis.executor.concurrency", previous);
            }
        }
    }
}
