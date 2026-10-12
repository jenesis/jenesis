package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.step.Assign;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependenciesVariantTest {

    @TempDir
    private Path root;
    private Path repository, dependencies;

    @BeforeEach
    public void setUp() throws IOException {
        repository = Files.createDirectory(root.resolve("repository"));
        dependencies = Files.createDirectory(root.resolve("dependencies"));
        pom("lib", "<dependency><groupId>org.example</groupId><artifactId>native</artifactId><version>1.0</version></dependency>");
        pom("native", "");
        Files.writeString(repository.resolve("org/example/lib/1.0/lib-1.0.jar"), "lib");
        Files.writeString(repository.resolve("org/example/native/1.0/native-1.0.jar"), "main");
        Files.writeString(repository.resolve("org/example/native/1.0/native-1.0-linux.jar"), "linux");
    }

    @Test
    public void a_variant_a_module_of_the_build_publishes_replaces_the_jar_of_its_artifact() throws IOException {
        SequencedProperties variants = new SequencedProperties();
        variants.setProperty("maven/org.example/native/jar/linux/1.0", "maven/org.example/native/1.0");
        variants.store(dependencies.resolve(Assign.VARIANTS));

        SequencedProperties resolved = resolve();

        assertThat(resolved.stringPropertyNames())
                .as("a variant carries the module of its artifact, so the two never share a path")
                .contains("main/compile/maven/org.example/native/jar/linux/1.0", "main/compile/maven/org.example/lib/1.0")
                .doesNotContain("main/compile/maven/org.example/native/1.0");
    }

    @Test
    public void keeps_a_classifier_jar_beside_its_artifact_where_it_is_no_variant_of_the_build() throws IOException {
        SequencedProperties resolved = resolve();

        assertThat(resolved.stringPropertyNames())
                .as("a classifier jar resolved from a repository adds to its artifact, as Maven has it")
                .contains("main/compile/maven/org.example/native/jar/linux/1.0", "main/compile/maven/org.example/native/1.0");
    }

    @Test
    public void refuses_a_coordinate_of_a_repository_the_layout_resolves_nothing_of() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.example/lib/1.0", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        BuildExecutor executor = executor();
        executor.addSource("dependencies", dependencies);
        executor.addModule("resolved", new Dependencies(Map.of(), Map.of("module", new ModularJarResolver(false))),
                "dependencies");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maven/org.example/lib/1.0")
                .hasMessageContaining("-Djenesis.project.layout=modular_to_maven");
    }

    private SequencedProperties resolve() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.example/lib/1.0", "");
        requires.setProperty("main/compile/maven/org.example/native/jar/linux/1.0", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        BuildExecutor executor = executor();
        executor.addSource("dependencies", dependencies);
        executor.addModule("resolved", new Dependencies(
                Map.of("maven", new MavenDefaultRepository(repository.toUri(), repository, Map.of(), null)),
                Map.<String, Resolver>of("maven", new MavenPomResolver(MavenDefaultVersionNegotiator.maven()))),
                "dependencies");
        return SequencedProperties.ofFiles(executor.execute().get("resolved").resolve(BuildStep.DEPENDENCIES));
    }

    private BuildExecutor executor() throws IOException {
        return BuildExecutor.of(root.resolve("build"),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }

    private void pom(String artifactId, String dependency) throws IOException {
        Files.writeString(Files.createDirectories(repository.resolve("org/example/" + artifactId + "/1.0"))
                .resolve(artifactId + "-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0</version>
                    <dependencies>%s</dependencies>
                </project>
                """.formatted(artifactId, dependency));
    }
}
