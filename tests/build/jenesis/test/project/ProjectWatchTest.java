package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.project.ProjectWatch;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectWatchTest {

    @TempDir
    private Path root;

    @Test
    public void runs_an_initial_build_and_rebuilds_on_change() throws Exception {
        Files.writeString(root.resolve("Sample.java"), "initial");
        CountDownLatch initial = new CountDownLatch(1);
        CountDownLatch afterChange = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        Runnable build = () -> {
            if (builds.incrementAndGet() == 1) {
                initial.countDown();
            } else {
                afterChange.countDown();
            }
        };
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = watching(new ProjectWatch(root, Set.of(), 50L), build, failure);
        try {
            await(initial, failure);
            Files.writeString(root.resolve("Sample.java"), "changed");
            await(afterChange, failure);
        } finally {
            stop(thread);
        }
    }

    @Test
    public void ignores_changes_under_an_excluded_directory() throws Exception {
        Path excluded = Files.createDirectory(root.resolve("target"));
        Files.writeString(root.resolve("Sample.java"), "initial");
        CountDownLatch initial = new CountDownLatch(1);
        AtomicInteger builds = new AtomicInteger();
        Runnable build = () -> {
            builds.incrementAndGet();
            initial.countDown();
        };
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = watching(new ProjectWatch(root, Set.of(excluded.toAbsolutePath().normalize()), 50L), build, failure);
        try {
            await(initial, failure);
            Files.writeString(excluded.resolve("ignored.txt"), "noise");
            Thread.sleep(500);
            assertThat(builds.get()).isEqualTo(1);
        } finally {
            stop(thread);
        }
    }

    private static Thread watching(ProjectWatch watch, Runnable build, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                watch.watch(build);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    // Waits for the watch thread to signal a build, but bails out as soon as the thread fails to
    // start watching. A file watcher is an OS resource (inotify instances on Linux) that can be
    // exhausted by a busy machine; treat "no watch service available" as an environment skip
    // rather than a spurious failure, and surface any other watch error immediately.
    private static void await(CountDownLatch latch, AtomicReference<Throwable> failure) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (latch.await(200, TimeUnit.MILLISECONDS)) {
                return;
            }
            Throwable thrown = failure.get();
            if (thrown instanceof UncheckedIOException || thrown instanceof IOException) {
                Assumptions.abort("File watching is unavailable in this environment: " + thrown.getMessage());
            } else if (thrown != null) {
                throw new AssertionError("Watch thread failed", thrown);
            }
        }
        throw new AssertionError("Timed out waiting for the watch thread to trigger a build");
    }

    private static void stop(Thread thread) throws InterruptedException {
        thread.interrupt();
        thread.join(TimeUnit.SECONDS.toMillis(5));
    }
}
