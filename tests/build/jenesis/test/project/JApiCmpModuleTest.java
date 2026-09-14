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
import build.jenesis.project.JApiCmpModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JApiCmpModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_japicmp_coordinate_and_the_declared_baseline() throws IOException {
        execute(configured("baseline", "com.example/library/1.2.3"));

        assertThat(requires().stringPropertyNames()).containsExactlyInAnyOrder(
                "japicmp/runtime/maven/com.github.siom79.japicmp/japicmp/RELEASE",
                "japicmp/baseline/maven/com.example/library/1.2.3");
    }

    @Test
    public void baseline_resolves_without_its_own_dependencies() throws IOException {
        execute(configured("baseline", "com.example/library/1.2.3"));

        assertThat(exclusions())
                .as("only the baseline artifact itself is compared, so its closure is never resolved")
                .containsEntry("japicmp/baseline/maven/com.example/library/1.2.3", "*/*");
    }

    @Test
    public void a_baseline_without_a_version_floats_the_latest_release() throws IOException {
        execute(configured("baseline", "com.example/library"));

        assertThat(requires().stringPropertyNames())
                .contains("japicmp/baseline/maven/com.example/library/RELEASE");
    }

    @Test
    public void a_baseline_names_the_repository_it_is_served_from() throws IOException {
        execute(configured("baseline", "modular/com.example/library/1.2.3"));

        assertThat(requires().stringPropertyNames())
                .contains("japicmp/baseline/modular/com.example/library/1.2.3");
    }

    @Test
    public void an_undeclared_baseline_is_the_last_release_of_the_modules_own_coordinate() throws IOException {
        SequencedProperties metadata = new SequencedProperties();
        metadata.setProperty("project", "com.example");
        metadata.setProperty("artifact", "library");
        metadata.setProperty("version", "1.3.0-SNAPSHOT");
        metadata.store(project.resolve(BuildStep.METADATA));

        execute(new SequencedProperties());

        assertThat(requires().stringPropertyNames())
                .contains("japicmp/baseline/maven/com.example/library/RELEASE");
    }

    @Test
    public void a_module_without_a_maven_coordinate_must_declare_a_baseline() {
        assertThatThrownBy(() -> execute(new SequencedProperties()))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("baseline=<groupId>/<artifactId>");
    }

    @Test
    public void a_baseline_that_is_not_a_coordinate_names_the_forms_that_are() {
        assertThatThrownBy(() -> execute(configured("baseline", "library")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("<groupId>/<artifactId>/<version>");
    }

    private static SequencedProperties configured(String key, String value) {
        SequencedProperties config = new SequencedProperties();
        config.setProperty(key, value);
        return config;
    }

    private void execute(SequencedProperties config) throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        executor.addSource("project", project);
        executor.addModule("japicmp", new JApiCmpModule(Map.of(), Map.of()).config(config), "project");
        executor.execute("japicmp/required");
    }

    private SequencedProperties requires() throws IOException {
        return SequencedProperties.ofFiles(output().resolve(BuildStep.REQUIRES));
    }

    private SequencedProperties exclusions() throws IOException {
        return SequencedProperties.ofFiles(output().resolve(BuildStep.EXCLUSIONS));
    }

    private Path output() {
        return root.resolve("japicmp").resolve("required").resolve("output");
    }
}
