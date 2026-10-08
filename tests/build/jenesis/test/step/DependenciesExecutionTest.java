package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.DiscoverySources;
import build.jenesis.HashDigestFunction;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;

public class DependenciesExecutionTest {

    @TempDir
    private Path input, root;
    private BuildExecutor buildExecutor;

    @BeforeEach
    public void setUp() throws Exception {
        buildExecutor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }

    @Test
    public void can_resolve_dependencies() throws IOException {
        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("main/compile/foo/bar", "");
        dependencies.store(input.resolve(BuildStep.REQUIRES));
        buildExecutor.addSource("input", input);
        buildExecutor.addModule("output", new Dependencies(
                Map.of("foo", (_, coordinate, _) -> Optional.of(() -> new ByteArrayInputStream(
                        coordinate.getBytes(StandardCharsets.UTF_8)))),
                Map.of("foo", Resolver.identity())), "input");
        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps)
                .as("the module names one output, the resolution it verified")
                .containsKey("output");
        SequencedProperties resolved = SequencedProperties.ofFiles(steps.get("output").resolve(BuildStep.DEPENDENCIES));
        assertThat(resolved.stringPropertyNames()).containsExactly("main/compile/foo/bar");
        assertThat(resolved.getProperty("main/compile/foo/bar")).doesNotContain(" ");
        assertThat(steps.get("output")
                .resolve(resolved.getProperty("main/compile/foo/bar"))).content().isEqualTo("bar");
    }

    @Test
    public void records_the_source_archive_a_domain_names_for_each_dependency() throws IOException {
        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("main/compile/foo/bar", "");
        dependencies.store(input.resolve(BuildStep.REQUIRES));
        buildExecutor.addSource("input", input);
        buildExecutor.addModule("output", new Dependencies(
                Map.of("foo", (_, coordinate, _) -> Optional.of(() -> new ByteArrayInputStream(
                                coordinate.getBytes(StandardCharsets.UTF_8))),
                        DiscoverySources.NAME, (_, coordinate, _) -> Optional.of(() -> new ByteArrayInputStream(
                                ("https://example.org/" + coordinate + ".zip").getBytes(StandardCharsets.UTF_8)))),
                Map.of("foo", Resolver.identity())), "input");
        SequencedMap<String, Path> steps = buildExecutor.execute();

        SequencedProperties archives = SequencedProperties.ofFiles(steps.get("output").resolve(Dependencies.ARCHIVES));
        assertThat(archives.getProperty("foo/bar")).isEqualTo("https://example.org/foo/bar.zip");
        assertThat(steps.get("output").resolve(BuildStep.SOURCES))
                .as("the archives are recorded apart from the sources a step reads")
                .doesNotExist();
    }

}
