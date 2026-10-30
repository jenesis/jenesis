package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorFileCache;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildExecutorPersistentTest {

    @TempDir
    private Path root, source, cached;

    @Test
    public void hands_a_step_the_persistent_folder_its_last_successful_run_left() throws IOException {
        assertThat(build("first", counting(0))).isEqualTo("0");
        assertThat(build("second", counting(0))).isEqualTo("1");
        assertThat(build("third", counting(0))).isEqualTo("2");
        assertThat(root.resolve("step").resolve("persistent").resolve("count")).content().isEqualTo("3");
    }

    @Test
    public void leaves_no_persistent_folder_behind_a_step_that_kept_nothing() throws IOException {
        Files.writeString(source.resolve("file"), "first");
        BuildExecutor executor = executor(BuildExecutorCache.nop());
        executor.addSource("source", source);
        executor.addStep("step", (_, context, _) -> {
            assertThat(context.persistent()).isEmptyDirectory();
            Files.writeString(context.next().resolve("file"), "written");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, "source");
        executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(root.resolve("step").resolve("output").resolve("file")).exists();
        assertThat(root.resolve("step").resolve("persistent")).doesNotExist();
    }

    @Test
    public void starts_from_an_empty_persistent_folder_after_a_run_that_failed() throws IOException {
        assertThat(build("first", counting(0))).isEqualTo("0");
        assertThatThrownBy(() -> build("fail", counting(0)))
                .hasStackTraceContaining("failed after counting");
        assertThat(build("third", counting(0)))
                .as("what a failed run left behind may be inconsistent, so it is not handed on")
                .isEqualTo("0");
    }

    @Test
    public void hands_a_step_that_persists_on_failure_the_persistent_folder_of_its_failed_run() throws IOException {
        assertThat(build("first", new Persisting(0))).isEqualTo("0");
        assertThatThrownBy(() -> build("fail", new Persisting(0)))
                .hasStackTraceContaining("failed after counting");
        assertThat(build("third", new Persisting(0)))
                .as("a step that persists on failure is handed what its failed run left")
                .isEqualTo("2");
    }

    @Test
    public void hands_a_step_that_persists_on_failure_the_folder_of_a_first_run_that_failed() throws IOException {
        assertThatThrownBy(() -> build("fail", new Persisting(0)))
                .hasStackTraceContaining("failed after counting");
        assertThat(build("second", new Persisting(0)))
                .as("no run succeeded before, so the failed run is the only one to remember")
                .isEqualTo("1");
    }

    @Test
    public void drops_the_folder_of_a_failed_run_of_a_step_whose_definition_changed() throws IOException {
        assertThatThrownBy(() -> build("fail", new Persisting(0)))
                .hasStackTraceContaining("failed after counting");
        assertThat(build("second", new Persisting(1)))
                .as("the folder describes a step that no longer exists")
                .isEqualTo("1");
    }

    @Test
    public void keeps_the_persistent_folder_of_a_run_that_reported_no_new_output() throws IOException {
        assertThat(build("first", counting(0))).isEqualTo("0");
        assertThat(build("keep", counting(0))).as("the output of the run before stays").isEqualTo("0");
        assertThat(build("third", counting(0))).isEqualTo("2");
    }

    @Test
    public void drops_the_persistent_folder_of_a_step_whose_definition_changed() throws IOException {
        assertThat(build("first", counting(0))).isEqualTo("0");
        assertThat(build("second", counting(1)))
                .as("the folder describes the output of the last run, which no longer counts")
                .isEqualTo("1");
    }

    @Test
    public void drops_the_persistent_folder_when_the_build_cache_supplies_the_output() throws IOException {
        BuildExecutorCache cache = new BuildExecutorFileCache(cached);
        assertThat(build("first", counting(0), cache)).isEqualTo("0");
        assertThat(build("second", counting(0), cache)).isEqualTo("1");
        assertThat(build("first", counting(0), cache)).as("the output of the first run, from the cache").isEqualTo("0");
        assertThat(build("fourth", counting(0), cache))
                .as("the folder of the second run does not describe the output the cache supplied")
                .isEqualTo("0");
    }

    @Test
    public void keeps_the_persistent_folder_out_of_what_a_dependent_step_is_handed() throws IOException {
        Files.writeString(source.resolve("file"), "first");
        BuildExecutor executor = executor(BuildExecutorCache.nop());
        executor.addSource("source", source);
        executor.addStep("step", counting(0), "source");
        executor.addStep("dependent", (_, context, arguments) -> {
            try (Stream<Path> files = Files.walk(arguments.get("step").folder())) {
                Files.writeString(context.next().resolve("files"), files
                        .filter(Files::isRegularFile)
                        .map(file -> file.getFileName().toString())
                        .sorted()
                        .collect(Collectors.joining(",")));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, "step");
        executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(root.resolve("dependent").resolve("output").resolve("files")).content().isEqualTo("file");
        assertThat(root.resolve("step").resolve("checksum").resolve("output.properties")).content().doesNotContain("count");
    }

    private record Persisting(int offset) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            return counting(offset).apply(executor, context, arguments);
        }

        @Override
        public boolean persistsOnFailure() {
            return true;
        }
    }

    private static BuildStep counting(int offset) {
        return (_, context, arguments) -> {
            Path count = context.persistent().resolve("count");
            int value = (Files.exists(count) ? Integer.parseInt(Files.readString(count)) : 0) + offset;
            Files.writeString(count, Integer.toString(value + 1));
            String content = Files.readString(arguments.get("source").folder().resolve("file"));
            if (content.equals("fail")) {
                throw new IllegalStateException("failed after counting");
            }
            if (content.equals("keep")) {
                return CompletableFuture.completedStage(new BuildStepResult(false));
            }
            Files.writeString(context.next().resolve("file"), Integer.toString(value));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        };
    }

    private String build(String content, BuildStep step) throws IOException {
        return build(content, step, BuildExecutorCache.nop());
    }

    private String build(String content, BuildStep step, BuildExecutorCache cache) throws IOException {
        Files.writeString(source.resolve("file"), content);
        BuildExecutor executor = executor(cache);
        executor.addSource("source", source);
        executor.addStep("step", step, "source");
        executor.execute(Runnable::run).toCompletableFuture().join();
        return Files.readString(root.resolve("step").resolve("output").resolve("file"));
    }

    private BuildExecutor executor(BuildExecutorCache cache) throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                cache,
                false,
                false,
                0);
    }
}
