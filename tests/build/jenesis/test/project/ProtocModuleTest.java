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
import build.jenesis.project.ProtocModule;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_platform_classified_protoc_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("protoc", new ProtocModule(Map.of(), Map.of()).classifier("linux-x86_64"), "project");
        executor.execute("protoc/required");

        Path requiredOutput = root.resolve("protoc").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("protoc/runtime/maven/com.google.protobuf/protoc/exe/linux-x86_64/RELEASE");
    }

    @Test
    public void derives_a_classifier_from_the_host_the_build_runs_on() {
        assertThat(ProtocModule.classifier())
                .as("protoc ships one native executable per operating system and chipset")
                .matches("(linux|osx|windows)-(x86_64|x86_32|aarch_64)");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
