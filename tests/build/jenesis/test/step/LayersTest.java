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
import build.jenesis.step.Layers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LayersTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, input, resolved, artifacts;

    @BeforeEach
    public void setUp() throws IOException {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
        resolved = Files.createDirectory(input.resolve("resolved"));
        artifacts = Files.createDirectory(input.resolve(BuildStep.ARTIFACTS));
    }

    @Test
    public void isolates_a_layers_closure_and_shares_the_api_module() throws IOException {
        module(artifacts, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        module(resolved, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        module(resolved, "demo.impl", builder -> builder
                .requires(ModuleDesc.of("demo.api"), 0, null));
        index("layer:render/runtime/module/demo.impl", "resolved/demo.impl.jar",
                "layer:render/runtime/module/demo.api", "resolved/demo.api.jar");
        declare("render", "demo.api");

        assertThat(apply().next()).isTrue();
        assertThat(layer("render"))
                .as("the API module resolves from the host, so the layer never carries a second copy")
                .containsExactly("demo.impl.jar");
    }

    @Test
    public void shares_everything_the_api_module_reaches() throws IOException {
        module(artifacts, "demo.api", builder -> builder
                .exports(PackageDesc.of("demo.api"), 0)
                .requires(ModuleDesc.of("org.shared"), 0, null));
        module(artifacts, "org.shared", builder -> builder.exports(PackageDesc.of("org.shared"), 0));
        module(resolved, "org.shared", builder -> builder.exports(PackageDesc.of("org.shared"), 0));
        module(resolved, "demo.impl", builder -> builder
                .requires(ModuleDesc.of("demo.api"), 0, null));
        index("layer:render/runtime/module/demo.impl", "resolved/demo.impl.jar",
                "layer:render/runtime/module/org.shared", "resolved/org.shared.jar");
        declare("render", "demo.api");

        assertThat(apply().next()).isTrue();
        assertThat(layer("render"))
                .as("a library type the API module exposes is the same class on both sides, so it is shared")
                .containsExactly("demo.impl.jar");
    }

    @Test
    public void rejects_a_layer_that_isolates_nothing() throws IOException {
        module(artifacts, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        module(resolved, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        index("layer:render/runtime/module/demo.api", "resolved/demo.api.jar");
        declare("render", "demo.api");

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Layer render isolates nothing")
                .hasMessageContaining("already shared through demo.api");
    }

    @Test
    public void rejects_a_layer_that_provides_a_contract_it_also_holds() throws IOException {
        module(artifacts, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        module(resolved, "demo.impl", builder -> builder
                .exports(PackageDesc.of("demo.api"), 0)
                .provides(ModuleProvideInfo.of(ClassDesc.of("demo.api.Contract"),
                        List.of(ClassDesc.of("demo.impl.Impl")))));
        index("layer:render/runtime/module/demo.impl", "resolved/demo.impl.jar");
        declare("render", "demo.api");

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Layer render provides demo.api.Contract")
                .hasMessageContaining("but holds demo.impl, which declares it")
                .hasMessageContaining("belongs in demo.api");
    }

    @Test
    public void rejects_a_jar_that_carries_no_module() throws IOException {
        module(artifacts, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        plainJar(resolved.resolve("legacy.jar"));
        index("layer:render/runtime/maven/org.example/legacy", "resolved/legacy.jar");
        declare("render", "demo.api");

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy.jar carries no module but is isolated in layer render")
                .hasMessageContaining("modules.properties");
    }

    @Test
    public void rejects_two_jars_carrying_one_module() throws IOException {
        module(artifacts, "demo.api", builder -> builder.exports(PackageDesc.of("demo.api"), 0));
        module(resolved, "demo.impl", builder -> builder.exports(PackageDesc.of("demo.impl"), 0));
        Files.copy(resolved.resolve("demo.impl.jar"), resolved.resolve("other.jar"));
        index("layer:render/runtime/module/demo.impl", "resolved/demo.impl.jar",
                "layer:render/runtime/module/demo.impl-other", "resolved/other.jar");
        declare("render", "demo.api");

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both carry module demo.impl in layer render");
    }

    @Test
    public void does_nothing_without_a_declaration() throws IOException {
        module(resolved, "demo.impl", builder -> builder.exports(PackageDesc.of("demo.impl"), 0));
        index("main/runtime/module/demo.impl", "resolved/demo.impl.jar");

        assertThat(apply().next()).isTrue();
        assertThat(next.resolve(Layers.LAYER_PATH)).doesNotExist();
    }

    private BuildStepResult apply() throws IOException {
        SequencedMap<Path, Checksum> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(input)) {
            walk.filter(Files::isRegularFile).forEach(file ->
                    files.put(input.relativize(file), Checksum.of(ChecksumStatus.ADDED)));
        }
        return new Layers().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(input, files))))
                .toCompletableFuture()
                .join();
    }

    private void declare(String layer, String api) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty(layer, api);
        properties.store(input.resolve(BuildStep.LAYERS));
    }

    private void index(String... entries) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        for (int index = 0; index < entries.length; index += 2) {
            properties.setProperty(entries[index], entries[index + 1]);
        }
        properties.store(input.resolve(BuildStep.DEPENDENCIES));
    }

    private SequencedSet<String> layer(String name) throws IOException {
        SequencedSet<String> files = new TreeSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(next.resolve(Layers.LAYER_PATH).resolve(name))) {
            for (Path file : stream) {
                files.add(file.getFileName().toString());
            }
        }
        return files;
    }

    private static void module(Path folder,
                               String name,
                               Consumer<ModuleAttribute.ModuleAttributeBuilder> directives) throws IOException {
        byte[] descriptor = ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(name), builder -> {
            builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
            directives.accept(builder);
        }));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(folder.resolve(name + ".jar")))) {
            jar.putNextEntry(new JarEntry("module-info.class"));
            jar.write(descriptor);
            jar.closeEntry();
        }
    }

    private static void plainJar(Path file) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            jar.putNextEntry(new JarEntry("legacy/Legacy.class"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
    }
}
