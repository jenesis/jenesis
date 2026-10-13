package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Pinning;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.DokkaDocumentationModule;
import build.jenesis.step.Javadoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DokkaDocumentationModuleRunTest {

    private static final String PINS = """
            dokka/maven/com.fasterxml.jackson.core/jackson-annotations 2.15.3 SHA-256/aae865c3d88256d61b11523cb1e88bd48d5b9ad5855fa1fc859504fd2204708a
            dokka/maven/com.fasterxml.jackson.core/jackson-core 2.15.3 SHA-256/51fab7aad51ed588482edc507fd542747936c5094d1ab76ed21ddb63b96b610d
            dokka/maven/com.fasterxml.jackson.core/jackson-databind 2.15.3 SHA-256/c3c53333a2172a80678bda1803e39cff45bec6ae3e9c7d4f44a81ec4e2ab18dc
            dokka/maven/com.fasterxml.jackson.module/jackson-module-kotlin 2.15.3 SHA-256/8f72e6b99e6145e3284a5224355ca8ee6030afa8c1d6a3395b530d29026f8d52
            dokka/maven/com.soywiz.korlibs.korte/korte-jvm 4.0.10 SHA-256/b9b983817a80dbb5f9b599b508616115c1e8e1e3f1fccc301695f4530792690f
            dokka/maven/org.freemarker/freemarker 2.3.32 SHA-256/04d65ec1bde6cea20e3495d5e78ef96ab774d9936434861d3254bd88e7e94f92
            dokka/maven/org.jetbrains.dokka/analysis-kotlin-symbols 2.2.0 SHA-256/31b4eabf7436083ae33200da6be7203675fb1fcdcd34d2f394f55e822dac8f0f
            dokka/maven/org.jetbrains.dokka/analysis-markdown 2.2.0 SHA-256/7070de21dc5078013b53cc9c139321cceaf476c1630711cc5bda6d96e9da2087
            dokka/maven/org.jetbrains.dokka/dokka-base 2.2.0 SHA-256/721c5a1647dc49d1680d884350b438a039b2426aec65b696baee8e5aeb6442f5
            dokka/maven/org.jetbrains.dokka/dokka-cli 2.2.0 SHA-256/b8c8aba823c0ac0e173af52abf2808aa692809a40b79f63828798bb77518324a
            dokka/maven/org.jetbrains.dokka/javadoc-plugin 2.2.0 SHA-256/c52e40ae390877fee050068fecfe701cd5464354dd83d1b1880e0e17f884aa6e
            dokka/maven/org.jetbrains.dokka/kotlin-as-java-plugin 2.2.0 SHA-256/0250e98d2d2b6b3ed04d562502ed64ba4c286e42c707c6813b8c88af83d1cb8c
            dokka/maven/org.jetbrains.kotlin/kotlin-reflect 2.0.21 SHA-256/3ad2fcad0c09ddc0922debab4444d612144b7b465b75a8bb7587e20ddfafd799
            dokka/maven/org.jetbrains.kotlin/kotlin-stdlib 2.0.21 SHA-256/f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09
            dokka/maven/org.jetbrains.kotlin/kotlin-stdlib-common 1.8.20 SHA-256/fa20188abaa8ecf1d0035e93a969b071f10e45a1c8378c314521eade73f75fd5
            dokka/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.8.20 SHA-256/af1ec40c3b951afdcc0c2a0173c7b81763c5281c2d5bafbf0a8544a24c5dcc0c
            dokka/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.8.20 SHA-256/e398b67977622718bf18ff99b739c7d9da060f33fb458a2e25203221c16af010
            dokka/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.7.3 SHA-256/1ab3acc38f3e7355c4f9d1ec62107a46fa73c899f3070d055e5d4373dfe67e12
            dokka/maven/org.jetbrains.kotlinx/kotlinx-html-jvm 0.9.1 SHA-256/b68f39cd9ad7b46de0a0e77e7f5908d4e7661f3d0c85d2d9171543fcd5b156fb
            dokka/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
            dokka/maven/org.jetbrains/markdown-jvm 0.7.3 SHA-256/893abb31f15b6216f74dc704c0c6bf1e567dcf2b62cca29cf42e4f53153637ac
            dokka/maven/org.jsoup/jsoup 1.16.1 SHA-256/1f115726540ddf71958c14bc517ebfc49cf481e91cd917b0face84f01272e901
            """;

    @TempDir
    private Path root, project, module;

    @Test
    public void renders_html_with_the_options_it_is_configured_with() throws IOException {
        sources("""
                package sample

                /** The base of every sample. */
                open class Base {
                    /** Inherited by every sample. */
                    fun inherited(): String = "base"
                }

                /** A sample. */
                class Sample : Base() {
                    /** Greets. */
                    fun greet(): String = "hello"

                    /** Visible within the module alone. */
                    internal fun hidden(): String = "internal"
                }
                """);
        Files.writeString(module.resolve("Module.md"), """
                # Module Sample module

                The module documentation, read from Module.md.
                """);
        Files.writeString(module.resolve("sample.css"), ".sample-marker { color: teal; }\n");
        document(dokka -> dokka.moduleName("Sample module")
                .includes(List.of(module.resolve("Module.md")))
                .documentedVisibilities(Set.of(DokkaDocumentationModule.Visibility.PUBLIC,
                        DokkaDocumentationModule.Visibility.INTERNAL))
                .suppressInheritedMembers(true)
                .sourceLink(URI.create("https://github.com/example/sample/tree/main/src"))
                .customStyles(List.of(module.resolve("sample.css")))
                .footerMessage("Rendered by Dokka for Jenesis"));
        Path output = output();
        assertThat(output.resolve("index.html")).content()
                .contains("Rendered by Dokka for Jenesis")
                .contains("The module documentation, read from Module.md.")
                .contains("styles/sample.css");
        assertThat(output.resolve("styles/sample.css")).hasContent(".sample-marker { color: teal; }");
        Path sample = output.resolve("-sample module/sample/-sample");
        assertThat(sample.resolve("hidden.html")).as("internal declarations are documented").exists();
        assertThat(sample.resolve("inherited.html")).as("inherited members are suppressed").doesNotExist();
        assertThat(sample.resolve("greet.html")).content()
                .contains("https://github.com/example/sample/tree/main/src/sample/Sample.kt#L");
    }

    @Test
    public void renders_the_javadoc_format_with_the_javadoc_plugin() throws IOException {
        sources("""
                package sample

                /** A sample. */
                class Sample {
                    /** Greets. */
                    fun greet(): String = "hello"
                }
                """);
        document(dokka -> dokka.format(DokkaDocumentationModule.Format.JAVADOC));
        assertThat(output().resolve("sample/Sample.html")).content().contains("greet");
        assertThat(output().resolve("element-list")).content().contains("sample");
    }

    @Test
    public void fails_the_build_on_a_warning_under_fail_on_warning() throws IOException {
        sources("""
                package sample

                class Undocumented {
                    fun nothing(): Int = 1
                }
                """);
        assertThatThrownBy(() -> document(dokka -> dokka.reportUndocumented(true).failOnWarning(true)))
                .as("a warning Dokka reports fails the build as a javadoc error does")
                .rootCause()
                .hasMessageContaining("Unexpected exit code: 1")
                .hasMessageContaining("Undocumented: sample/Undocumented");
    }

    private void sources(String source) throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt"),
                source);
    }

    private void document(UnaryOperator<DokkaDocumentationModule> configuration) throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("project", project);
        executor.addModule("dokka",
                configuration.apply(new DokkaDocumentationModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))).pinning(Pinning.STRICT)),
                "project");
        executor.execute();
    }

    private Path output() {
        return root.resolve("dokka").resolve(DokkaDocumentationModule.DOCUMENTED).resolve("output")
                .resolve(Javadoc.JAVADOC);
    }
}
