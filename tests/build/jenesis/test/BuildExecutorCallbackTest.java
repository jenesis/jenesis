package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorException;
import build.jenesis.BuildStepResult;
import build.jenesis.Json;
import build.jenesis.Palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildExecutorCallbackTest {

    @TempDir
    private Path target;

    @Test
    public void can_print_executed() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, Palette.ANSI, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(true, null);
        assertThat(printed).hasSize(1);
        assertThat(printed.getFirst())
                .matches(Pattern.quote(Palette.ANSI.status() + "[EXECUTED] " + Palette.ANSI.reset())
                        + " foo "
                        + Pattern.quote(Palette.ANSI.detail())
                        + "in [0-9]+.[0-9]{2} seconds"
                        + Pattern.quote(Palette.ANSI.reset()));
    }

    @Test
    public void prints_plain_text_without_colors() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, Palette.NONE, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(false, null);
        assertThat(printed).containsExactly("[SKIPPED]   foo");
    }

    @Test
    public void can_print_skipped() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, Palette.ANSI, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(false, null);
        assertThat(printed).containsExactly(
                Palette.ANSI.skipped() + "[SKIPPED]  " + Palette.ANSI.reset() + " foo");
    }

    @Test
    public void can_print_failed() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, Palette.ANSI, false, false, null)
                .step("foo", new LinkedHashSet<>(Set.of("bar")))
                .accept(null, new RuntimeException("message"));
        assertThat(printed)
                .as("a line is handed to the consumer as it stands, so what ends it is the caller's business")
                .containsExactly(Palette.ANSI.failure() + "[FAILED]   " + Palette.ANSI.reset()
                        + " foo: message");
    }

    @Test
    public void announces_the_events_file_where_the_build_starts() {
        List<String> printed = new ArrayList<>();
        BuildExecutorCallback.printing(printed::add, Palette.ANSI, false, false, target, true).step(null, new LinkedHashSet<>());
        assertThat(printed)
                .as("a reader of the progress lines learns from the second one where the outcome is recorded")
                .hasSize(2)
                .last()
                .asString()
                .contains("[EVENTS]", target.resolve(BuildExecutor.EVENTS).toString());
    }

    @Test
    public void writes_each_outcome_as_one_json_object_per_line() throws IOException {
        BuildExecutorCallback callback = BuildExecutorCallback.events(target);
        BiConsumer<Boolean, Throwable> build = callback.step(null, new LinkedHashSet<>());
        callback.step("foo", new LinkedHashSet<>()).accept(true, null);
        callback.step("bar", new LinkedHashSet<>()).accept(false, null);
        callback.loaded("baz", 1_500_000_000L);
        callback.step("baz", new LinkedHashSet<>()).accept(true, null);
        callback.module("qux").accept(null);
        callback.step("qux/quux", new LinkedHashSet<>())
                .accept(null, new BuildExecutorException("qux/quux", new IllegalStateException("broke \"here\"")));
        build.accept(null, new IllegalStateException("broke"));
        List<Map<String, Object>> events = events();
        assertThat(events).hasSize(8);
        assertThat(events)
                .as("status leads every line, so a reader scanning the file sees what happened before where")
                .allSatisfy(event -> assertThat(event.keySet()).first().isEqualTo("status"));
        assertThat(events.getFirst()).containsEntry("status", "started")
                .containsEntry("target", target.toAbsolutePath().normalize().toString());
        assertThat(events.get(1)).containsEntry("step", "foo")
                .containsEntry("status", "executed")
                .containsEntry("folder", target.toAbsolutePath().normalize().resolve("foo").toString())
                .containsKey("seconds");
        assertThat(events.get(2)).containsEntry("step", "bar").containsEntry("status", "skipped");
        assertThat(events.get(3)).containsEntry("step", "baz")
                .containsEntry("status", "loaded")
                .containsEntry("seconds", 1.5);
        assertThat(events.get(5)).containsEntry("module", "qux").containsEntry("status", "resolved");
        assertThat(events.get(6))
                .as("a failure names the exception that caused it rather than the executor's wrapper")
                .containsEntry("step", "qux/quux")
                .containsEntry("status", "failed")
                .containsEntry("error", IllegalStateException.class.getName())
                .containsEntry("message", "broke \"here\"");
        assertThat(events.getLast()).containsEntry("status", "failed")
                .containsEntry("executed", 2.0)
                .containsEntry("skipped", 1.0)
                .containsEntry("failed", 1.0)
                .containsEntry("message", "broke");
    }

    @Test
    public void replaces_the_events_of_the_previous_build() throws IOException {
        BuildExecutorCallback callback = BuildExecutorCallback.events(target);
        BiConsumer<Boolean, Throwable> first = callback.step(null, new LinkedHashSet<>());
        callback.step("foo", new LinkedHashSet<>()).accept(true, null);
        first.accept(null, null);
        BiConsumer<Boolean, Throwable> second = callback.step(null, new LinkedHashSet<>());
        callback.step("bar", new LinkedHashSet<>()).accept(false, null);
        second.accept(null, null);
        assertThat(events()).extracting(event -> event.get("step")).containsExactly(null, "bar", null);
        assertThat(events().getLast()).containsEntry("status", "completed").containsEntry("skipped", 1.0);
    }

    @Test
    public void a_build_records_its_steps_in_the_target_folder() throws IOException {
        BuildExecutor executor = new BuildExecutor.Configuration().progress(false).of(target);
        executor.addStep("foo", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
        executor.addStep("bar", (_, _, _) -> {
            throw new IllegalArgumentException("bar is broken");
        }, "foo");
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .hasRootCauseMessage("bar is broken");
        List<Map<String, Object>> events = events();
        assertThat(events).extracting(event -> event.get("step")).containsExactly(null, "foo", "bar", null);
        assertThat(events.get(2)).containsEntry("status", "failed")
                .containsEntry("error", IllegalArgumentException.class.getName());
        assertThat(events.getLast()).containsEntry("status", "failed")
                .containsEntry("executed", 1.0)
                .containsEntry("failed", 1.0);
    }

    @Test
    public void a_build_records_a_selector_it_refuses() throws IOException {
        BuildExecutor executor = new BuildExecutor.Configuration().progress(false).of(target);
        executor.addStep("foo", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
        assertThatThrownBy(() -> executor.execute(Runnable::run, "bar")).hasMessageContaining("Unknown selector: bar");
        assertThat(events().getLast())
                .as("a selector refused before any step ran still ends the file, so a reader is not left waiting")
                .containsEntry("status", "failed")
                .containsEntry("error", IllegalArgumentException.class.getName());
    }

    @Test
    public void a_build_records_no_events_when_switched_off() throws IOException {
        BuildExecutor executor = new BuildExecutor.Configuration().progress(false).events(false).of(target);
        executor.addStep("foo", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
        executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(target.resolve(BuildExecutor.EVENTS)).doesNotExist();
    }

    private List<Map<String, Object>> events() throws IOException {
        return Files.readAllLines(target.resolve(BuildExecutor.EVENTS)).stream()
                .map(line -> (Map<String, Object>) Json.parse(line))
                .toList();
    }
}
