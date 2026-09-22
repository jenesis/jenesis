package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Output;
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
import build.jenesis.project.AntlrModule;

import static org.assertj.core.api.Assertions.assertThat;
import static build.jenesis.SequencedProperties.SYSTEM;

public class AntlrModuleRunTest {

    private static final String PINS = """
            antlr/maven/com.ibm.icu/icu4j 72.1 SHA-256/3df572b240a68d13b5cd778ad2393e885d26411434cd8f098ac5987ea2e64ce3
            antlr/maven/org.abego.treelayout/org.abego.treelayout.core 1.0.3 SHA-256/fa5e31395c39c2e7d46aca0f81f72060931607b2fa41bd36038eb2cb6fb93326
            antlr/maven/org.antlr/ST4 4.3.4 SHA-256/f927ac384c46d749f8b5ec68972a53aed21e00313509299616edb73bfa15ff33
            antlr/maven/org.antlr/antlr-runtime 3.5.3 SHA-256/68bf9f5a33dfcb34033495c587e6236bef4e37aa6612919f5b1e843b90669fb9
            antlr/maven/org.antlr/antlr4 4.13.2 SHA-256/e6f0b10d2ad206f338afe16867fc47148b6729d6e3a260ea28379b91f03a3657
            antlr/maven/org.antlr/antlr4-runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
            """;
    private static final String GRAMMAR = """
            grammar Greeting;

            greeting : HELLO NAME EOF ;

            HELLO : 'hello' ;
            NAME : [a-zA-Z]+ ;
            WHITESPACE : [ \\t\\r\\n]+ -> skip ;
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(
                Files.createDirectories(project.resolve(AntlrModule.FOLDER)).resolve("Greeting.g4"),
                GRAMMAR);
    }

    @Test
    public void generates_a_lexer_and_parser_into_the_configured_package() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("antlr", newModule().packageName("test.antlr"), "project");
        executor.execute();

        Path generated = generated().resolve("test").resolve("antlr");
        assertThat(generated.resolve("GreetingLexer.java")).isNotEmptyFile();
        assertThat(generated.resolve("GreetingParser.java")).isNotEmptyFile();
        assertThat(generated.resolve("GreetingParser.java"))
                .as("the configured package is declared by the generated sources")
                .content()
                .contains("package test.antlr;");
    }

    @Test
    public void keeps_only_the_sources_the_compiler_reads() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("antlr", newModule().packageName("test.antlr"), "project");
        executor.execute();

        Path generated = generated().resolve("test").resolve("antlr");
        assertThat(generated.resolve("Greeting.tokens"))
                .as("the token files ANTLR writes beside its sources do not reach the jar")
                .doesNotExist();
        assertThat(generated.resolve("GreetingLexer.interp")).doesNotExist();
    }

    @Test
    public void passes_arguments_to_the_tool() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("antlr",
                newModule().packageName("test.antlr").arguments(List.of("-visitor", "-no-listener")),
                "project");
        executor.execute();

        Path generated = generated().resolve("test").resolve("antlr");
        assertThat(generated.resolve("GreetingVisitor.java")).isNotEmptyFile();
        assertThat(generated.resolve("GreetingListener.java"))
                .as("-no-listener reached the tool")
                .doesNotExist();
    }

    private Path generated() {
        return root.resolve("antlr").resolve(AntlrModule.GENERATE).resolve("output").resolve(BuildStep.SOURCES);
    }

    private AntlrModule newModule() {
        return new AntlrModule(Map.of("maven", MavenDefaultRepository.ofKeys(SYSTEM, new Output())), Map.of("maven", MavenPomResolver.ofKeys(SYSTEM)))
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
