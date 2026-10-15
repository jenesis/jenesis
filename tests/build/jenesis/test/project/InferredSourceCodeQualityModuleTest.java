package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.InferredSourceCodeQualityModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredSourceCodeQualityModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void runs_the_checks_it_is_handed_over_the_sources() throws IOException {
        Files.writeString(project.resolve("Sample.java"), "class Sample {}");
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(), Map.of(), Map.of()).custom("lines", (_, context, arguments) -> {
            Files.writeString(context.next().resolve("checked.txt"), String.valueOf(arguments.size()));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }), "project");

        assertThat(executor.execute().get("quality/custom/lines").resolve("checked.txt")).hasContent("1");
    }

    @Test
    public void refuses_a_check_whose_name_is_taken() {
        InferredSourceCodeQualityModule module = new InferredSourceCodeQualityModule(new LinkedHashSet<>(), Map.of(), Map.of())
                .custom("lines", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));

        assertThatThrownBy(() -> module.custom("lines", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is added already");
    }
}
