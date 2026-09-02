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
import build.jenesis.project.ProtocModule;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocModuleRunTest {

    private static final String PINS = """
            protoc/maven/com.google.protobuf/protoc/exe/linux-x86_64 4.32.1 SHA-256/9a757b79f98195a1798e6e5e698360b621121de79a87aac0f7cf4a8a3bd5dfa2
            protoc/maven/com.google.protobuf/protoc/exe/linux-aarch_64 4.32.1 SHA-256/0058033e6e98dbdf7221d4723e93ac861e3b17cd7cff78963d9acbe282d4cae2
            protoc/maven/com.google.protobuf/protoc/exe/osx-x86_64 4.32.1 SHA-256/58671447f9b871f4832108bad560c9fae3e32e0484bb24febcb15e67da15dc4d
            protoc/maven/com.google.protobuf/protoc/exe/osx-aarch_64 4.32.1 SHA-256/e3b836d8497998d009a7aabd1e6171d8f8cdcd5052f8c4b874a873c21fa6b1c2
            protoc/maven/com.google.protobuf/protoc/exe/windows-x86_64 4.32.1 SHA-256/cd1136e75dab5bab146c981736f3cb8c4ac98b717a0f4fbc0a65a82f6c883b16
            protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/linux-x86_64 1.83.1 SHA-256/db4044e78391d5a23439143c8147f07ba5877675587b4d48f3b68fbee3893589
            protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/linux-aarch_64 1.83.1 SHA-256/5af5544369bd8557111abfbed21453bb6fa867bf32a25f2ffe5f72366e705cef
            protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/osx-x86_64 1.83.1 SHA-256/372e13b25cb058ea6e3ab6cb54ba1458d5d2bbe1cfdcd37bc57e82b872656e21
            protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/osx-aarch_64 1.83.1 SHA-256/372e13b25cb058ea6e3ab6cb54ba1458d5d2bbe1cfdcd37bc57e82b872656e21
            protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/windows-x86_64 1.83.1 SHA-256/f4654b0b8e1faedf897f4abcd754c5250cb48afe07d595e56efc9fb478736c73
            """;


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
        versions.load(new StringReader(PINS));
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
        return new ProtocModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()))
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
