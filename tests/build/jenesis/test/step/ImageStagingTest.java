package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ImageStaging;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;

public class ImageStagingTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, source;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        source = Files.createDirectory(root.resolve("source"));
    }

    @Test
    public void stages_application_image_tree() throws IOException {
        Path folder = Files.createDirectory(source.resolve("module"));
        Path image = folder.resolve("packages").resolve("app");
        Files.createDirectories(image.resolve("bin"));
        Files.writeString(image.resolve("bin").resolve("app"), "launcher");
        Files.writeString(image.resolve("app.cfg"), "config");
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module.package", "packages");
        inventory.setProperty("module.artifact", "demo.app");
        inventory.store(folder.resolve(Inventory.INVENTORY));

        BuildStepResult result = run(folder);

        assertThat(result.next()).isTrue();
        assertThat(next.resolve("demo.app/app/bin/app")).hasContent("launcher");
        assertThat(next.resolve("demo.app/app/app.cfg")).hasContent("config");
    }

    @Test
    public void preserves_each_module_image_separately() throws IOException {
        Path foo = stagedImage("foo", "foo-app", "foo-launcher");
        Path bar = stagedImage("bar", "bar-app", "bar-launcher");

        run(foo, bar);

        assertThat(next.resolve("foo/foo-app/bin/foo-app")).hasContent("foo-launcher");
        assertThat(next.resolve("bar/bar-app/bin/bar-app")).hasContent("bar-launcher");
    }

    @Test
    public void stages_under_the_configured_inventory_key() throws IOException {
        Path folder = Files.createDirectory(source.resolve("module"));
        Path image = folder.resolve("runtime");
        Files.createDirectories(image.resolve("bin"));
        Files.writeString(image.resolve("bin").resolve("java"), "launcher");
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module.image", "runtime");
        inventory.setProperty("module.artifact", "demo.app");
        inventory.store(folder.resolve(Inventory.INVENTORY));

        BuildStepResult result = new ImageStaging("image").apply(Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("module", new BuildStepArgument(folder, Map.of()))))
                .toCompletableFuture()
                .join();

        assertThat(result.next()).isTrue();
        assertThat(next.resolve("demo.app/bin/java")).hasContent("launcher");
    }

    @Test
    public void stages_native_binary_under_native_key() throws IOException {
        Path folder = Files.createDirectory(source.resolve("module"));
        Path image = folder.resolve("native");
        Files.createDirectories(image);
        Files.writeString(image.resolve("demo.image"), "binary");
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module.native", "native");
        inventory.setProperty("module.artifact", "demo.app");
        inventory.store(folder.resolve(Inventory.INVENTORY));

        BuildStepResult result = new ImageStaging("native").apply(Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("module", new BuildStepArgument(folder, Map.of()))))
                .toCompletableFuture()
                .join();

        assertThat(result.next()).isTrue();
        assertThat(next.resolve("demo.app/demo.image")).hasContent("binary");
    }

    @Test
    public void two_runtime_images_do_not_merge() throws IOException {
        Path foo = runtimeImage("foo", "foo.app", "foo-java");
        Path bar = runtimeImage("bar", "bar.app", "bar-java");

        new ImageStaging("image").apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of(
                                "foo", new BuildStepArgument(foo, Map.of()),
                                "bar", new BuildStepArgument(bar, Map.of()))))
                .toCompletableFuture()
                .join();

        assertThat(next.resolve("foo.app/bin/java"))
                .as("a runtime image is a directory of fixed names, so each module needs a folder of its own")
                .hasContent("foo-java");
        assertThat(next.resolve("bar.app/bin/java"))
                .as("without a folder the second image would silently lose to the first")
                .hasContent("bar-java");
    }

    @Test
    public void a_folder_can_be_declined() throws IOException {
        Path folder = Files.createDirectory(source.resolve("module"));
        Path image = folder.resolve("runtime");
        Files.createDirectories(image.resolve("bin"));
        Files.writeString(image.resolve("bin").resolve("java"), "launcher");
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module.image", "runtime");
        inventory.setProperty("module.artifact", "demo.app");
        inventory.store(folder.resolve(Inventory.INVENTORY));

        new ImageStaging("image").noFolder(true).apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("module", new BuildStepArgument(folder, Map.of()))))
                .toCompletableFuture()
                .join();

        assertThat(next.resolve("bin/java")).hasContent("launcher");
    }

    @Test
    public void an_inventory_without_an_artifact_still_gets_a_folder() throws IOException {
        Path folder = Files.createDirectory(source.resolve("module"));
        Path image = folder.resolve("runtime");
        Files.createDirectories(image.resolve("bin"));
        Files.writeString(image.resolve("bin").resolve("java"), "launcher");
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module-sources.image", "runtime");
        inventory.store(folder.resolve(Inventory.INVENTORY));

        new ImageStaging("image").apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("module", new BuildStepArgument(folder, Map.of()))))
                .toCompletableFuture()
                .join();

        assertThat(next.resolve("module-sources/bin/java"))
                .as("falling back to flat would reinstate the merge the folder prevents")
                .hasContent("launcher");
    }

    @Test
    public void arguments_without_inventory_are_skipped() throws IOException {
        Path stray = Files.createDirectory(source.resolve("stray"));
        Files.createDirectories(stray.resolve("packages").resolve("app"));
        Files.writeString(stray.resolve("packages").resolve("app").resolve("file"), "x");

        BuildStepResult result = run(stray);

        assertThat(result.next()).isTrue();
        try (Stream<Path> stream = Files.walk(next)) {
            assertThat(stream.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private Path runtimeImage(String folderName, String artifact, String launcher) throws IOException {
        Path folder = Files.createDirectory(source.resolve(folderName));
        Path bin = Files.createDirectories(folder.resolve("runtime").resolve("bin"));
        Files.writeString(bin.resolve("java"), launcher);
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module-" + folderName + ".image", "runtime");
        inventory.setProperty("module-" + folderName + ".artifact", artifact);
        inventory.store(folder.resolve(Inventory.INVENTORY));
        return folder;
    }

    private Path stagedImage(String folderName, String appName, String launcher) throws IOException {
        Path folder = Files.createDirectory(source.resolve(folderName));
        Path bin = Files.createDirectories(folder.resolve("packages").resolve(appName).resolve("bin"));
        Files.writeString(bin.resolve(appName), launcher);
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module-" + folderName + ".package", "packages");
        inventory.setProperty("module-" + folderName + ".artifact", folderName);
        inventory.store(folder.resolve(Inventory.INVENTORY));
        return folder;
    }

    private BuildStepResult run(Path... inventoryFolders) throws IOException {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        for (Path folder : inventoryFolders) {
            arguments.put(folder.getFileName().toString(), new BuildStepArgument(folder, Map.of()));
        }
        return new ImageStaging("package").apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        arguments)
                .toCompletableFuture()
                .join();
    }
}
