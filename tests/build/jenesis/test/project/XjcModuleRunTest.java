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
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.XjcModule;

import static org.assertj.core.api.Assertions.assertThat;

public class XjcModuleRunTest {

    private static final String VERSION = "4.0.9";

    private static final String SCHEMA = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:tns="https://jenesis.build/test/order"
                       targetNamespace="https://jenesis.build/test/order"
                       elementFormDefault="qualified">
                <xs:element name="order" type="tns:Order"/>
                <xs:complexType name="Order">
                    <xs:attribute name="id" type="xs:string" use="required"/>
                </xs:complexType>
            </xs:schema>
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("xjc/maven/org.glassfish.jaxb/jaxb-xjc", VERSION);
        versions.store(project.resolve(BuildStep.VERSIONS));
    }

    @Test
    public void generates_java_sources_from_a_schema_among_the_inputs() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(XjcModule.FOLDER)).resolve("order.xsd"),
                SCHEMA);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("xjc", newModule().packageName("test.order"), "project");
        executor.execute();

        Path generated = root.resolve("xjc").resolve("generate").resolve("output").resolve("sources").resolve("test").resolve("order");
        assertThat(generated.resolve("Order.java"))
                .as("the schema's complex type reaches javac as a generated source file")
                .isNotEmptyFile();
        assertThat(generated.resolve("Order.java")).content().contains("package test.order;");
    }

    @Test
    public void generates_the_same_bytes_on_every_run() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(XjcModule.FOLDER)).resolve("order.xsd"),
                SCHEMA);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("xjc", newModule().packageName("test.order"), "project");
        executor.execute();

        Path generated = root.resolve("xjc").resolve("generate").resolve("output")
                .resolve("sources").resolve("test").resolve("order").resolve("Order.java");
        assertThat(generated).content()
                .as("xjc stamps the wall clock into a file header unless told not to, "
                        + "which would give the step a different output on every run")
                .doesNotContain("Generated on:");
    }

    @Test
    public void ignores_a_schema_outside_the_folder_the_step_reads() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(BuildStep.SOURCES + "schema")).resolve("order.xsd"),
                SCHEMA);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("xjc", newModule().packageName("test.order"), "project");
        executor.execute();

        assertThat(root.resolve("xjc").resolve("generate").resolve("output").resolve("sources"))
                .as("the step reads its own folder and never searches the module it belongs to")
                .doesNotExist();
    }

    @Test
    public void compiles_a_schema_below_the_folder_the_step_reads() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(XjcModule.FOLDER + "order")).resolve("order.xsd"),
                SCHEMA);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("xjc", newModule().packageName("test.order"), "project");
        executor.execute();

        assertThat(root.resolve("xjc").resolve("generate").resolve("output")
                .resolve("sources").resolve("test").resolve("order").resolve("Order.java"))
                .as("a schema keeps the path it holds within the folder, so imports resolve as they are written")
                .isNotEmptyFile();
    }

    @Test
    public void generates_nothing_when_no_schema_is_among_the_inputs() throws IOException {
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("xjc", newModule(), "project");
        executor.execute();

        assertThat(root.resolve("xjc").resolve("generate").resolve("output").resolve("sources"))
                .as("a shared xjc.properties activates the module for every project module, "
                        + "so one without a schema contributes no sources rather than failing")
                .doesNotExist();
    }

    private XjcModule newModule() {
        return new XjcModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
