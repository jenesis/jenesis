package build.jenesis.test.module;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.module.PinFile;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PinFileTest {

    @TempDir
    private Path root;
    private Path next, input, file;
    private final SequencedProperties inventory = new SequencedProperties();

    @BeforeEach
    public void setUp() throws Exception {
        next = Files.createDirectory(root.resolve("next"));
        input = Files.createDirectory(root.resolve("input"));
        file = root.resolve("pin-shared.properties");
    }

    private void imports(String path, String declaration) {
        inventory.setProperty(Inventory.prefixOf(path) + "bom." + count(path, "bom."), "file/" + declaration + " ");
    }

    private void resolved(String path, String group, String coordinate, String version) {
        String key = Inventory.prefixOf(path) + "dependency." + count(path, "dependency.");
        inventory.setProperty(key, coordinate + "/" + version + " missing.jar SHA-256/" + version);
        inventory.setProperty(key + ".group", group);
    }

    private int count(String path, String kind) {
        String prefix = Inventory.prefixOf(path) + kind;
        return (int) inventory.stringPropertyNames().stream()
                .filter(name -> name.startsWith(prefix) && name.indexOf('.', prefix.length()) < 0)
                .count();
    }

    private SequencedProperties run(String... paths) throws IOException {
        inventory.store(input.resolve(Inventory.INVENTORY));
        new PinFile(new LinkedHashSet<>(List.of(paths)), file, new LinkedHashSet<>(), new HashDigestFunction("SHA-256"))
                .apply(Runnable::run,
                        new BuildStepContext(root.resolve("previous"),
                                next,
                                Files.createDirectories(root.resolve("supplement")),
                                Files.createDirectories(root.resolve("persistent"))),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        return SequencedProperties.ofFiles(file);
    }

    @Test
    public void writes_every_group_that_imports_the_file() throws IOException {
        imports("cli", "main/pin-shared.properties");
        imports("cli", "launcher/pin-shared.properties");
        resolved("cli", "main", "maven/org.foo/foo", "1.0");
        resolved("cli", "launcher", "maven/build.jenesis/build.jenesis.launcher", "0.6.3");
        resolved("cli", "checkstyle:tool", "maven/org.bar/bar", "2.0");
        assertThat(run("cli")).containsOnly(
                Map.entry("org.foo/foo", "1.0 SHA-256/1.0"),
                Map.entry("build.jenesis/build.jenesis.launcher", "0.6.3 SHA-256/0.6.3"));
    }

    @Test
    public void leaves_out_the_modules_that_do_not_import_the_file() throws IOException {
        imports("enterprise", "main/pin-shared.properties");
        imports("core", "main/pin-core.properties");
        resolved("enterprise", "main", "maven/org.foo/foo", "2.0");
        resolved("core", "main", "maven/org.foo/foo", "1.0");
        resolved("core", "main", "maven/org.baz/baz", "3.0");
        assertThat(run("enterprise", "core")).containsOnly(Map.entry("org.foo/foo", "2.0 SHA-256/2.0"));
    }

    @Test
    public void writes_every_main_group_while_no_module_imports_the_file() throws IOException {
        resolved("first", "main", "maven/org.foo/foo", "1.0");
        resolved("first", "launcher", "maven/build.jenesis/build.jenesis.launcher", "0.6.3");
        resolved("second", "main", "maven/org.baz/baz", "3.0");
        assertThat(run("first", "second")).containsOnly(
                Map.entry("org.foo/foo", "1.0 SHA-256/1.0"),
                Map.entry("org.baz/baz", "3.0 SHA-256/3.0"));
    }

    @Test
    public void refuses_a_coordinate_its_importers_resolve_at_two_versions() {
        imports("first", "main/pin-shared.properties");
        imports("second", "launcher/pin-shared.properties");
        resolved("first", "main", "maven/org.foo/foo", "1.0");
        resolved("second", "launcher", "maven/org.foo/foo", "2.0");
        assertThatThrownBy(() -> run("first", "second"))
                .hasStackTraceContaining("org.foo/foo 1.0 SHA-256/1.0 in first (main), 2.0 SHA-256/2.0 in second (launcher)");
        assertThat(file).doesNotExist();
    }
}
