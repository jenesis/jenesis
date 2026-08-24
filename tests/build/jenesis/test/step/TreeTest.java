package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;
import build.jenesis.step.Tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TreeTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, argument, testArgument;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        argument = Files.createDirectory(root.resolve("argument"));
        testArgument = Files.createDirectory(root.resolve("test-argument"));
    }

    @Test
    public void rejects_an_unknown_tree_format() {
        System.setProperty("jenesis.tree.format", "fancy");
        try {
            assertThatThrownBy(() -> new Tree(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown jenesis.tree.format 'fancy'")
                    .hasMessageContaining("full")
                    .hasMessageContaining("compact")
                    .hasMessageContaining("main");
        } finally {
            System.clearProperty("jenesis.tree.format");
        }
    }

    @Test
    public void compact_format_prints_only_internal_and_counts_external() throws IOException {
        System.setProperty("jenesis.tree.format", "compact");
        try {
            SequencedProperties graph = new SequencedProperties();
            graph.setProperty("edge/0", "main\tcompile\tmodule\ttrue\tcompile\t1.0\t\tmodule/foo/1.0");
            graph.setProperty("edge/1", "main\tcompile\tmodule\ttrue\tcompile\t2.0\tmodule/foo/1.0\tmaven/org.ext/lib/2.0");
            graph.setProperty("vertex/main/compile/module/foo", "1.0\tfoo\tfalse\ttrue");
            graph.setProperty("vertex/main/compile/maven/org.ext/lib", "2.0\t\tfalse\tfalse");
            graph.store(argument.resolve("graph.properties"));
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module.graph.0", "graph.properties");
            inventory.store(argument.resolve(Inventory.INVENTORY));

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new Tree(new PrintStream(bytes, true, StandardCharsets.UTF_8)).apply(
                    Runnable::run,
                    new BuildStepContext(previous, next, supplement),
                    new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                            argument,
                            Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                    .toCompletableFuture().join();
            String text = bytes.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
            assertThat(text).contains("(module foo, local)");
            assertThat(text).doesNotContain("maven/org.ext/lib");
            assertThat(text).contains("1 external dependency");
        } finally {
            System.clearProperty("jenesis.tree.format");
        }
    }

    @Test
    public void main_format_omits_a_test_module() throws IOException {
        System.setProperty("jenesis.tree.format", "main");
        try {
            SequencedProperties graph = new SequencedProperties();
            graph.setProperty("edge/0", "main\tcompile\tmodule\ttrue\tcompile\t1.0\t\tmodule/foo/1.0");
            graph.setProperty("vertex/main/compile/module/foo", "1.0\tfoo\tfalse\ttrue");
            graph.store(argument.resolve("graph.properties"));
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module-foo.graph.0", "graph.properties");
            inventory.store(argument.resolve(Inventory.INVENTORY));

            SequencedProperties testGraph = new SequencedProperties();
            testGraph.setProperty("edge/0", "main\tcompile\tmodule\ttrue\tcompile\t1.0\t\tmodule/bar/1.0");
            testGraph.setProperty("vertex/main/compile/module/bar", "1.0\tbar\tfalse\ttrue");
            testGraph.store(testArgument.resolve("graph.properties"));
            SequencedProperties testInventory = new SequencedProperties();
            testInventory.setProperty("module-bar.graph.0", "graph.properties");
            testInventory.setProperty("module-bar.test", "foo");
            testInventory.store(testArgument.resolve(Inventory.INVENTORY));

            SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
            arguments.put("foo", new BuildStepArgument(argument,
                    Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))));
            arguments.put("bar", new BuildStepArgument(testArgument,
                    Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new Tree(new PrintStream(bytes, true, StandardCharsets.UTF_8)).apply(
                    Runnable::run,
                    new BuildStepContext(previous, next, supplement),
                    arguments)
                    .toCompletableFuture().join();
            String text = bytes.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
            assertThat(text).contains("module/foo");
            assertThat(text).doesNotContain("module/bar");
        } finally {
            System.clearProperty("jenesis.tree.format");
        }
    }

    @Test
    public void renders_the_resolution_graph_from_the_inventory() throws IOException {
        SequencedProperties graph = new SequencedProperties();
        graph.setProperty("edge/0", "main\tcompile\tmaven\ttrue\tcompile\t1.0\t\tmaven/org.foo/bar/1.0");
        graph.setProperty("edge/1", "main\tcompile\tmaven\ttrue\tcompile\t2.0\tmaven/org.foo/bar/1.0\tmaven/org.foo/baz/2.0");
        graph.setProperty("vertex/main/compile/maven/org.foo/bar", "1.0\torg.foo.bar\tfalse");
        graph.setProperty("vertex/main/compile/maven/org.foo/baz", "2.0\t\tfalse");
        graph.store(argument.resolve("graph.properties"));
        SequencedProperties licenses = new SequencedProperties();
        licenses.setProperty("maven/org.foo/bar/1.0#0#name", "Apache-2.0");
        licenses.store(argument.resolve("licenses.properties"));
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module.graph.0", "graph.properties");
        inventory.setProperty("module.licenses.0", "licenses.properties");
        inventory.store(argument.resolve(Inventory.INVENTORY));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BuildStepResult result = new Tree(new PrintStream(bytes, true, StandardCharsets.UTF_8)).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                        argument,
                        Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        String text = bytes.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
        assertThat(text).contains("main/compile (module)");
        assertThat(text).contains("maven/org.foo/bar 1.0 [compile] (module org.foo.bar) {Apache-2.0}");
        assertThat(text).contains("└─ maven/org.foo/baz 2.0 [compile]");
        assertThat(text).contains("maven/org.foo/bar -> 1.0");
    }
}
