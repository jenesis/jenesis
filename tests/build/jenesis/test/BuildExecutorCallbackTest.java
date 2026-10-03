package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutorCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildExecutorCallbackTest {

    @Test
    public void can_print_executed() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(true, null);
        assertThat(printed).hasSize(1);
        assertThat(printed.getFirst())
                .matches(Pattern.quote(BuildExecutorCallback.GREEN + "[EXECUTED] " + BuildExecutorCallback.RESET)
                        + " foo "
                        + Pattern.quote(BuildExecutorCallback.CYAN)
                        + "in [0-9]+.[0-9]{2} seconds"
                        + Pattern.quote(BuildExecutorCallback.RESET));
    }

    @Test
    public void can_print_skipped() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(false, null);
        assertThat(printed).containsExactly(
                BuildExecutorCallback.BLUE + "[SKIPPED]  " + BuildExecutorCallback.RESET + " foo");
    }

    @Test
    public void can_print_failed() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(null, new RuntimeException("message"));
        assertThat(printed)
                .as("a line is handed to the consumer as it stands, so what ends it is the caller's business")
                .containsExactly(BuildExecutorCallback.RED + "[FAILED]   " + BuildExecutorCallback.RESET
                        + " foo: message");
    }

    @Test
    public void prints_why_a_step_runs_only_when_asked_to() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, false, false, false, null).outdated("foo", List.of("a", "b"));
        assertThat(printed).isEmpty();
        BuildExecutorCallback.printing(printed::add, false, false, true, null).outdated("foo", List.of("a", "b"));
        assertThat(printed).containsExactly(BuildExecutorCallback.YELLOW + "[CHANGED]  " + BuildExecutorCallback.RESET
                + " foo: a; b");
    }

    @Test
    public void prints_each_pending_step_and_how_many_would_run() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback callback = BuildExecutorCallback.printing(printed::add, false, false, false, Path.of("target"));
        BiConsumer<Boolean, Throwable> build = callback.step(null, new LinkedHashSet<>());
        callback.step("foo", new LinkedHashSet<>()).accept(false, null);
        callback.pending("bar", List.of("it never ran"), true);
        callback.pending("qux", List.of("it may run after bar"), false);
        build.accept(null, null);
        assertThat(printed).contains(
                BuildExecutorCallback.YELLOW + "[PENDING]  " + BuildExecutorCallback.RESET + " bar: it never ran",
                BuildExecutorCallback.YELLOW + "[PENDING]  " + BuildExecutorCallback.RESET
                        + " 1 step runs, 1 more may run after it, and 1 is up to date");
    }
}
