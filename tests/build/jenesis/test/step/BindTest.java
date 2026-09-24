package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.step.Bind;

import static org.assertj.core.api.Assertions.assertThat;

public class BindTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, original;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        original = Files.createDirectory(root.resolve("original"));
    }

    @Test
    public void binds_several_sources_into_one_input_at_their_targets() throws IOException {
        Path schemas = Files.createDirectory(root.resolve("schemas"));
        Files.writeString(schemas.resolve("order.xsd"), "<order/>");
        Path catalog = Files.writeString(root.resolve("catalog.xml"), "<catalog/>");
        SequencedMap<Path, Path> bindings = new LinkedHashMap<>();
        bindings.put(Path.of(""), schemas);
        bindings.put(Path.of("xjc/catalog.xml"), catalog);
        BuildExecutor buildExecutor = BuildExecutor.of(root.resolve("target"),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        buildExecutor.addModule("inputs", Bind.asInputs(new LinkedHashMap<>(Map.of("contracts", bindings))));

        Path input = buildExecutor.execute().get("inputs/contracts");

        assertThat(input.resolve("order.xsd")).content().isEqualTo("<order/>");
        assertThat(input.resolve("xjc/catalog.xml")).content().isEqualTo("<catalog/>");
    }

    @Test
    public void can_link_files() throws IOException {
        Files.writeString(original.resolve("file"), "foo");
        Files.writeString(Files.createDirectories(original.resolve("folder/sub")).resolve("file"), "bar");
        BuildStepResult result = new Bind(
                Map.of(
                        Path.of("file"), Path.of("other/copied"),
                        Path.of("folder"), Path.of("other"))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("original", new BuildStepArgument(
                        original,
                        Map.of(Path.of("file"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("folder/sub/file"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve("other/copied")).content().isEqualTo("foo");
        assertThat(next.resolve("other/sub/file")).content().isEqualTo("bar");
    }

    @Test
    public void binds_a_folder_reached_through_a_symbolic_link() throws IOException {
        Path shared = Files.createDirectory(root.resolve("shared"));
        Files.writeString(shared.resolve("pin-lib.properties"), "org.example/lib=1.0");
        Files.createSymbolicLink(original.resolve("folder"), shared);
        BuildStepResult result = new Bind(Map.of(Path.of("folder"), Path.of("linked"))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("original", new BuildStepArgument(
                        original,
                        Map.of(Path.of("folder/pin-lib.properties"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve("linked/pin-lib.properties"))
                .as("change detection follows a link to decide the folder changed, so the bind that"
                        + " acts on that decision has to reach the same files")
                .content().isEqualTo("org.example/lib=1.0");
    }
}
