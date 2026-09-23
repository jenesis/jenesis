package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.InferredCompilerChainModule;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ErrorProneRunTest {

    private static final String PLUGIN = "javac/plugin/maven/com.google.errorprone/error_prone_core/2.50.0";
    private static final String REFERENCE_EQUALITY = """
            package sample;
            public class Comparison {
                public boolean same(String left, String right) { return left == right; }
            }
            """;

    private static final String VALUE_EQUALITY = """
            package sample;
            import java.util.Objects;
            public class Comparison {
                public boolean same(String left, String right) { return Objects.equals(left, right); }
            }
            """;

    @TempDir
    private Path root, project, configuration;

    @BeforeEach
    public void writeProject() throws IOException {
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
    }

    @Test
    public void reports_a_check_the_compiler_alone_would_not() throws IOException {
        declarePlugin();
        writeSource(REFERENCE_EQUALITY);
        activate("arguments=-Xep:ReferenceEquality:ERROR");

        assertThatThrownBy(this::compile).rootCause().hasMessageContaining("[ReferenceEquality]");
    }

    @Test
    public void compiles_a_source_that_passes_the_promoted_check() throws IOException {
        declarePlugin();
        writeSource(VALUE_EQUALITY);
        activate("arguments=-Xep:ReferenceEquality:ERROR");

        compile();

        assertThat(compiled()).isNotEmptyFile();
    }

    @Test
    public void compiles_without_the_plugin_when_no_configuration_activates_it() throws IOException {
        writeSource(REFERENCE_EQUALITY);

        compile();

        assertThat(compiled())
                .as("the same source javac accepts on its own when Error Prone is not configured")
                .isNotEmptyFile();
    }

    @Test
    public void names_the_declaration_that_is_missing_when_no_plugin_resolved() throws IOException {
        writeSource(VALUE_EQUALITY);
        activate("");

        assertThatThrownBy(this::compile).rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No javac plugin resolved for Error Prone")
                .hasMessageContaining("@jenesis.plugin javac");
    }

    @Test
    public void hands_the_compiler_the_options_the_plugin_needs() throws IOException {
        declarePlugin();
        writeSource(VALUE_EQUALITY);
        activate("arguments=-XepAllErrorsAsWarnings");

        compile();

        SequencedProperties options = SequencedProperties.ofFiles(root.resolve("chain")
                .resolve(InferredCompilerChainModule.COMPILE)
                .resolve(InferredCompilerChainModule.ERRORPRONE)
                .resolve("output")
                .resolve("process")
                .resolve("javac.properties"));
        assertThat(options.stringPropertyNames())
                .as("the plugin is named with its arguments as one option, and javac is granted the internals it reads")
                .contains("-Xplugin:ErrorProne -XepAllErrorsAsWarnings",
                        "-XDcompilePolicy=simple",
                        "-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                        "-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");
    }

    private void declarePlugin() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty(PLUGIN, "");
        requires.store(project.resolve(BuildStep.REQUIRES));
    }

    private void writeSource(String content) throws IOException {
        Files.writeString(project.resolve(BuildStep.SOURCES + "sample").resolve("Comparison.java"), content);
    }

    private void activate(String content) throws IOException {
        Files.writeString(configuration.resolve("errorprone.properties"), content);
    }

    private void compile() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dependencies",
                new Dependencies(Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))).group("javac"),
                "project");
        executor.addModule("chain",
                new InferredCompilerChainModule(
                        new LinkedHashSet<>(Set.of(configuration)),
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", "dependencies");
        executor.execute();
    }

    private Path compiled() {
        return root.resolve("chain")
                .resolve(InferredCompilerChainModule.COMPILE)
                .resolve(InferredCompilerChainModule.JAVAC)
                .resolve("output")
                .resolve(BuildStep.CLASSES)
                .resolve("sample")
                .resolve("Comparison.class");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
