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
import build.jenesis.project.InferredApiCompatibilityModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredApiCompatibilityModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void wires_japicmp_when_its_properties_file_is_present() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("compatibility", compatibility(), "project");
        executor.execute("compatibility/japicmp/required");

        Path requiredOutput = root.resolve("compatibility").resolve("japicmp").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).containsExactlyInAnyOrder(
                "japicmp/runtime/maven/com.github.siom79.japicmp/japicmp/RELEASE",
                "japicmp/baseline/maven/com.example/library/RELEASE");
    }

    @Test
    public void skips_japicmp_when_no_properties_file_is_present() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("compatibility", compatibility(), "project");
        executor.execute();

        assertThat(root.resolve("compatibility").resolve("japicmp"))
                .as("japicmp is not wired without a japicmp.properties")
                .doesNotExist();
    }

    @Test
    public void the_japicmp_override_switches_off_japicmp() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("compatibility", compatibility().japicmp(null), "project");
        executor.execute();

        assertThat(root.resolve("compatibility").resolve("japicmp"))
                .as("japicmp is not wired once its configurator is dropped, even with a properties file present")
                .doesNotExist();
    }

    @Test
    public void an_unknown_property_names_the_ones_that_are_known() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "breakBuild=true");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("compatibility", compatibility(), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown japicmp property: breakBuild")
                .hasMessageContaining("error-on-binary-incompatibility");
    }

    private InferredApiCompatibilityModule compatibility() {
        return new InferredApiCompatibilityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of());
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
