package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependenciesModuleClaimTest {

    @TempDir
    private Path root, mavenRepoFolder, work;
    private Path previous, next, supplement, dependencies;

    @BeforeEach
    public void setUp() throws IOException {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        dependencies = Files.createDirectory(root.resolve("dependencies"));
    }

    @Test
    public void rejects_two_artifacts_that_carry_one_module_name() throws IOException {
        modularLib("one-lib", "1.0", "lib.shared", "one");
        modularLib("two-lib", "2.0", "lib.shared", "two");

        assertThatThrownBy(() -> resolve("org.example/one-lib/1.0", "org.example/two-lib/2.0"))
                .as("the two are materialized under distinct file names, so only the module they declare"
                        + " shows that the module path could resolve either of them")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maven/org.example/one-lib/1.0 and maven/org.example/two-lib/2.0"
                        + " both carry module lib.shared"
                        + " - a module path resolves whichever of the two comes first,"
                        + " so drop one with @jenesis.exclude");
    }

    @Test
    public void accepts_two_artifacts_that_carry_distinct_module_names() throws IOException {
        modularLib("one-lib", "1.0", "lib.one", "one");
        modularLib("two-lib", "2.0", "lib.two", "two");

        BuildStepResult result = resolve("org.example/one-lib/1.0", "org.example/two-lib/2.0");

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Dependencies.RESOLVED + "lib.one-1.0.jar")).exists();
        assertThat(next.resolve(Dependencies.RESOLVED + "lib.two-2.0.jar")).exists();
    }

    private void modularLib(String artifactId, String version, String module, String name) throws IOException {
        Path sources = Files.createDirectories(work.resolve(artifactId + "-sources"));
        Path classes = Files.createDirectories(work.resolve(artifactId + "-classes"));
        Path folder = Files.createDirectories(sources.resolve(name));
        Files.writeString(folder.resolve("Value.java"), """
                package %s;
                public class Value {
                }
                """.formatted(name));
        Files.writeString(sources.resolve("module-info.java"), """
                module %s {
                    exports %s;
                }
                """.formatted(module, name));
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                "--module-version", version,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                folder.resolve("Value.java").toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        Files.writeString(Files
                        .createDirectories(mavenRepoFolder.resolve("org/example/" + artifactId + "/" + version))
                        .resolve(artifactId + "-" + version + ".pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                </project>
                """.formatted(artifactId, version));
        Path file = mavenRepoFolder.resolve("org/example/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file));
             Stream<Path> stream = Files.walk(classes)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path)
                        .toString()
                        .replace(File.separatorChar, '/')));
                output.write(Files.readAllBytes(path));
                output.closeEntry();
            }
        }
    }

    private BuildStepResult resolve(String... coordinates) throws IOException {
        SequencedProperties requires = new SequencedProperties();
        for (String coordinate : coordinates) {
            requires.setProperty("main/compile/maven/" + coordinate, "");
        }
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        return new Dependencies(
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {
                })),
                Map.of("maven", new MavenPomResolver(MavenDefaultVersionNegotiator.maven()))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("dependencies", new BuildStepArgument(dependencies, new LinkedHashMap<>(
                        Map.of(Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED)))))))
                .toCompletableFuture()
                .join();
    }
}
