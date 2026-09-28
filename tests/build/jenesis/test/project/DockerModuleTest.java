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
import build.jenesis.project.DockerModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DockerModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_a_module_name_or_a_maven_coordinate_in_the_docker_group() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("docker", new DockerModule(Map.of(), Map.of(), "example/base:1.0")
                .diff(List.of("com.example.app", "org.slf4j:slf4j-api", "org.slf4j:slf4j-simple:2.0.16")), "project");
        executor.execute("docker/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root.resolve("docker").resolve("required")
                .resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).containsExactlyInAnyOrder(
                "docker/runtime/module/com.example.app",
                "docker/runtime/maven/org.slf4j/slf4j-api/RELEASE",
                "docker/runtime/maven/org.slf4j/slf4j-simple/2.0.16");
    }

    @Test
    public void resolves_nothing_for_an_image_that_extends_no_other() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("docker", new DockerModule(Map.of(), Map.of(), "example/base:1.0"), "project");
        executor.execute("docker");

        assertThat(root.resolve("docker").resolve("required")).doesNotExist();
        assertThat(root.resolve("docker").resolve(DockerModule.IMAGE)).isDirectory();
    }

    @Test
    public void refuses_a_diff_entry_that_is_neither_a_module_nor_a_coordinate() {
        assertThatThrownBy(() -> new DockerModule(Map.of(), Map.of(), "example/base:1.0")
                .diff(List.of("maven/org.slf4j/slf4j-api")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maven/org.slf4j/slf4j-api")
                .hasMessageContaining("<groupId>:<artifactId>[:<version>]");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
