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
import build.jenesis.project.InferredSourceGenerationModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredSourceGenerationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void wires_xjc_when_its_properties_file_is_present() throws IOException {
        Files.writeString(project.resolve("xjc.properties"), "package=demo.order\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("generated", generation(), "project");
        executor.execute("generated/xjc/tool/required");

        Path requiredOutput = root.resolve("generated").resolve("xjc").resolve("tool").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("xjc/runtime/maven/org.glassfish.jaxb/jaxb-xjc/RELEASE");
    }

    @Test
    public void skips_xjc_when_its_properties_file_is_absent() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("generated", generation(), "project");
        executor.execute();

        assertThat(root.resolve("generated").resolve("xjc"))
                .as("xjc is not wired when xjc.properties is absent from the configuration")
                .doesNotExist();
    }

    @Test
    public void binds_the_configured_folders_into_the_tool_folder() throws IOException {
        Files.writeString(project.resolve("xjc.properties"), "folders=contracts\n");
        Files.writeString(
                Files.createDirectories(project.resolve(BuildStep.SOURCES + "contracts/order")).resolve("order.xsd"),
                "<schema/>");
        Files.writeString(
                Files.createDirectories(project.resolve(BuildStep.SOURCES + "contracts")).resolve("order.txt"),
                "not a schema");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("generated", generation(), "project");
        executor.execute("generated/xjc/prepare");

        Path bound = root.resolve("generated").resolve("xjc").resolve("prepare").resolve("output").resolve("xjc");
        assertThat(bound.resolve("order").resolve("order.xsd"))
                .as("a schema is linked under the path it holds within the configured folder")
                .isNotEmptyFile();
        assertThat(bound.resolve("order.txt"))
                .as("only the file kinds the tool reads are linked into its folder")
                .doesNotExist();
    }

    @Test
    public void rejects_an_unknown_xjc_property() throws IOException {
        Files.writeString(project.resolve("xjc.properties"), "packages=demo.order\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("generated", generation(), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown xjc property: packages");
    }

    private InferredSourceGenerationModule generation() {
        return new InferredSourceGenerationModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of());
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
