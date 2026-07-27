package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import java.util.jar.Attributes;
import build.jenesis.DependencyScope;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.maven.MavenAliasResolver;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the module-path delivery of an alias whose target jar carries <b>no module identity</b> - no
 * {@code module-info.class} and no {@code Automatic-Module-Name} manifest attribute. Such a target
 * lands on the class path, which no named module can read, so the alias jar must be a copy of the
 * target carrying the alias as its {@code Automatic-Module-Name}: {@link PathPlacement#moduleDescriptor}
 * then yields an automatic descriptor (it yielded {@code null} for the identity-less artifact alone,
 * leaving {@code requires <alias>} without the target's packages), the alias module carries the
 * target's own packages, and a named module requiring the alias compiles against them. A target that
 * already owns a module identity keeps the former shape - an empty alias jar beside the untouched
 * target - and the copy drops signature entries and any package another resolved module already
 * exports onto the module path.
 */
public class MavenAliasResolverModulePathTest {

    @TempDir
    private Path mavenRepoFolder;

    @TempDir
    private Path work;

    private Resolver resolver;

    @BeforeEach
    public void setUp() {
        resolver = new MavenAliasResolver("maven", new MavenModuleResolver("maven",
                new MavenPomResolver(MavenDefaultVersionNegotiator.maven()),
                (_, _) -> Optional.empty()));
    }

    @Test
    public void identity_less_alias_target_is_delivered_as_a_module_path_automatic_module_named_by_the_alias()
            throws IOException {
        addPom("org.example", "plain-lib", "1.0", List.of());
        addJar("org/example/plain-lib/1.0/plain-lib-1.0.jar", null, Map.of(
                "toollib/Lib.class", new byte[]{1, 2, 3},
                "toollib/lib.properties", new byte[]{4, 5, 6}));

        Resolver.Resolution resolution = resolve("toolkit.lib", "org.example/plain-lib 1.0");

        Path alias = resolution.artifacts().get("module/toolkit.lib").file();
        // The placement gate that used to fail: the alias artifact now carries a module identity...
        ModuleDescriptor placed = PathPlacement.moduleDescriptor(alias);
        assertThat(placed).isNotNull();
        assertThat(placed.isAutomatic()).isTrue();
        assertThat(placed.name()).isEqualTo("toolkit.lib");
        assertThat(PathPlacement.INFERRED.test(alias)).isTrue();
        // ...and the module named by the alias carries the target's own packages and resources.
        ModuleDescriptor descriptor = ModuleFinder.of(alias).findAll().iterator().next().descriptor();
        assertThat(descriptor.name()).isEqualTo("toolkit.lib");
        assertThat(descriptor.isAutomatic()).isTrue();
        assertThat(descriptor.packages()).contains("toollib");
        assertThat(entries(alias)).contains("toollib/Lib.class", "toollib/lib.properties");
        // The target itself is delivered untouched and stays a class path artifact.
        Path target = resolution.artifacts().get("maven/org.example/plain-lib/1.0").file();
        assertThat(PathPlacement.moduleDescriptor(target)).isNull();
        assertThat(PathPlacement.INFERRED.test(target)).isFalse();
    }

    @Test
    public void named_module_requiring_an_identity_less_alias_compiles_against_the_target_packages()
            throws IOException {
        Path libClasses = compile("plain", "toollib/Lib.java", """
                package toollib;
                public class Lib {
                    public static String value() {
                        return "from-lib";
                    }
                }
                """);
        addPom("org.example", "plain-lib", "1.0", List.of());
        jarOf(Files.createDirectories(mavenRepoFolder.resolve("org/example/plain-lib/1.0"))
                .resolve("plain-lib-1.0.jar"), libClasses);

        Resolver.Resolution resolution = resolve("toolkit.lib", "org.example/plain-lib 1.0");

        Path sources = Files.createDirectories(work.resolve("app-sources"));
        Files.writeString(sources.resolve("module-info.java"), """
                module my.app {
                    requires toolkit.lib;
                }
                """);
        Files.createDirectories(sources.resolve("myapp"));
        Files.writeString(sources.resolve("myapp/Main.java"), """
                package myapp;
                public class Main {
                    public static String read() {
                        return toollib.Lib.value();
                    }
                }
                """);
        // The jars split the way PathPlacement.INFERRED places them in a build: artifacts with a
        // module identity onto the module path, the identity-less rest onto the class path. Before
        // the alias jar carried the target's packages, this compile failed with the target's package
        // declared in the unnamed module, unreadable from my.app.
        Map<Boolean, String> paths = resolution.artifacts().values().stream()
                .map(Resolver.Resolved::file)
                .collect(Collectors.partitioningBy(
                        file -> PathPlacement.moduleDescriptor(file) != null,
                        Collectors.mapping(Path::toString, Collectors.joining(File.pathSeparator))));
        List<String> arguments = new ArrayList<>(List.of(
                "-d", Files.createDirectories(work.resolve("app-classes")).toString(),
                "-p", paths.get(true)));
        if (!paths.get(false).isEmpty()) {
            arguments.addAll(List.of("-cp", paths.get(false)));
        }
        arguments.add(sources.resolve("module-info.java").toString());
        arguments.add(sources.resolve("myapp/Main.java").toString());
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                arguments.toArray(String[]::new));
        assertThat(result).withFailMessage(() -> "Compilation failed: " + errors).isEqualTo(0);
    }

    @Test
    public void amn_carrying_alias_target_keeps_the_empty_alias_jar_and_the_untouched_target() throws IOException {
        addPom("org.example", "amn-lib", "1.0", List.of());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", "lib.target");
        addJar("org/example/amn-lib/1.0/amn-lib-1.0.jar", manifest, Map.of(
                "foo/Amn.class", new byte[]{1, 2, 3}));

        Resolver.Resolution resolution = resolve("toolkit.amn", "org.example/amn-lib 1.0");

        // The former shape, byte for byte: an empty alias jar named by the alias...
        Path alias = resolution.artifacts().get("module/toolkit.amn").file();
        assertThat(entries(alias)).containsExactly("META-INF/MANIFEST.MF");
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(alias);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.isAutomatic()).isTrue();
        assertThat(descriptor.name()).isEqualTo("toolkit.amn");
        // ...beside the untouched target, module-path-visible under its own name.
        Path target = resolution.artifacts().get("maven/org.example/amn-lib/1.0").file();
        assertThat(entries(target)).containsExactlyInAnyOrder("META-INF/MANIFEST.MF", "foo/Amn.class");
        assertThat(PathPlacement.moduleDescriptor(target).name()).isEqualTo("lib.target");
    }

    @Test
    public void alias_copy_drops_signature_entries_and_packages_another_resolved_module_exports() throws IOException {
        // The target duplicates a package that other.mod - resolved into the same closure - exports.
        addPom("org.example", "other-lib", "1.0", List.of());
        Manifest otherManifest = new Manifest();
        otherManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        otherManifest.getMainAttributes().putValue("Automatic-Module-Name", "other.mod");
        addJar("org/example/other-lib/1.0/other-lib-1.0.jar", otherManifest, Map.of(
                "shared/Dup.class", new byte[]{1, 2, 3}));
        addPom("org.example", "fat-lib", "1.0", List.of("org.example/other-lib/1.0"));
        addJar("org/example/fat-lib/1.0/fat-lib-1.0.jar", null, Map.of(
                "toollib/Lib.class", new byte[]{1, 2, 3},
                "shared/Dup.class", new byte[]{7, 8, 9},
                "META-INF/FAKE.SF", new byte[]{1},
                "META-INF/FAKE.RSA", new byte[]{2}));

        Resolver.Resolution resolution = resolve("toolkit.fat", "org.example/fat-lib 1.0");

        Path alias = resolution.artifacts().get("module/toolkit.fat").file();
        assertThat(PathPlacement.moduleDescriptor(alias).name()).isEqualTo("toolkit.fat");
        assertThat(entries(alias))
                .contains("toollib/Lib.class")
                .doesNotContain("shared/Dup.class", "META-INF/FAKE.SF", "META-INF/FAKE.RSA");
    }

    private Resolver.Resolution resolve(String alias, String declaration) throws IOException {
        return resolver.dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of(alias, Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(Map.of(alias, declaration)),
                DependencyScope.COMPILE);
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
                .createDirectories(mavenRepoFolder.resolve(groupId.replace('.', '/') + "/" + artifactId + "/" + version))
                .resolve(artifactId + "-" + version + ".pom"), pom.toString());
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

    private void jarOf(Path file, Path classes) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file));
             Stream<Path> stream = Files.walk(classes)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace(File.separatorChar, '/')));
                output.write(Files.readAllBytes(path));
                output.closeEntry();
            }
        }
    }
}
