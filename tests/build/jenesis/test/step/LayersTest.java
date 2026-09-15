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
    public void isolates_a_declared_group_and_leaves_the_application_path_alone() throws IOException {
        modularJar(resolved, "org.example.renderer");
        modularJar(resolved, "org.example.lib");
        index("render/runtime/maven/org.example/renderer", "resolved/org.example.renderer.jar",
                "main/runtime/maven/org.example/lib", "resolved/org.example.lib.jar");
        declare("render", null);

        assertThat(apply().next()).isTrue();
        assertThat(layer("render")).containsExactly("org.example.renderer.jar");
    }

    @Test
    public void keeps_a_shared_module_out_of_the_layer() throws IOException {
        modularJar(resolved, "org.example.renderer", "org.slf4j");
        modularJar(resolved, "org.slf4j");
        index("render/runtime/maven/org.example/renderer", "resolved/org.example.renderer.jar",
                "render/runtime/maven/org.slf4j/slf4j-api", "resolved/org.slf4j.jar");
        declare("render", "org.slf4j");

        assertThat(apply().next()).isTrue();
        assertThat(layer("render"))
                .as("a shared module resolves from the parent layer, so it is not materialized in the layer")
                .containsExactly("org.example.renderer.jar");
    }

    @Test
    public void rejects_a_shared_module_that_reaches_an_isolated_module() throws IOException {
        modularJar(resolved, "org.example.renderer", "demo.api", "org.slf4j");
        modularJar(resolved, "org.slf4j");
        modularJar(artifacts, "demo.api", "org.slf4j");
        index("render/runtime/maven/org.example/renderer", "resolved/org.example.renderer.jar",
                "render/runtime/maven/org.slf4j/slf4j-api", "resolved/org.slf4j.jar");
        declare("render", null);

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("module demo.api is shared with layer render but reaches org.slf4j")
                .hasMessageContaining("demo.api -> org.slf4j")
                .hasMessageContaining("@jenesis.layer render shared org.slf4j");
    }

    @Test
    public void accepts_a_shared_module_whose_closure_avoids_the_layer() throws IOException {
        modularJar(resolved, "org.example.renderer", "demo.api", "org.slf4j");
        modularJar(resolved, "org.slf4j");
        modularJar(artifacts, "demo.api");
        index("render/runtime/maven/org.example/renderer", "resolved/org.example.renderer.jar",
                "render/runtime/maven/org.slf4j/slf4j-api", "resolved/org.slf4j.jar");
        declare("render", null);

        assertThat(apply().next()).isTrue();
        assertThat(layer("render")).containsExactly("org.example.renderer.jar", "org.slf4j.jar");
    }

    @Test
    public void rejects_a_jar_that_carries_no_module() throws IOException {
        plainJar(resolved.resolve("legacy.jar"));
        index("render/runtime/maven/org.example/legacy", "resolved/legacy.jar");
        declare("render", null);

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy.jar carries no module but is isolated in layer render")
                .hasMessageContaining("modules.properties");
    }

    @Test
    public void rejects_two_jars_carrying_one_module() throws IOException {
        modularJar(resolved, "org.example.renderer");
        Path duplicate = resolved.resolve("other.jar");
        Files.copy(resolved.resolve("org.example.renderer.jar"), duplicate);
        index("render/runtime/maven/org.example/renderer", "resolved/org.example.renderer.jar",
                "render/runtime/maven/org.example/renderer-other", "resolved/other.jar");
        declare("render", null);

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both carry module org.example.renderer in layer render");
    }

    @Test
    public void rejects_an_unexpected_declaration_entry() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("render/exposed", "org.slf4j");
        properties.store(input.resolve(BuildStep.LAYERS));

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("render/exposed")
                .hasMessageContaining("expected <layer> or <layer>/shared");
    }

    @Test
    public void rejects_a_layer_name_that_escapes_its_folder() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("..", "");
        properties.store(input.resolve(BuildStep.LAYERS));

        assertThatThrownBy(this::apply).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void does_nothing_without_a_declaration() throws IOException {
        modularJar(resolved, "org.example.lib");
        index("main/runtime/maven/org.example/lib", "resolved/org.example.lib.jar");

        assertThat(apply().next()).isTrue();
        assertThat(next.resolve(Layers.LAYER_PATH)).doesNotExist();
    }

    @Test
    public void rejects_a_layer_that_provides_a_contract_it_also_isolates() throws IOException {
        exportingJar(resolved, "demo.api", "demo.api");
        providingJar(resolved, "demo.impl", "demo.api", "demo.api.Greeter", "demo.impl.Impl");
        index("render/runtime/maven/demo/api", "resolved/demo.api.jar",
                "render/runtime/maven/demo/impl", "resolved/demo.impl.jar");
        declare("render", null);

        assertThatThrownBy(this::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("layer render provides demo.api.Greeter, but the module that declares it,"
                        + " demo.api, is isolated in the layer")
                .hasMessageContaining("@jenesis.layer render shared demo.api");
    }

    @Test
    public void accepts_a_layer_whose_contract_is_shared() throws IOException {
        exportingJar(resolved, "demo.api", "demo.api");
        providingJar(resolved, "demo.impl", "demo.api", "demo.api.Greeter", "demo.impl.Impl");
        index("render/runtime/maven/demo/api", "resolved/demo.api.jar",
                "render/runtime/maven/demo/impl", "resolved/demo.impl.jar");
        declare("render", "demo.api");

        assertThat(apply().next()).isTrue();
        assertThat(layer("render")).containsExactly("demo.impl.jar");
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

    private void declare(String layer, String shared) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty(layer, "");
        if (shared != null) {
            properties.setProperty(layer + "/shared", shared);
        }
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

    private static void modularJar(Path folder, String module, String... requires) throws IOException {
        write(folder, module, builder -> {
            builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
            for (String required : requires) {
                builder.requires(ModuleDesc.of(required), 0, null);
            }
        });
    }

    private static void exportingJar(Path folder, String module, String exported) throws IOException {
        write(folder, module, builder -> {
            builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
            builder.exports(PackageDesc.of(exported), 0);
        });
    }

    private static void providingJar(Path folder,
                                     String module,
                                     String required,
                                     String contract,
                                     String provider) throws IOException {
        write(folder, module, builder -> {
            builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
            builder.requires(ModuleDesc.of(required), 0, null);
            builder.provides(ModuleProvideInfo.of(ClassDesc.of(contract), List.of(ClassDesc.of(provider))));
        });
    }

    private static void write(Path folder, String module, Consumer<ModuleAttribute.ModuleAttributeBuilder> directives)
            throws IOException {
        byte[] descriptor = ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(module), directives::accept));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(folder.resolve(module + ".jar")))) {
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
