package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;
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

    private static final String PINS = """
            xjc/maven/com.sun.istack/istack-commons-runtime 4.1.2 SHA-256/7fd6792361f4dd00f8c56af4a20cecc0066deea4a8f3dec38348af23fc2296ee
            xjc/maven/com.sun.istack/istack-commons-tools 4.1.2 SHA-256/85b4fe7ad6fdfc64a586133f039d3de7b51db2c8111a1aa98a267891e27f386f
            xjc/maven/com.sun.xml.bind.external/relaxng-datatype 4.0.9 SHA-256/89933721c2dc654049ebea84a33e2f3d36e918e5871ccb6f08df1960779c5cb9
            xjc/maven/com.sun.xml.bind.external/rngom 4.0.9 SHA-256/b3daa8902535f33ff90d66c2c327b95b3827f0b36a31f9a2c1094d7b82263e1b
            xjc/maven/com.sun.xml.dtd-parser/dtd-parser 1.5.1 SHA-256/fd07c1b528e8649e956bd4c759a63badb79e8076260b5c23bdf37f2b11d06711
            xjc/maven/jakarta.activation/jakarta.activation-api 2.1.4 SHA-256/c9db52100ce6c8aac95cc39075f95720d2e561b11f8051b81c121ad4effd7004
            xjc/maven/jakarta.xml.bind/jakarta.xml.bind-api 4.0.5 SHA-256/5e489b6c874c4119e003ff1403db523ee3a8959ec499f3de29e77245efccf216
            xjc/maven/org.eclipse.angus/angus-activation 2.0.3 SHA-256/a6bd35c538cf90fff941ad6258c40c08fca0b5c9c3f536c657114f27ce0527a7
            xjc/maven/org.glassfish.jaxb/codemodel 4.0.9 SHA-256/12c9ba587b85af187a71b25c531c9929bd1a53da334d102e485468a493a1f1d7
            xjc/maven/org.glassfish.jaxb/jaxb-core 4.0.9 SHA-256/6b014df25d22c8446cc2df0a378c5b2137747f197a731d99eb24c756b26f7e8d
            xjc/maven/org.glassfish.jaxb/jaxb-xjc 4.0.9 SHA-256/5556b3d06a4af1bbcd9d68ef80dbaa04bdc686c246c89b3d98db4acf7f8faf90
            xjc/maven/org.glassfish.jaxb/txw2 4.0.9 SHA-256/d6343e5945d87266ca4e8974a0a2a876e1bd147a00fb65ed3f2e1a3a00f3a82a
            xjc/maven/org.glassfish.jaxb/xsom 4.0.9 SHA-256/be584192033acb7424e0edb283e18f83ae681a46c9e3218fb4377f4df870ed07
            """;


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
        versions.load(new StringReader(PINS));
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
        return new XjcModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()))
                .pinning(Pinning.STRICT);
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
