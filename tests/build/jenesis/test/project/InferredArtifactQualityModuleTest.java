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
import build.jenesis.project.InferredArtifactQualityModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredArtifactQualityModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void wires_japicmp_when_its_properties_file_is_present() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact(), "project");
        executor.execute("artifact/japicmp/required");

        Path requiredOutput = root.resolve("artifact").resolve("japicmp").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).containsExactlyInAnyOrder(
                "japicmp/runtime/maven/com.github.siom79.japicmp/japicmp/RELEASE",
                "japicmp/baseline/maven/com.example/library/RELEASE");
    }

    @Test
    public void wires_revapi_when_its_properties_file_is_present() throws IOException {
        Files.writeString(project.resolve("revapi.properties"), "baseline=com.example/library\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact(), "project");
        executor.execute("artifact/revapi/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root.resolve("artifact").resolve("revapi")
                .resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).contains("revapi/baseline/maven/com.example/library/RELEASE");
    }

    @Test
    public void compares_no_api_with_revapi_of_a_test_module_or_when_switched_off() throws IOException {
        Files.writeString(project.resolve("revapi.properties"), "baseline=com.example/library\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact().revapi(null), "project");
        executor.execute();
        assertThat(root.resolve("artifact").resolve("revapi")).doesNotExist();

        Files.writeString(project.resolve(BuildStep.MODULE), "test=main_artifact\n");
        BuildExecutor tests = newExecutor();
        tests.addSource("project", project);
        tests.addModule("tests", artifact(), "project");
        tests.execute();
        assertThat(root.resolve("tests").resolve("revapi")).doesNotExist();
    }

    @Test
    public void skips_japicmp_when_no_properties_file_is_present() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact(), "project");
        executor.execute();

        assertThat(root.resolve("artifact").resolve("japicmp"))
                .as("japicmp is not wired without a japicmp.properties")
                .doesNotExist();
    }

    @Test
    public void compares_no_api_of_a_module_its_description_declares_a_test_module() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library");
        Files.writeString(project.resolve(BuildStep.MODULE), "test=main_artifact\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact(), "project");
        executor.execute();

        assertThat(root.resolve("artifact").resolve("japicmp"))
                .as("the tests of a module have no release of their own to be compatible with")
                .doesNotExist();
    }

    @Test
    public void the_japicmp_override_switches_off_japicmp() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact().japicmp(null), "project");
        executor.execute();

        assertThat(root.resolve("artifact").resolve("japicmp"))
                .as("japicmp is not wired once its configurator is dropped, even with a properties file present")
                .doesNotExist();
    }

    @Test
    public void an_unknown_property_names_the_ones_that_are_known() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "breakBuild=true");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact(), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown japicmp property: breakBuild")
                .hasMessageContaining(project.resolve("japicmp.properties").toString())
                .hasMessageContaining("error-on-binary-incompatibility");
    }

    @Test
    public void an_unknown_format_or_access_names_the_file_and_the_ones_that_exist() throws IOException {
        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library\nformat=xml,pdf\n");
        assertThatThrownBy(() -> wired("artifact/japicmp/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("japicmp.properties names the format pdf")
                .hasMessageContaining("[xml, html]");

        Files.writeString(project.resolve("japicmp.properties"), "baseline=com.example/library\naccess=internal\n");
        assertThatThrownBy(() -> wired("artifact/japicmp/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("japicmp.properties names the access internal")
                .hasMessageContaining("[public, protected, package, private]");
    }

    @Test
    public void reads_the_revapi_configuration_and_the_json_of_revapi_s_own_beside_it() throws IOException {
        Files.writeString(project.resolve("revapi.properties"), "baseline=com.example/library/1.0.0\n"
                + "severity=breaking\nconfiguration=revapi.json\n");
        Files.writeString(project.resolve("revapi.json"),
                "[{\"extension\": \"revapi.differences\", \"configuration\": {}}]");

        wired("artifact/revapi/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root.resolve("artifact").resolve("revapi")
                .resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).contains("revapi/baseline/maven/com.example/library/1.0.0");
    }

    @Test
    public void refuses_an_unknown_revapi_key_and_an_unknown_severity() throws IOException {
        Files.writeString(project.resolve("revapi.properties"), "failSeverity=breaking\n");
        assertThatThrownBy(() -> wired("artifact/revapi/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("names failSeverity")
                .hasMessageContaining("severity=<equivalent|non-breaking|potentially-breaking|breaking>");

        Files.writeString(project.resolve("revapi.properties"), "severity=fatal\n");
        assertThatThrownBy(() -> wired("artifact/revapi/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("names the severity fatal")
                .hasMessageContaining("potentially-breaking");
    }

    @Test
    public void refuses_a_revapi_configuration_that_is_missing_outside_the_folder_or_no_json_array() throws IOException {
        Files.writeString(project.resolve("revapi.properties"), "configuration=revapi.json\n");
        assertThatThrownBy(() -> wired("artifact/revapi/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("does not exist beside it");

        Files.writeString(project.resolve("revapi.properties"), "configuration=../revapi.json\n");
        assertThatThrownBy(() -> wired("artifact/revapi/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("not beside or below it");

        Files.writeString(project.resolve("revapi.properties"), "configuration=revapi.json\n");
        Files.writeString(project.resolve("revapi.json"), "{\"revapi\": {}}");
        assertThatThrownBy(() -> wired("artifact/revapi/required"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining(project.resolve("revapi.json").toString())
                .hasMessageContaining("is no JSON array");
    }

    private void wired(String selector) throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("artifact", artifact(), "project");
        executor.execute(selector);
    }

    private InferredArtifactQualityModule artifact() {
        return new InferredArtifactQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of());
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
