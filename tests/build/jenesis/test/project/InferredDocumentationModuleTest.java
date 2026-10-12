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
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.DokkaDocumentationModule;
import build.jenesis.project.GroovyDocumentationModule;
import build.jenesis.project.InferredDocumentationChainModule;
import build.jenesis.project.InferredDocumentationModule;
import build.jenesis.project.ScalaDocumentationModule;
import org.assertj.core.api.AbstractThrowableAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredDocumentationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void scan_detects_each_language_from_source_extensions() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Greeter.java"), "package sample; class Greeter {}");
        Files.writeString(sample.resolve("Sample.kt"), "package sample\nclass Sample");
        Files.writeString(sample.resolve("Pure.scala"), "package sample\nclass Pure");
        Files.writeString(sample.resolve("Script.groovy"), "package sample\nclass Script {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(Map.of(), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("doc/scan");

        SequencedProperties scan = SequencedProperties.ofFiles(root
                .resolve("doc").resolve("scan").resolve("output").resolve("scan.properties"));
        assertThat(scan.getProperty("javadoc")).isEqualTo("true");
        assertThat(scan.getProperty("dokka")).isEqualTo("true");
        assertThat(scan.getProperty("scaladoc")).isEqualTo("true");
        assertThat(scan.getProperty("groovydoc")).isEqualTo("true");
    }

    @Test
    public void scan_reports_absent_languages_as_false() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Greeter.java"), "package sample; class Greeter {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(Map.of(), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("doc/scan");

        SequencedProperties scan = SequencedProperties.ofFiles(root
                .resolve("doc").resolve("scan").resolve("output").resolve("scan.properties"));
        assertThat(scan.getProperty("javadoc")).isEqualTo("true");
        assertThat(scan.getProperty("dokka")).isEqualTo("false");
        assertThat(scan.getProperty("scaladoc")).isEqualTo("false");
        assertThat(scan.getProperty("groovydoc")).isEqualTo("false");
    }

    @Test
    public void documents_java_sources_that_declare_a_module_as_that_module() throws IOException {
        Path sources = Files.createDirectories(project.resolve(BuildStep.SOURCES));
        Files.writeString(sources.resolve("module-info.java"), "module sample {\n    exports sample;\n}\n");
        Files.writeString(Files.createDirectories(sources.resolve("sample")).resolve("Greeter.java"),
                "package sample; /** Greets. */ public class Greeter {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(Map.of(), Map.of("maven", Resolver.identity())),
                "project");
        Path javadoc = executor.execute().get("doc/document/aggregate").resolve("javadoc");

        assertThat(javadoc.resolve("sample/module-summary.html"))
                .as("a module's documentation carries its module page and its packages below its name")
                .isNotEmptyFile();
        assertThat(javadoc.resolve("sample/sample/Greeter.html")).isNotEmptyFile();
    }

    @Test
    public void archives_an_intentionally_empty_javadoc_jar_without_rendering_the_documentation() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Greeter.java"), "package sample; class Greeter {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("documentation",
                InferredDocumentationModule.ofEnvironment(new Environment(Map.of("documentation.empty", "true")),
                        Map.of(),
                        Map.of("maven", Resolver.identity())),
                "project");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps.keySet())
                .as("no documentation tool runs for an empty javadoc jar")
                .noneMatch(step -> step.startsWith("documentation/generate"));
        try (JarFile jar = new JarFile(steps.get("documentation/archive")
                .resolve(BuildStep.DOCUMENTATION)
                .resolve("javadoc.jar")
                .toFile())) {
            assertThat(jar.stream().map(JarEntry::getName))
                    .containsExactlyInAnyOrder("META-INF/", "META-INF/MANIFEST.MF", "INTENTIONALLY_EMPTY");
        }
    }

    @Test
    public void documents_a_module_its_description_declares_a_test_module_only_where_its_tests_are_staged()
            throws IOException {
        Files.writeString(project.resolve(BuildStep.MODULE), "test=main_artifact\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("documentation",
                InferredDocumentationModule.ofEnvironment(new Environment(Map.of("documentation.empty", "true")),
                        Map.of(),
                        Map.of("maven", Resolver.identity())),
                "project");
        executor.addModule("staged",
                InferredDocumentationModule.ofEnvironment(new Environment(Map.of("documentation.empty", "true",
                                "stage.tests", "true")),
                        Map.of(),
                        Map.of("maven", Resolver.identity())),
                "project");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps.keySet())
                .as("the documentation of a test module is published only with its staged tests")
                .noneMatch(step -> step.startsWith("documentation/"))
                .contains("staged/archive");
    }

    @Test
    public void dokka_requires_the_cli_and_plugin_coordinates_under_a_qualified_trail() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dokka",
                new DokkaDocumentationModule(Map.of(), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("dokka/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root
                .resolve("dokka").resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).containsExactlyInAnyOrder(
                "dokka/runtime/maven/org.jetbrains.dokka/dokka-cli/RELEASE",
                "dokka/runtime/maven/org.jetbrains.dokka/dokka-base/RELEASE",
                "dokka/runtime/maven/org.jetbrains.dokka/analysis-kotlin-symbols/RELEASE");
    }

    @Test
    public void dokka_requires_nothing_without_a_maven_resolver() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dokka",
                new DokkaDocumentationModule(Map.of(), Map.of("module", Resolver.identity())),
                "project");
        executor.execute("dokka/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root
                .resolve("dokka").resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).isEmpty();
    }

    @Test
    public void scaladoc_requires_pins_the_jackson_annotations_its_runtime_expects() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("scaladoc",
                new ScalaDocumentationModule(Map.of(), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("scaladoc/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root
                .resolve("scaladoc").resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames()).contains(
                "scaladoc/runtime/maven/org.scala-lang/scaladoc_3/RELEASE",
                "scaladoc/runtime/maven/com.fasterxml.jackson.core/jackson-annotations/2.21");
    }

    @Test
    public void groovydoc_requires_the_groovydoc_coordinate_under_a_qualified_trail() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("groovydoc",
                new GroovyDocumentationModule(Map.of(), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("groovydoc/required");

        SequencedProperties requires = SequencedProperties.ofFiles(root
                .resolve("groovydoc").resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("groovydoc/runtime/maven/org.apache.groovy/groovy-groovydoc/RELEASE");
    }

    @Test
    public void refuses_a_key_dokka_properties_does_not_know() throws IOException {
        refusing("moduleName=sample\n")
                .hasMessageContaining("Unknown dokka property: moduleName")
                .hasMessageContaining("dokka.properties")
                .hasMessageContaining("module-name")
                .hasMessageContaining("external-link.<name>.package-list");
    }

    @Test
    public void refuses_a_format_the_dokka_plugins_do_not_render() throws IOException {
        refusing("format=gfm\n")
                .hasMessageContaining("Unknown dokka format: gfm")
                .hasMessageContaining("(expected one of [html, javadoc])");
    }

    @Test
    public void refuses_a_visibility_dokka_does_not_know() throws IOException {
        refusing("documented-visibilities=public,friends\n")
                .hasMessageContaining("Unknown dokka visibility in documented-visibilities")
                .hasMessageContaining(": friends")
                .hasMessageContaining("[internal, package, private, protected, public]");
    }

    @Test
    public void refuses_a_jdk_version_that_is_no_feature_release() throws IOException {
        refusing("jdk-version=1.8\n").hasMessageContaining("The dokka property jdk-version")
                .hasMessageContaining("is 1.8, but names the feature release");
    }

    @Test
    public void refuses_a_line_suffix_without_a_source_link() throws IOException {
        refusing("source-link-suffix=#L\n").hasMessageContaining("declare source-link=");
    }

    @Test
    public void refuses_a_source_link_that_is_no_url() throws IOException {
        refusing("source-link=https://example.com/a b\n")
                .hasMessageContaining("The dokka property source-link")
                .hasMessageContaining("names https://example.com/a b, which is no URL");
    }

    @Test
    public void refuses_a_package_list_without_its_external_link() throws IOException {
        refusing("external-link.okio.package-list=https://square.github.io/okio/3.x/okio/okio/package-list\n")
                .hasMessageContaining("declare external-link.okio=<url>");
    }

    @Test
    public void refuses_a_suppressed_package_that_is_no_regular_expression() throws IOException {
        refusing("suppressed-packages=sample.(internal\n")
                .hasMessageContaining("The dokka property suppressed-packages")
                .hasMessageContaining("names sample.(internal, which is no regular expression");
    }

    @Test
    public void refuses_an_include_the_module_folder_does_not_hold() throws IOException {
        refusing("includes=docs/Missing.md\n")
                .hasMessageContaining("The dokka property includes")
                .hasMessageContaining("names docs/Missing.md")
                .hasMessageContaining("name a file relative to the module's folder");
    }

    private AbstractThrowableAssert<?, ? extends Throwable> refusing(String properties) throws IOException {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("dokka.properties"), properties);
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(Map.of(), Map.of("maven", Resolver.identity()))
                        .configuration(new LinkedHashSet<>(List.of(configuration)))
                        .location(project),
                "project");
        return assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause();
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }
}
