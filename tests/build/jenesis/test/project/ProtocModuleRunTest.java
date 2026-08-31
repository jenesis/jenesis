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
import build.jenesis.project.ProtocModule;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocModuleRunTest {

    private static final String VERSION = "4.32.1";

    private static final String GRPC_VERSION = "1.83.1";

    private static final String DEFINITION = """
            syntax = "proto3";
            package test;
            option java_package = "test.greeting";
            option java_outer_classname = "GreetingProto";
            message Greeting {
              string text = 1;
            }
            service Greeter {
              rpc Greet (Greeting) returns (Greeting);
            }
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("protoc/maven/com.google.protobuf/protoc/exe/" + ProtocModule.classifier(), VERSION);
        versions.setProperty("protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/" + ProtocModule.classifier(),
                GRPC_VERSION);
        versions.store(project.resolve(BuildStep.VERSIONS));
    }

    @Test
    public void generates_java_sources_from_a_definition_among_the_inputs() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(ProtocModule.FOLDER)).resolve("greeting.proto"),
                DEFINITION);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("protoc", newModule(), "project");
        executor.execute();

        Path generated = root.resolve("protoc").resolve("generate").resolve("output")
                .resolve("sources").resolve("test").resolve("greeting");
        assertThat(generated.resolve("GreetingProto.java"))
                .as("the definition's messages reach javac as generated source files")
                .isNotEmptyFile();
        assertThat(generated.resolve("GreetingProto.java")).content().contains("package test.greeting;");
    }

    @Test
    public void generates_nothing_when_no_definition_is_among_the_inputs() throws IOException {
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("protoc", newModule(), "project");
        executor.execute();

        assertThat(root.resolve("protoc").resolve("generate").resolve("output").resolve("sources"))
                .as("a shared protoc.properties activates the module for every project module, "
                        + "so one without a definition contributes no sources rather than failing")
                .doesNotExist();
    }

    @Test
    public void runs_a_declared_plugin_next_to_the_compiler() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(ProtocModule.FOLDER)).resolve("greeting.proto"),
                DEFINITION);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        SequencedMap<String, String> plugins = new LinkedHashMap<>();
        plugins.put("grpc-java", "io.grpc/protoc-gen-grpc-java");
        executor.addModule("protoc", newModule().plugins(plugins), "project");
        executor.execute();

        Path generated = root.resolve("protoc").resolve("generate").resolve("output")
                .resolve("sources").resolve("test").resolve("greeting");
        assertThat(generated.resolve("GreetingProto.java"))
                .as("the compiler still writes the message classes")
                .isNotEmptyFile();
        assertThat(generated.resolve("GreeterGrpc.java"))
                .as("the plugin, resolved for this platform in its own group, writes the service stubs")
                .isNotEmptyFile();
    }

    private ProtocModule newModule() {
        return new ProtocModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
