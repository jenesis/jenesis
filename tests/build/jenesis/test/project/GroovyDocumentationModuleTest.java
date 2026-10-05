package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.GroovyDocumentationModule;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class GroovyDocumentationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void honors_release_from_upstream_javac_properties() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Sample.groovy"), "package sample\nclass Sample {}");
        Path process = Files.createDirectories(project.resolve("process"));
        SequencedProperties javac = new SequencedProperties();
        javac.setProperty("--release", "11");
        javac.store(process.resolve("javac.properties"));
        Path groovydocJar = Files.createFile(project.resolve("groovydoc.jar"));

        List<String> captured = new ArrayList<>();
        ToolProvider noop = new ToolProvider() {
            @Override
            public String name() {
                return "groovydoc";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                return 0;
            }
        };

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "groovydoc",
                new GroovyDocumentationModule(
                        Map.of("maven", (_, _, _) -> Optional.of(RepositoryItem.ofFile(groovydocJar))),
                        Map.of("maven", Resolver.identity()))
                        .factory(commands -> {
                            captured.addAll(commands);
                            return ProcessHandler.OfTool.of(noop).apply(commands);
                        }),
                "project");
        executor.execute();

        assertThat(captured)
                .as("--release=11 from process/javac.properties drives -javaVersion JAVA_11")
                .containsSequence("-javaVersion", "JAVA_11");
    }

    @Test
    public void documents_again_when_the_upstream_release_changes() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Sample.groovy"), "package sample\nclass Sample {}");
        Path process = Files.createDirectories(project.resolve("process"));
        Path groovydocJar = Files.createFile(project.resolve("groovydoc.jar"));
        ToolProvider noop = new ToolProvider() {
            @Override
            public String name() {
                return "groovydoc";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                return 0;
            }
        };
        List<String> captured = new ArrayList<>();
        for (String release : List.of("11", "17")) {
            SequencedProperties javac = new SequencedProperties();
            javac.setProperty("--release", release);
            javac.store(process.resolve("javac.properties"));
            captured.clear();
            BuildExecutor executor = newExecutor();
            executor.addSource("project", project);
            executor.addModule(
                    "groovydoc",
                    new GroovyDocumentationModule(
                            Map.of("maven", (_, _, _) -> Optional.of(RepositoryItem.ofFile(groovydocJar))),
                            Map.of("maven", Resolver.identity()))
                            .factory(commands -> {
                                captured.addAll(commands);
                                return ProcessHandler.OfTool.of(noop).apply(commands);
                            }),
                    "project");
            executor.execute();
        }

        assertThat(captured)
                .as("a changed --release documents the sources again for the release they now target")
                .containsSequence("-javaVersion", "JAVA_17");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
