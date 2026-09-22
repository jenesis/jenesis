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
}
