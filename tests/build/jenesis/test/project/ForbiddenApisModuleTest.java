package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.ForbiddenApisModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ForbiddenApisModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_forbiddenapis_maven_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("forbiddenapis",
                new ForbiddenApisModule(Map.of(), Map.of()).bundled(List.of("jdk-unsafe")),
                "project");
        executor.execute("forbiddenapis/required");

        Path requiredOutput = root.resolve("forbiddenapis").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("forbiddenapis/runtime/maven/de.thetaphi/forbiddenapis/RELEASE");
    }

    @Test
    public void refuses_to_check_against_no_signatures() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("forbiddenapis", new ForbiddenApisModule(Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .hasStackTraceContaining("forbiddenapis is given no signatures to check against");
    }

    @Test
    public void refuses_a_signatures_file_that_is_no_path_below_the_folders_it_reads() {
        assertThatThrownBy(() -> new ForbiddenApisModule(Map.of(), Map.of()).signatures(List.of(Path.of("../outside.txt"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("../outside.txt is no path below the folders the check reads");
        assertThatThrownBy(() -> new ForbiddenApisModule(Map.of(), Map.of()).signatures(List.of(project.resolve("forbidden.txt"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is no path below the folders the check reads");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }
}
