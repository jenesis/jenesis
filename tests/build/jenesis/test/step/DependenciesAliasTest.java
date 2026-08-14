package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import java.lang.module.Configuration;
import java.util.jar.Attributes;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependenciesAliasTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    private Path root;
    @TempDir
    private Path mavenRepoFolder, work;
    private Path previous, next, supplement, dependencies;

    @BeforeEach
    public void setUp() throws IOException {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        dependencies = Files.createDirectory(root.resolve("dependencies"));
    }

    @Test
    public void renames_an_identity_less_target_to_the_declared_module_name() throws IOException {
        plainLib();
        Path original = mavenRepoFolder.resolve("org/example/plain-lib/1.0/plain-lib-1.0.jar");
        byte[] bytes = Files.readAllBytes(original);

        BuildStepResult result = resolve(Map.of("toolkit.lib", "org.example/plain-lib"),
                "org.example/plain-lib/1.0");

        assertThat(result.next()).isTrue();
        Path aliased = next.resolve(Dependencies.RESOLVED + "toolkit.lib.jar");
        assertThat(aliased).exists();
        assertThat(Files.readAllBytes(aliased))
                .as("a pinned checksum keeps describing the jar on the command line, so only the name changes")
                .isEqualTo(bytes);
        assertThat(entries(aliased))
                .as("nothing is stripped, not even signature entries, as the target is never rewritten")
                .contains("toollib/Lib.class", "META-INF/FAKE.SF");
        assertThat(SequencedProperties.ofFiles(next.resolve(Dependencies.ALIASED))
                .getProperty("toolkit.lib"))
                .isEqualTo("maven/org.example/plain-lib/1.0");
        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES))
                .getProperty("main/compile/maven/org.example/plain-lib/1.0"))
                .startsWith(Dependencies.RESOLVED + "toolkit.lib.jar");
    }

    @Test
    public void places_a_renamed_target_on_the_module_path() throws IOException {
        plainLib();

        resolve(Map.of("toolkit.lib", "org.example/plain-lib"), "org.example/plain-lib/1.0");

        Path aliased = next.resolve(Dependencies.RESOLVED + "toolkit.lib.jar");
        assertThat(PathPlacement.INFERRED.test(aliased))
                .as("a jar named for a module the build declared belongs on the module path")
                .isTrue();
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(aliased);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.isAutomatic()).isTrue();
        assertThat(descriptor.name()).isEqualTo("toolkit.lib");
    }

    @Test
    public void an_aliased_target_is_a_module_that_compiles_and_runs_under_its_name() throws Exception {
        plainLib();

        resolve(Map.of("toolkit.lib", "org.example/plain-lib"), "org.example/plain-lib/1.0");

        Path aliased = next.resolve(Dependencies.RESOLVED + "toolkit.lib.jar");
        Path classes = app(aliased, """
                module my.app {
                    exports myapp;
                    requires toolkit.lib;
                }
                """, """
                package myapp;
                public class Main {
                    public static String read() {
                        return toollib.Lib.value();
                    }
                }
                """);
        ModuleFinder finder = ModuleFinder.of(classes, aliased);
        Configuration configuration = ModuleLayer.boot().configuration().resolve(
                finder,
                ModuleFinder.of(),
                Set.of("my.app"));
        ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(
                configuration,
                List.of(ModuleLayer.boot()),
                ClassLoader.getSystemClassLoader()).layer();

        assertThat(layer.findLoader("my.app").loadClass("myapp.Main").getMethod("read").invoke(null))
                .as("the module name is derived from the file name, so javac and java agree on it")
                .isEqualTo("from-lib");
    }

    @Test
    public void reuses_a_renamed_jar_from_a_previous_resolution() throws IOException {
        plainLib();
        resolve(Map.of("toolkit.lib", "org.example/plain-lib"), "org.example/plain-lib/1.0");
        Files.delete(mavenRepoFolder.resolve("org/example/plain-lib/1.0/plain-lib-1.0.jar"));
        previous = next;
        next = Files.createDirectory(root.resolve("second"));

        BuildStepResult result = resolve(Map.of("toolkit.lib", "org.example/plain-lib"),
                "org.example/plain-lib/1.0");

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Dependencies.RESOLVED + "toolkit.lib.jar"))
                .as("a jar renamed by a previous resolution is found again under the name it was given")
                .exists();
    }

    @Test
    public void a_classified_target_is_matched_by_its_token() throws IOException {
        addPom("org.example", "native-lib", "1.0", List.of());
        addJar("org/example/native-lib/1.0/native-lib-1.0-linux.jar", null, Map.of(
                "nativelib/Native.class", new byte[]{1, 2, 3}));

        resolve(Map.of("toolkit.natives", "org.example/native-lib/jar/linux"),
                "org.example/native-lib/jar/linux/1.0");

        assertThat(next.resolve(Dependencies.RESOLVED + "toolkit.natives.jar"))
                .as("a type and a classifier are part of the token a declaration matches")
                .exists();
    }

    @Test
    public void an_alias_that_names_no_resolved_dependency_is_rejected() throws IOException {
        plainLib();

        assertThatThrownBy(() -> resolve(Map.of("toolkit.absent", "org.example/absent-lib"),
                "org.example/plain-lib/1.0"))
                .as("an alias renames a jar the tree already holds, it never adds one")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Module alias toolkit.absent declared by a local @jenesis.alias declaration"
                        + " does not name a resolved dependency: org.example/absent-lib"
                        + " - require the target or drop the alias");
    }

    @Test
    public void an_alias_target_carrying_an_automatic_module_name_is_rejected() throws IOException {
        addPom("org.example", "amn-lib", "1.0", List.of());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", "lib.target");
        addJar("org/example/amn-lib/1.0/amn-lib-1.0.jar", manifest, Map.of(
                "amnlib/Amn.class", new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> resolve(Map.of("toolkit.amn", "org.example/amn-lib"),
                "org.example/amn-lib/1.0"))
                .as("a target that already names itself is required under that name")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target of module alias toolkit.amn is already the automatic module lib.target"
                        + " - require lib.target instead of aliasing org.example/amn-lib");
    }

    @Test
    public void an_alias_target_declaring_a_module_is_rejected() throws IOException {
        Path classes = compile("named", "module-info.java", """
                module lib.named {
                }
                """);
        addPom("org.example", "named-lib", "1.0", List.of());
        jarOf(Files.createDirectories(mavenRepoFolder.resolve("org/example/named-lib/1.0"))
                .resolve("named-lib-1.0.jar"), classes, null);

        assertThatThrownBy(() -> resolve(Map.of("toolkit.named", "org.example/named-lib"),
                "org.example/named-lib/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target of module alias toolkit.named is already the named module lib.named");
    }

    @Test
    public void an_alias_colliding_with_a_resolved_module_is_rejected() throws IOException {
        plainLib();
        Path classes = compile("named", "module-info.java", """
                module toolkit.lib {
                }
                """);
        addPom("org.example", "named-lib", "1.0", List.of());
        jarOf(Files.createDirectories(mavenRepoFolder.resolve("org/example/named-lib/1.0"))
                .resolve("named-lib-1.0.jar"), classes, null);

        assertThatThrownBy(() -> resolve(Map.of("toolkit.lib", "org.example/plain-lib"),
                "org.example/plain-lib/1.0",
                "org.example/named-lib/1.0"))
                .as("two modules of one name cannot share a module path")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Module alias toolkit.lib collides with module toolkit.lib resolved from"
                        + " maven/org.example/named-lib/1.0 - require it directly");
    }

    @Test
    public void two_aliases_for_one_target_are_rejected() throws IOException {
        plainLib();

        assertThatThrownBy(() -> resolve(new LinkedHashMap<>(Map.of(
                        "toolkit.lib", "org.example/plain-lib",
                        "toolkit.other", "org.example/plain-lib")),
                "org.example/plain-lib/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is aliased as both")
                .hasMessageContaining("a jar can carry only one module name");
    }

    @Test
    public void an_alias_declared_by_a_dependency_renames_the_target() throws IOException {
        plainLib();
        Path classes = compile("consumer", "module-info.java", """
                module lib.consumer {
                }
                """);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(PathPlacement.ALIASES, "toolkit.lib=org.example/plain-lib");
        addPom("org.example", "consumer-lib", "1.0", List.of("org.example/plain-lib/1.0"));
        jarOf(Files.createDirectories(mavenRepoFolder.resolve("org/example/consumer-lib/1.0"))
                .resolve("consumer-lib-1.0.jar"), classes, manifest);

        BuildStepResult result = resolve(Map.of(), "org.example/consumer-lib/1.0");

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Dependencies.RESOLVED + "toolkit.lib.jar"))
                .as("a declaration travels to every consumer through the manifest of the jar that made it")
                .exists();
    }

    @Test
    public void an_alias_a_dependency_declares_for_another_target_is_rejected() throws IOException {
        plainLib();
        addPom("org.example", "other-lib", "1.0", List.of());
        addJar("org/example/other-lib/1.0/other-lib-1.0.jar", null, Map.of(
                "otherlib/Other.class", new byte[]{1, 2, 3}));
        Path classes = compile("consumer", "module-info.java", """
                module lib.consumer {
                }
                """);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(PathPlacement.ALIASES, "toolkit.lib=org.example/other-lib");
        addPom("org.example", "consumer-lib", "1.0", List.of(
                "org.example/plain-lib/1.0",
                "org.example/other-lib/1.0"));
        jarOf(Files.createDirectories(mavenRepoFolder.resolve("org/example/consumer-lib/1.0"))
                .resolve("consumer-lib-1.0.jar"), classes, manifest);

        assertThatThrownBy(() -> resolve(Map.of("toolkit.lib", "org.example/plain-lib"),
                "org.example/consumer-lib/1.0"))
                .as("one module name cannot describe two jars, wherever the two declarations come from")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Module alias toolkit.lib is declared for org.example/plain-lib"
                        + " by a local @jenesis.alias declaration and for org.example/other-lib by");
    }

    private void plainLib() throws IOException {
        Path classes = compile("plain", "toollib/Lib.java", """
                package toollib;
                public class Lib {
                    public static String value() {
                        return "from-lib";
                    }
                }
                """);
        addPom("org.example", "plain-lib", "1.0", List.of());
        Path file = Files.createDirectories(mavenRepoFolder.resolve("org/example/plain-lib/1.0"))
                .resolve("plain-lib-1.0.jar");
        jarOf(file, classes, null);
        try (FileSystem system = FileSystems.newFileSystem(file)) {
            Files.createDirectories(system.getPath("META-INF"));
            Files.write(system.getPath("META-INF/FAKE.SF"), new byte[]{7});
        }
    }

    private BuildStepResult resolve(Map<String, String> aliases, String... coordinates) throws IOException {
        SequencedProperties requires = new SequencedProperties();
        for (String coordinate : coordinates) {
            requires.setProperty("main/compile/maven/" + coordinate, "");
        }
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        Map<Path, Checksum> changed = new LinkedHashMap<>(Map.of(
                Path.of(BuildStep.REQUIRES),
                Checksum.of(ChecksumStatus.ADDED)));
        if (!aliases.isEmpty()) {
            SequencedProperties declared = new SequencedProperties();
            aliases.forEach((alias, token) -> declared.setProperty("main/maven/" + alias, token));
            declared.store(dependencies.resolve(BuildStep.ALIASES));
            changed.put(Path.of(BuildStep.ALIASES), Checksum.of(ChecksumStatus.ADDED));
        }
        return new Dependencies(
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {
                })),
                Map.of("maven", new MavenPomResolver(MavenDefaultVersionNegotiator.maven()))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("dependencies", new BuildStepArgument(dependencies, changed))))
                .toCompletableFuture()
                .join();
    }

    private Path app(Path modulePath, String moduleInfo, String main) throws IOException {
        Path sources = Files.createDirectories(work.resolve("app-sources"));
        Path classes = Files.createDirectories(work.resolve("app-classes"));
        Files.writeString(sources.resolve("module-info.java"), moduleInfo);
        Files.createDirectories(sources.resolve("myapp"));
        Files.writeString(sources.resolve("myapp/Main.java"), main);
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                "-d", classes.toString(),
                "-p", modulePath.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("myapp/Main.java").toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        return classes;
    }

    private void addPom(String groupId, String artifactId, String version, List<String> dependencies)
            throws IOException {
        StringBuilder pom = new StringBuilder("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                """.formatted(groupId, artifactId, version));
        if (!dependencies.isEmpty()) {
            pom.append("    <dependencies>\n");
            for (String dependency : dependencies) {
                String[] elements = dependency.split("/");
                pom.append("""
                                <dependency>
                                    <groupId>%s</groupId>
                                    <artifactId>%s</artifactId>
                                    <version>%s</version>
                                </dependency>
                        """.formatted(elements[0], elements[1], elements[2]));
            }
            pom.append("    </dependencies>\n");
        }
        pom.append("</project>");
        Files.writeString(Files
                        .createDirectories(mavenRepoFolder.resolve(
                                groupId.replace('.', '/') + "/" + artifactId + "/" + version))
                        .resolve(artifactId + "-" + version + ".pom"),
                pom.toString());
    }

    private void addJar(String path, Manifest manifest, Map<String, byte[]> entries) throws IOException {
        Path file = mavenRepoFolder.resolve(path);
        Files.createDirectories(file.getParent());
        try (JarOutputStream output = manifest == null
                ? new JarOutputStream(Files.newOutputStream(file))
                : new JarOutputStream(Files.newOutputStream(file), manifest)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private List<String> entries(Path file) throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            return jar.stream().map(JarEntry::getName).filter(name -> !name.endsWith("/")).toList();
        }
    }

    private Path compile(String name, String file, String source) throws IOException {
        Path sources = Files.createDirectories(work.resolve(name + "-sources"));
        Path classes = Files.createDirectories(work.resolve(name + "-classes"));
        Path java = sources.resolve(file);
        Files.createDirectories(java.getParent());
        Files.writeString(java, source);
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                "-d", classes.toString(),
                java.toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        return classes;
    }

    private void jarOf(Path file, Path classes, Manifest manifest) throws IOException {
        try (JarOutputStream output = manifest == null
                ? new JarOutputStream(Files.newOutputStream(file))
                : new JarOutputStream(Files.newOutputStream(file), manifest);
             Stream<Path> stream = Files.walk(classes)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path)
                        .toString()
                        .replace(File.separatorChar, '/')));
                output.write(Files.readAllBytes(path));
                output.closeEntry();
            }
        }
    }
}
