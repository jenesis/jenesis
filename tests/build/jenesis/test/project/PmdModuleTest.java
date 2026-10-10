package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.PmdModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PmdModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_pmd_maven_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("pmd", new PmdModule(Map.of(), Map.of()), "project");
        executor.execute("pmd/required");

        Path requiredOutput = root.resolve("pmd").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("pmd/runtime/maven/net.sourceforge.pmd/pmd-dist/RELEASE");
    }

    @Test
    public void refuses_a_rule_priority_that_pmd_does_not_know() {
        assertThatThrownBy(() -> PmdModule.ofEnvironment(new Environment(Map.of("source.pmd.priority", "6")), Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.source.pmd.priority")
                .hasMessageContaining("from 1, the highest, to 5, the lowest");
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
