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
import build.jenesis.SequencedProperties;
import build.jenesis.project.InferredDocumentationChainModule;
import build.jenesis.project.InferredDocumentationModule;

import static org.assertj.core.api.Assertions.assertThat;

public class InferredDocumentationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void scan_records_the_extension_of_every_source() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Greeter.java"), "package sample; class Greeter {}");
        Files.writeString(sample.resolve("Sample.kt"), "package sample\nclass Sample");
        Files.writeString(sample.resolve("Pure.scala"), "package sample\nclass Pure");
        Files.writeString(sample.resolve("Script.groovy"), "package sample\nclass Script {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(new LinkedHashSet<>(), Map.of(), Map.of()),
                "project");
        executor.execute("doc/scan");

        SequencedProperties scan = SequencedProperties.ofFiles(root
                .resolve("doc").resolve("scan").resolve("output").resolve("scan.properties"));
        assertThat(scan.getProperty("javadoc")).isEqualTo("true");
        assertThat(scan.entries("extensions")).containsExactly(".groovy", ".java", ".kt", ".scala");
    }

    @Test
    public void scan_records_only_the_extensions_a_module_holds() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Greeter.java"), "package sample; class Greeter {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(new LinkedHashSet<>(), Map.of(), Map.of()),
                "project");
        executor.execute("doc/scan");

        SequencedProperties scan = SequencedProperties.ofFiles(root
                .resolve("doc").resolve("scan").resolve("output").resolve("scan.properties"));
        assertThat(scan.getProperty("javadoc")).isEqualTo("true");
        assertThat(scan.entries("extensions")).containsExactly(".java");
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
                new InferredDocumentationChainModule(new LinkedHashSet<>(), Map.of(), Map.of()),
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
                InferredDocumentationModule.ofEnvironment(new Environment(Map.of("documentation.empty", "true")), new LinkedHashSet<>(), Map.of(), Map.of()),
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
