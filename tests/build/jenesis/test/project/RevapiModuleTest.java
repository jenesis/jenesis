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
import build.jenesis.project.RevapiModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RevapiModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_the_revapi_extensions_and_the_baseline_it_names() throws IOException {
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
        executor.addModule("revapi", new RevapiModule(Map.of(), Map.of()).baseline("org.example/library/1.0.0"), "project");
        executor.execute("revapi/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root.resolve("revapi").resolve("required")
                .resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).containsExactlyInAnyOrder(
                "revapi/runtime/maven/org.revapi/revapi-java/RELEASE",
                "revapi/runtime/maven/org.revapi/revapi-basic-features/RELEASE",
                "revapi/runtime/maven/org.revapi/revapi-reporter-json/RELEASE",
                "revapi/baseline/maven/org.example/library/1.0.0");
    }

    @Test
    public void reads_its_configuration_and_the_json_of_revapi_s_own_beside_it() throws IOException {
        Path file = configuration("severity=breaking\nconfiguration=revapi.json\n");
        Files.writeString(project.resolve("revapi.json"), "[{\"extension\": \"revapi.differences\", \"configuration\": {}}]");

        new RevapiModule(Map.of(), Map.of()).configuration(file, SequencedProperties.ofFiles(file));
    }

    @Test
    public void refuses_an_unknown_key_and_an_unknown_severity() throws IOException {
        Path unknown = configuration("failSeverity=breaking\n");
        assertThatThrownBy(() -> new RevapiModule(Map.of(), Map.of()).configuration(unknown, SequencedProperties.ofFiles(unknown)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names failSeverity");

        Path severity = configuration("severity=fatal\n");
        assertThatThrownBy(() -> new RevapiModule(Map.of(), Map.of()).configuration(severity, SequencedProperties.ofFiles(severity)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names the severity fatal")
                .hasMessageContaining("potentially-breaking");
    }

    @Test
    public void refuses_a_configuration_that_is_missing_outside_the_folder_or_no_json_array() throws IOException {
        Path missing = configuration("configuration=revapi.json\n");
        assertThatThrownBy(() -> new RevapiModule(Map.of(), Map.of()).configuration(missing, SequencedProperties.ofFiles(missing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist beside it");

        Path outside = configuration("configuration=../revapi.json\n");
        assertThatThrownBy(() -> new RevapiModule(Map.of(), Map.of()).configuration(outside, SequencedProperties.ofFiles(outside)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not beside or below it");

        Path object = configuration("configuration=revapi.json\n");
        Files.writeString(project.resolve("revapi.json"), "{\"revapi\": {}}");
        assertThatThrownBy(() -> new RevapiModule(Map.of(), Map.of()).configuration(object, SequencedProperties.ofFiles(object)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is no JSON array");
    }

    private Path configuration(String content) throws IOException {
        return Files.writeString(project.resolve(RevapiModule.CONFIGURATION), content);
    }
}
