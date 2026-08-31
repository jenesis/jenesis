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
import build.jenesis.project.WsImportModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WsImportModuleRunTest {

    private static final String VERSION = "4.0.5";

    private static final String DESCRIPTION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions name="Greeter"
                         targetNamespace="https://jenesis.build/test/greeter"
                         xmlns:tns="https://jenesis.build/test/greeter"
                         xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                         xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                         xmlns="http://schemas.xmlsoap.org/wsdl/">
                <types>
                    <xsd:schema targetNamespace="https://jenesis.build/test/greeter">
                        <xsd:element name="greet" type="xsd:string"/>
                        <xsd:element name="greetResponse" type="xsd:string"/>
                    </xsd:schema>
                </types>
                <message name="greetRequest"><part name="parameters" element="tns:greet"/></message>
                <message name="greetResponse"><part name="parameters" element="tns:greetResponse"/></message>
                <portType name="GreeterPort">
                    <operation name="greet">
                        <input message="tns:greetRequest"/>
                        <output message="tns:greetResponse"/>
                    </operation>
                </portType>
                <binding name="GreeterBinding" type="tns:GreeterPort">
                    <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                    <operation name="greet">
                        <soap:operation soapAction="greet"/>
                        <input><soap:body use="literal"/></input>
                        <output><soap:body use="literal"/></output>
                    </operation>
                </binding>
                <service name="GreeterService">
                    <port name="GreeterPort" binding="tns:GreeterBinding">
                        <soap:address location="https://example.invalid/greeter"/>
                    </port>
                </service>
            </definitions>
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("wsimport/maven/com.sun.xml.ws/jaxws-tools", VERSION);
        versions.store(project.resolve(BuildStep.VERSIONS));
    }

    @Test
    public void generates_a_service_client_from_a_description_among_the_inputs() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(WsImportModule.FOLDER)).resolve("greeter.wsdl"),
                DESCRIPTION);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("wsimport",
                newModule().packageName("test.greeter").location("https://example.invalid/greeter?wsdl"),
                "project");
        executor.execute();

        Path generated = root.resolve("wsimport").resolve("generate").resolve("output")
                .resolve("sources").resolve("test").resolve("greeter");
        assertThat(generated.resolve("GreeterService.java"))
                .as("the description's service becomes a generated client class")
                .isNotEmptyFile();
        assertThat(generated.resolve("GreeterPort.java")).content().contains("package test.greeter;");
    }

    @Test
    public void states_a_configured_location_verbatim() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(WsImportModule.FOLDER)).resolve("greeter.wsdl"),
                DESCRIPTION);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("wsimport",
                newModule().packageName("test.greeter").location("https://example.invalid/greeter?wsdl"),
                "project");
        executor.execute();

        Path generated = root.resolve("wsimport").resolve("generate").resolve("output")
                .resolve("sources").resolve("test").resolve("greeter").resolve("GreeterService.java");
        assertThat(generated).content().contains("https://example.invalid/greeter?wsdl");
    }

    @Test
    public void refuses_to_generate_a_client_that_cannot_state_where_it_reads_its_description() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(WsImportModule.FOLDER)).resolve("greeter.wsdl"),
                DESCRIPTION);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("wsimport", newModule().packageName("test.greeter"), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("nothing states where it is served");
    }

    @Test
    public void generates_nothing_when_no_description_is_among_the_inputs() throws IOException {
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("wsimport", newModule(), "project");
        executor.execute();

        assertThat(root.resolve("wsimport").resolve("generate").resolve("output").resolve("sources"))
                .as("a shared wsimport.properties activates the module for every project module, "
                        + "so one without a description contributes no sources rather than failing")
                .doesNotExist();
    }

    private WsImportModule newModule() {
        return new WsImportModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
