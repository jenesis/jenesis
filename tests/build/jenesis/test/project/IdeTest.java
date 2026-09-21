package build.jenesis.test.project;

import module java.base;
import module java.xml;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Json;
import build.jenesis.SequencedProperties;
import build.jenesis.project.Ide;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;

public class IdeTest {

    @TempDir
    private Path root;
    private Path next, supplement;

    @BeforeEach
    public void setUp() throws IOException {
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
    }

    @Test
    public void idea_writes_iml_and_modules_xml() throws IOException {
        Files.createDirectories(root.resolve("greeter").resolve("sources"));
        Path inventory = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.dependency.0", "maven/org.example/lib/1.0 lib/lib.jar");
        });
        Path jar = library(inventory, "lib.jar");

        run(Ide.IDEA, inventory);

        Path iml = root.resolve("greeter").resolve("greeter.iml");
        assertThat(sourceFolders(iml)).containsExactly(Map.entry("file://$MODULE_DIR$/sources", false));
        assertThat(libraryUrls(iml)).containsExactly("jar://$MODULE_DIR$/" + relative("greeter", jar) + "!/");
        assertThat(modulePaths()).containsExactly("$PROJECT_DIR$/greeter/greeter.iml");
    }

    @Test
    public void idea_resolves_libraries_below_the_module_directory() throws IOException {
        Files.createDirectories(root.resolve("nested").resolve("greeter").resolve("sources"));
        Path inventory = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "nested/greeter");
            properties.setProperty("module-greeter.dependency.0", "maven/org.example/lib/1.0 lib/lib.jar");
        });
        library(inventory, "lib.jar");

        run(Ide.IDEA, inventory);

        Path iml = root.resolve("nested").resolve("greeter").resolve("nested.greeter.iml");
        assertThat(Files.readString(iml))
                .as("IntelliJ expands only $MODULE_DIR$ within a module file")
                .doesNotContain("$PROJECT_DIR$");
        assertThat(libraryUrls(iml)).allSatisfy(url -> assertThat(url).startsWith("jar://$MODULE_DIR$/../../"));
    }

    @Test
    public void idea_compiles_below_the_target_folder() throws IOException {
        Files.createDirectories(root.resolve("greeter").resolve("sources"));
        Path inventory = inventory("module-greeter", properties ->
                properties.setProperty("module-greeter.path", "greeter"));

        run(Ide.IDEA, inventory);

        assertThat(attributes(root.resolve(".idea").resolve("misc.xml"), "output", "url"))
                .as("the IDE compiles into the folder the build already owns")
                .containsExactly("file://$PROJECT_DIR$/target/.idea");
    }

    @Test
    public void idea_links_sibling_module_instead_of_jar() throws IOException {
        Files.createDirectories(root.resolve("greeter"));
        Files.createDirectories(root.resolve("app"));
        Path greeter = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.identity.0", "maven/org.example/greeter/1.0");
        });
        Path app = inventory("module-app", properties -> {
            properties.setProperty("module-app.path", "app");
            properties.setProperty("module-app.dependency.0", "maven/org.example/greeter/1.0 lib/greeter.jar");
        });

        run(Ide.IDEA, greeter, app);

        Path iml = root.resolve("app").resolve("app.iml");
        assertThat(moduleDependencies(iml)).containsExactly("greeter");
        assertThat(libraryUrls(iml)).isEmpty();
    }

    @Test
    public void idea_names_module_by_jpms_module_property() throws IOException {
        Files.createDirectories(root.resolve("greeter"));
        Files.createDirectories(root.resolve("app"));
        Path greeter = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.module", "org.example.greeter");
            properties.setProperty("module-greeter.identity.0", "maven/org.example/greeter/1.0");
        });
        Path app = inventory("module-app", properties -> {
            properties.setProperty("module-app.path", "app");
            properties.setProperty("module-app.dependency.0", "maven/org.example/greeter/1.0 lib/greeter.jar");
        });

        run(Ide.IDEA, greeter, app);

        assertThat(root.resolve("greeter").resolve("org.example.greeter.iml")).exists();
        assertThat(modulePaths()).contains("$PROJECT_DIR$/greeter/org.example.greeter.iml");
        assertThat(moduleDependencies(root.resolve("app").resolve("app.iml")))
                .containsExactly("org.example.greeter");
    }

    @Test
    public void idea_keeps_colliding_basenames_distinct() throws IOException {
        Files.createDirectories(root.resolve("store").resolve("spi"));
        Files.createDirectories(root.resolve("format").resolve("spi"));
        Files.createDirectories(root.resolve("app"));
        Path store = inventory("module-store", properties -> {
            properties.setProperty("module-store.path", "store/spi");
            properties.setProperty("module-store.module", "app.store");
            properties.setProperty("module-store.identity.0", "maven/org.example/store/1.0");
        });
        Path format = inventory("module-format", properties -> {
            properties.setProperty("module-format.path", "format/spi");
            properties.setProperty("module-format.module", "app.format");
            properties.setProperty("module-format.identity.0", "maven/org.example/format/1.0");
        });
        Path app = inventory("module-app", properties -> {
            properties.setProperty("module-app.path", "app");
            properties.setProperty("module-app.dependency.0", "maven/org.example/store/1.0 lib/store.jar");
            properties.setProperty("module-app.dependency.1", "maven/org.example/format/1.0 lib/format.jar");
        });

        run(Ide.IDEA, store, format, app);

        assertThat(moduleDependencies(root.resolve("app").resolve("app.iml")))
                .containsExactly("app.store", "app.format");
        assertThat(root.resolve("store").resolve("spi").resolve("app.store.iml")).exists();
        assertThat(root.resolve("format").resolve("spi").resolve("app.format.iml")).exists();
        assertThat(modulePaths()).contains(
                "$PROJECT_DIR$/store/spi/app.store.iml",
                "$PROJECT_DIR$/format/spi/app.format.iml");
    }

    @Test
    public void idea_marks_a_test_module_as_test_sources() throws IOException {
        Files.createDirectories(root.resolve("greeter-test").resolve("sources"));
        Path inventory = inventory("module-greeter-test", properties -> {
            properties.setProperty("module-greeter-test.path", "greeter-test");
            properties.setProperty("module-greeter-test.test", "demo.greeter");
        });

        run(Ide.IDEA, inventory);

        assertThat(sourceFolders(root.resolve("greeter-test").resolve("greeter-test.iml")))
                .containsExactly(Map.entry("file://$MODULE_DIR$/sources", true));
    }

    @Test
    public void idea_treats_an_abstract_test_module_as_production_sources() throws IOException {
        Files.createDirectories(root.resolve("greeter-testing").resolve("sources"));
        Path inventory = inventory("module-greeter-testing", abstractTestModule());

        run(Ide.IDEA, inventory);

        assertThat(sourceFolders(root.resolve("greeter-testing").resolve("greeter-testing.iml")))
                .as("a fixture module is read by other modules, so it is not a test source root")
                .containsExactly(Map.entry("file://$MODULE_DIR$/sources", false));
    }

    @Test
    public void eclipse_treats_an_abstract_test_module_as_production_sources() throws IOException {
        Files.createDirectories(root.resolve("greeter-testing").resolve("sources"));
        Path inventory = inventory("module-greeter-testing", abstractTestModule());

        run(Ide.ECLIPSE, inventory);

        Path classpath = root.resolve("greeter-testing").resolve(".classpath");
        assertThat(classpathEntries(classpath, "src")).containsExactly("sources");
        assertThat(Files.readString(classpath)).doesNotContain("<attribute name=\"test\" value=\"true\"/>");
    }

    @ParameterizedTest
    @ValueSource(strings = {Ide.IDEA, Ide.VSCODE, Ide.ECLIPSE})
    public void omits_a_build_tool_group_from_the_libraries(String tool) throws IOException {
        Files.createDirectories(root.resolve("greeter").resolve("sources"));
        Path inventory = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.dependency.0", "maven/org.example/lib/1.0 lib/lib.jar");
            properties.setProperty("module-greeter.dependency.0.group", "main");
            properties.setProperty("module-greeter.dependency.1",
                    "maven/com.puppycrawl.tools/checkstyle/13.5.0 lib/checkstyle.jar");
            properties.setProperty("module-greeter.dependency.1.group", "checkstyle");
        });
        library(inventory, "lib.jar");
        library(inventory, "checkstyle.jar");

        run(tool, inventory);

        assertThat(libraries(tool))
                .as("a build tool resolves in its own group and never reaches the module's classpath")
                .hasSize(1)
                .allSatisfy(library -> assertThat(library).contains("lib.jar"));
    }

    @ParameterizedTest
    @ValueSource(strings = {Ide.IDEA, Ide.VSCODE, Ide.ECLIPSE})
    public void omits_a_pom_typed_dependency_from_the_libraries(String tool) throws IOException {
        Files.createDirectories(root.resolve("greeter").resolve("sources"));
        Path inventory = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.dependency.0", "maven/org.example/lib/1.0 lib/lib.jar");
            properties.setProperty("module-greeter.dependency.1",
                    "maven/org.example/parent/pom/1.0 lib/parent.jar");
        });
        library(inventory, "lib.jar");
        library(inventory, "parent.jar");

        run(tool, inventory);

        assertThat(libraries(tool))
                .as("a POM carries no classes, so it is not a library")
                .hasSize(1)
                .allSatisfy(library -> assertThat(library).contains("lib.jar"));
    }

    @Test
    public void eclipse_writes_project_and_classpath() throws IOException {
        Files.createDirectories(root.resolve("greeter").resolve("sources"));
        Files.createDirectories(root.resolve("greeter").resolve("test"));
        Path inventory = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.dependency.0", "maven/org.example/lib/1.0 lib/lib.jar");
        });
        Path jar = library(inventory, "lib.jar");

        run(Ide.ECLIPSE, inventory);

        assertThat(Files.readString(root.resolve("greeter").resolve(".project")))
                .contains("<name>greeter</name>", "org.eclipse.jdt.core.javanature");
        Path classpath = root.resolve("greeter").resolve(".classpath");
        assertThat(classpathEntries(classpath, "src")).containsExactly("sources", "test");
        assertThat(classpathEntries(classpath, "lib")).containsExactly(slash(jar.toAbsolutePath().normalize()));
        assertThat(classpathEntries(classpath, "con")).containsExactly("org.eclipse.jdt.launching.JRE_CONTAINER");
        assertThat(Files.readString(classpath)).contains("<attribute name=\"test\" value=\"true\"/>");
    }

    @Test
    public void vscode_writes_settings() throws IOException {
        Files.createDirectories(root.resolve("greeter").resolve("sources"));
        Path inventory = inventory("module-greeter", properties -> {
            properties.setProperty("module-greeter.path", "greeter");
            properties.setProperty("module-greeter.dependency.0", "maven/org.example/lib/1.0 lib/lib.jar");
        });
        library(inventory, "lib.jar");

        run(Ide.VSCODE, inventory);

        assertThat(sourcePaths()).containsExactly("greeter/sources");
        assertThat(referencedLibraries()).containsExactly("out/module-greeter/lib/lib.jar");
    }

    @Test
    public void falls_back_to_module_folder_when_no_source_dir() throws IOException {
        Files.createDirectories(root.resolve("greeter"));
        Path inventory = inventory("module-greeter", properties ->
                properties.setProperty("module-greeter.path", "greeter"));

        run(Ide.IDEA, inventory);

        assertThat(sourceFolders(root.resolve("greeter").resolve("greeter.iml")))
                .containsExactly(Map.entry("file://$MODULE_DIR$", false));
    }

    private static Consumer<SequencedProperties> abstractTestModule() {
        return properties -> {
            properties.setProperty("module-greeter-testing.path", "greeter-testing");
            properties.setProperty("module-greeter-testing.test", "");
            properties.setProperty("module-greeter-testing.abstract", "true");
        };
    }

    private List<String> libraries(String tool) throws IOException {
        return switch (tool) {
            case Ide.IDEA -> libraryUrls(root.resolve("greeter").resolve("greeter.iml"));
            case Ide.VSCODE -> referencedLibraries();
            case Ide.ECLIPSE -> classpathEntries(root.resolve("greeter").resolve(".classpath"), "lib");
            default -> throw new IllegalArgumentException(tool);
        };
    }

    private List<String> modulePaths() throws IOException {
        return attributes(root.resolve(".idea").resolve("modules.xml"), "module", "filepath");
    }

    private List<String> sourcePaths() throws IOException {
        return settings("java.project.sourcePaths");
    }

    private List<String> referencedLibraries() throws IOException {
        return settings("java.project.referencedLibraries");
    }

    private List<String> settings(String key) throws IOException {
        List<String> values = new ArrayList<>();
        if (Json.parse(Files.readString(root.resolve(".vscode").resolve("settings.json")))
                instanceof Map<?, ?> settings && settings.get(key) instanceof List<?> entries) {
            for (Object entry : entries) {
                values.add(entry.toString());
            }
        }
        return values;
    }

    private String relative(String module, Path target) {
        return slash(root.resolve(module).toAbsolutePath().normalize()
                .relativize(target.toAbsolutePath().normalize()));
    }

    private static List<String> libraryUrls(Path file) throws IOException {
        return attributes(file, "root", "url");
    }

    private static SequencedMap<String, Boolean> sourceFolders(Path file) throws IOException {
        SequencedMap<String, Boolean> folders = new LinkedHashMap<>();
        for (Element element : elements(file, "sourceFolder")) {
            folders.put(element.getAttribute("url"), Boolean.parseBoolean(element.getAttribute("isTestSource")));
        }
        return folders;
    }

    private static List<String> moduleDependencies(Path file) throws IOException {
        List<String> names = new ArrayList<>();
        for (Element element : elements(file, "orderEntry")) {
            if (element.getAttribute("type").equals("module")) {
                names.add(element.getAttribute("module-name"));
            }
        }
        return names;
    }

    private static List<String> classpathEntries(Path file, String kind) throws IOException {
        List<String> paths = new ArrayList<>();
        for (Element element : elements(file, "classpathentry")) {
            if (element.getAttribute("kind").equals(kind)) {
                paths.add(element.getAttribute("path"));
            }
        }
        return paths;
    }

    private static List<String> attributes(Path file, String tag, String attribute) throws IOException {
        List<String> values = new ArrayList<>();
        for (Element element : elements(file, tag)) {
            values.add(element.getAttribute(attribute));
        }
        return values;
    }

    private static List<Element> elements(Path file, String tag) throws IOException {
        Document document;
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(file + " is not well-formed XML", e);
        }
        NodeList nodes = document.getElementsByTagName(tag);
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            elements.add((Element) nodes.item(index));
        }
        return elements;
    }

    private static String slash(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    private static Path library(Path inventory, String name) throws IOException {
        Path jar = Files.createDirectories(inventory.resolve("lib")).resolve(name);
        Files.writeString(jar, "library");
        return jar;
    }

    private Path inventory(String prefix, Consumer<SequencedProperties> values) throws IOException {
        Path folder = Files.createDirectories(root.resolve("out").resolve(prefix));
        SequencedProperties inventory = new SequencedProperties();
        values.accept(inventory);
        inventory.store(folder.resolve(Inventory.INVENTORY));
        return folder;
    }

    private void run(String tool, Path... inventories) throws IOException {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        int index = 0;
        for (Path inventory : inventories) {
            arguments.put("inventory-" + index++, new BuildStepArgument(inventory, Map.of()));
        }
        BuildStep step = switch (tool) {
            case Ide.IDEA -> new Ide.Idea(root);
            case Ide.VSCODE -> new Ide.VsCode(root);
            case Ide.ECLIPSE -> new Ide.Eclipse(root);
            default -> throw new IllegalArgumentException(tool);
        };
        BuildStepResult result = step.apply(Runnable::run,
                        new BuildStepContext(root.resolve("previous"), next, supplement),
                        arguments)
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();
    }
}
