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
import build.jenesis.project.WsImportModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WsImportModuleRunTest {

    private static final String PINS = """
            wsimport/maven/com.fasterxml.woodstox/woodstox-core 7.2.0 SHA-256/689f5be95f9bb369d6c0e8db99a99f930b0c48d6a69b46151782b5816c7ea820
            wsimport/maven/com.sun.xml.bind/jaxb-core 4.0.9 SHA-256/209eb94452d89644380e398e4ac126974718eeb3aa94ed1786654837ebcd3097
            wsimport/maven/com.sun.xml.bind/jaxb-impl 4.0.9 SHA-256/6ec4361ade0b7e0267ee4d1ef495928bf722570af25904e1618c9949b67fdd86
            wsimport/maven/com.sun.xml.bind/jaxb-jxc 4.0.9 SHA-256/734d18a38b9941d263aa65a1fb56c516b62a5138b671396e4ca7c62696106953
            wsimport/maven/com.sun.xml.bind/jaxb-xjc 4.0.9 SHA-256/eac40b7db01233395d69a996f183f8c9f697dfb5038115ba2d443a9f355378ab
            wsimport/maven/com.sun.xml.fastinfoset/FastInfoset 2.1.1 SHA-256/75d6635e09101ef1545d9a59bb4d9524ec6bf15246540af5435750f271612765
            wsimport/maven/com.sun.xml.messaging.saaj/saaj-impl 3.0.6 SHA-256/70d657b7a6bf525b98cf6780ccf0c6fc27c623e7c195574258e882c4e76c48ae
            wsimport/maven/com.sun.xml.stream.buffer/streambuffer 2.1.0 SHA-256/4fc371f13aa58b77223600cfb4651a3f98e7809ede1af70a3f00df49e2a4d028
            wsimport/maven/com.sun.xml.ws/jaxws-rt 4.0.5 SHA-256/d46f7562dca03f342093630a07c9822abe752d56867766db4e9a58a21521653d
            wsimport/maven/com.sun.xml.ws/jaxws-tools 4.0.5 SHA-256/7eb07d9fdb654264579173d6a98898f550f083cc80abd0043252e46f122f9ecd
            wsimport/maven/jakarta.activation/jakarta.activation-api 2.1.4 SHA-256/c9db52100ce6c8aac95cc39075f95720d2e561b11f8051b81c121ad4effd7004
            wsimport/maven/jakarta.annotation/jakarta.annotation-api 3.0.0 SHA-256/b01f55552284cfb149411e64eabca75e942d26d2e1786b32914250e4330afaa2
            wsimport/maven/jakarta.mail/jakarta.mail-api 2.1.5 SHA-256/aa493753acb7a8c45ba8f4c9cf1230a74e20237056dd5b5c8bc86c583e8cfa0e
            wsimport/maven/jakarta.xml.bind/jakarta.xml.bind-api 4.0.5 SHA-256/5e489b6c874c4119e003ff1403db523ee3a8959ec499f3de29e77245efccf216
            wsimport/maven/jakarta.xml.soap/jakarta.xml.soap-api 3.0.2 SHA-256/62ecd5c3b5c107779e5ffe84922594c381f7a8e397320a05c3ee3957b5b7863f
            wsimport/maven/jakarta.xml.ws/jakarta.xml.ws-api 4.0.3 SHA-256/e2244d1046363f21a1dbb9485d592f6697b219b8086de70de759c7c5d725517f
            wsimport/maven/org.codehaus.woodstox/stax2-api 4.3.0 SHA-256/7c805f36129ea9fa42b696093b7ae1eb20bb6ccec65c8280d6f33db5609ca5e1
            wsimport/maven/org.eclipse.angus/angus-activation 2.0.3 SHA-256/a6bd35c538cf90fff941ad6258c40c08fca0b5c9c3f536c657114f27ce0527a7
            wsimport/maven/org.eclipse.angus/angus-mail 2.0.5 SHA-256/b4d8c30d35f455def6c7a05fe595a1e62ea2b80cac3efec1e9ccf4118b23168a
            wsimport/maven/org.glassfish.external/management-api 3.3.0 SHA-256/317e5b0ca2e90542f4462eb87e47dc3c23d5b2fa3d992b0d68e6aa14658efda7
            wsimport/maven/org.glassfish.gmbal/gmbal-api-only 4.1.2 SHA-256/9a0cbc5116745141b713b863fcf367740864301050db81fa9dc37ee828ac4f87
            wsimport/maven/org.glassfish.ha/ha-api 3.1.13 SHA-256/49770bf120f76d78ead363e00fa09d02a76d5f12a88e654009066aec67f8d8ad
            wsimport/maven/org.glassfish.pfl/pfl-basic 5.1.1 SHA-256/d6162cbd121913412622042b0f76c9cab61d8348fe0f45b41008d5978bb774e4
            wsimport/maven/org.glassfish.pfl/pfl-dynamic 5.1.1 SHA-256/411360c3fbea6034289a4dbe8622131e447a53a2edf995e348601e1d2543ea6c
            wsimport/maven/org.glassfish.pfl/pfl-tf 5.1.1 SHA-256/7ebe4d6a191b1191eb8502c17634071fb58d2ce06e0e10bfd44dbd2eb0b9ded8
            wsimport/maven/org.jvnet.mimepull/mimepull 1.11.0 SHA-256/af26c386c4baf48dab68f38b132d5990bd8f8324424c8579edd3ffce250d329e
            wsimport/maven/org.jvnet.staxex/stax-ex 2.1.0 SHA-256/9f786ab52392106a53491bd1ddd8bd9028c95bb280e30387b70d498a8647cf35
            wsimport/maven/org.ow2.asm/asm 9.9.1 SHA-256/6f3828a215c920059a5efa2fb55c233d6c54ec5cadca99ce1b1bdd10077c7ddd
            wsimport/maven/org.ow2.asm/asm-analysis 9.9.1 SHA-256/6260bffc8ec008dd1b713702c7994e2c94d188a3da5bef9e87278a16df6a7522
            wsimport/maven/org.ow2.asm/asm-tree 9.9.1 SHA-256/0f3555096b720b820bbacab0b515589bee0200bee099bda14c561738ae837ba1
            wsimport/maven/org.ow2.asm/asm-util 9.9.1 SHA-256/c5ebbbeaf68126af094b42fa4800f59bc4413abd02d95b9aefad722cd257e207
            """;


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
        versions.load(new StringReader(PINS));
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
        return new WsImportModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()))
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
