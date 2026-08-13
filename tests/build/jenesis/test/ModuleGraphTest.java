package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import java.util.jar.Attributes;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleGraphTest {

    @TempDir
    private Path folder;

    @Test
    public void a_graph_of_explicit_modules_needs_no_relaxation() throws IOException {
        ModuleGraph graph = new ModuleGraph();
        graph.module(named("explicit.one"));
        graph.module(named("explicit.two"));

        assertThat(graph.arguments()).isEmpty();
        assertThat(graph.options()).isEmpty();
    }

    @Test
    public void an_automatic_module_roots_the_whole_module_path() throws IOException {
        ModuleGraph graph = new ModuleGraph();
        graph.module(named("explicit.one"));
        graph.module(automatic("auto.two"));

        assertThat(graph.arguments())
                .as("an automatic module is only resolved when the module path is rooted wholesale")
                .containsExactly("--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT");
    }

    @Test
    public void a_class_path_entry_roots_the_module_path() throws IOException {
        ModuleGraph graph = new ModuleGraph();
        graph.module(named("explicit.one"));
        graph.unnamed();

        assertThat(graph.arguments())
                .as("a jar written for the class path expects the platform a -m launch does not root")
                .containsExactly("--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT");
        assertThat(graph.options()).containsExactly("--add-modules=ALL-MODULE-PATH,ALL-DEFAULT");
    }

    @Test
    public void a_class_path_without_a_module_path_relaxes_nothing() {
        ModuleGraph graph = new ModuleGraph();
        graph.unnamed();

        assertThat(graph.arguments())
                .as("a launch that has no module at all resolves nothing that could need rooting")
                .isEmpty();
    }

    @Test
    public void placement_routes_a_module_to_the_module_path_and_the_rest_to_the_class_path()
            throws IOException {
        Path module = automatic("auto.one"), plain = plain();
        List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
        ModuleGraph graph = new ModuleGraph();
        graph.place(PathPlacement.INFERRED, module, modulePath, classPath);
        graph.place(PathPlacement.INFERRED, plain, modulePath, classPath);

        assertThat(modulePath).containsExactly(module.toString());
        assertThat(classPath)
                .as("a jar that declares no module name is not made one by the name of its file")
                .containsExactly(plain.toString());
        assertThat(graph.arguments()).containsExactly("--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT");
    }

    @Test
    public void placement_routes_an_aliased_jar_to_the_module_path() throws IOException {
        Path aliased = plain(), target = folder.resolve("toolkit.lib.jar");
        Files.move(aliased, target);
        Files.writeString(PathPlacement.declaration(target), "toolkit.lib");
        List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
        ModuleGraph graph = new ModuleGraph();
        graph.place(PathPlacement.INFERRED, target, modulePath, classPath);

        assertThat(modulePath)
                .as("the build declared this file name, so the module name it derives is intended")
                .containsExactly(target.toString());
        assertThat(classPath).isEmpty();
    }

    @Test
    public void a_folder_without_a_descriptor_is_not_an_automatic_module() throws IOException {
        Path classes = Files.createDirectories(folder.resolve("classes"));
        List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
        ModuleGraph graph = new ModuleGraph();
        graph.place(PathPlacement.MODULE_PATH, classes, modulePath, classPath);

        assertThat(modulePath).containsExactly(classes.toString());
        assertThat(graph.arguments())
                .as("a folder of classes is a source of packages, not a jar with a derivable module name")
                .isEmpty();
    }

    @Test
    public void options_render_as_single_tokens_a_property_can_carry() throws IOException {
        ModuleGraph graph = new ModuleGraph();
        graph.module(automatic("auto.one"));

        assertThat(graph.options()).containsExactly("--add-modules=ALL-MODULE-PATH,ALL-DEFAULT");
    }

    @Test
    public void a_relaxed_graph_is_stored_and_read_back() throws IOException {
        ModuleGraph graph = new ModuleGraph();
        graph.module(automatic("auto.one"));
        SequencedProperties properties = new SequencedProperties();
        graph.store(properties);

        assertThat(properties.getProperty(ModuleGraph.JAVA_OPTIONS))
                .isEqualTo("--add-modules=ALL-MODULE-PATH,ALL-DEFAULT");
        assertThat(ModuleGraph.load(properties))
                .containsExactly("--add-modules=ALL-MODULE-PATH,ALL-DEFAULT");
    }

    @Test
    public void a_self_contained_graph_stores_nothing() throws IOException {
        ModuleGraph graph = new ModuleGraph();
        graph.module(named("explicit.one"));
        SequencedProperties properties = new SequencedProperties();
        graph.store(properties);

        assertThat(properties.stringPropertyNames())
                .as("an absent key is what tells a consumer to launch the graph as it stands")
                .isEmpty();
        assertThat(ModuleGraph.load(properties)).isEmpty();
    }

    @Test
    public void a_legacy_boolean_is_read_when_no_options_are_stored() {
        Properties relaxed = new Properties();
        relaxed.setProperty("selfContainedModuleGraph", "false");
        Properties contained = new Properties();
        contained.setProperty("selfContainedModuleGraph", "true");

        assertThat(ModuleGraph.load(relaxed))
                .as("an installation written before javaOptions existed still gets its module path rooted")
                .containsExactly("--add-modules=ALL-MODULE-PATH");
        assertThat(ModuleGraph.load(contained)).isEmpty();
    }

    private Path named(String name) throws IOException {
        Path sources = Files.createDirectories(folder.resolve(name + "-sources"));
        Path classes = Files.createDirectories(folder.resolve(name + "-classes"));
        Files.createDirectories(sources.resolve("shared"));
        Files.writeString(sources.resolve("shared/Type.java"), "package shared;\npublic class Type {\n}\n");
        Files.writeString(sources.resolve("module-info.java"), "module " + name + " {\n}\n");
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("shared/Type.java").toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        Path file = folder.resolve(name + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
            for (String entry : List.of("module-info.class", "shared/Type.class")) {
                output.putNextEntry(new JarEntry(entry));
                output.write(Files.readAllBytes(classes.resolve(entry)));
                output.closeEntry();
            }
        }
        return file;
    }

    private Path automatic(String name) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", name);
        Path file = folder.resolve(name + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            output.putNextEntry(new JarEntry("auto/Type.class"));
            output.write(new byte[]{1, 2, 3});
            output.closeEntry();
        }
        return file;
    }

    private Path plain() throws IOException {
        Path file = folder.resolve("plain.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new JarEntry("plain/Type.class"));
            output.write(new byte[]{1, 2, 3});
            output.closeEntry();
        }
        return file;
    }
}
