package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildStepHashFunctionEngineTest {

    private static final BuildStep STEP = (_, _, _) ->
            CompletableFuture.completedStage(new BuildStepResult(true));

    @Test
    public void a_different_engine_changes_a_step_hash() throws IOException {
        byte[] first = BuildStepHashFunction
                .ofSerializationDigest("SHA-256", "0.12.0".getBytes(StandardCharsets.UTF_8))
                .hash(STEP);
        byte[] second = BuildStepHashFunction
                .ofSerializationDigest("SHA-256", "0.13.0".getBytes(StandardCharsets.UTF_8))
                .hash(STEP);
        assertThat(first)
                .as("a step carries no version of its own, so the engine's identity is what tells"
                        + " a new tool not to consume the outputs the old one left behind")
                .isNotEqualTo(second);
    }

    @Test
    public void the_same_engine_keeps_a_step_hash() throws IOException {
        byte[] first = BuildStepHashFunction
                .ofSerializationDigest("SHA-256", "0.13.0".getBytes(StandardCharsets.UTF_8))
                .hash(STEP);
        byte[] second = BuildStepHashFunction
                .ofSerializationDigest("SHA-256", "0.13.0".getBytes(StandardCharsets.UTF_8))
                .hash(STEP);
        assertThat(first)
                .as("the salt is content, not a timestamp, so two machines on one engine share a cache")
                .isEqualTo(second);
    }

    @Test
    public void the_discovered_engine_identity_is_stable_and_not_empty() {
        assertThat(BuildStepHashFunction.Engine.identity())
                .as("an engine that cannot identify itself would key every build the same")
                .isNotEmpty()
                .isEqualTo(BuildStepHashFunction.Engine.identity());
    }
}
