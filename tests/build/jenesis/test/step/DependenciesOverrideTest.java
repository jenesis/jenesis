package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependenciesOverrideTest {

    @TempDir
    private Path root, mavenRepoFolder, work;
    private final SequencedMap<String, String> discovered = new LinkedHashMap<>();
    private Path previous, next, supplement, dependencies;

    @BeforeEach
    public void setUp() throws IOException {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        dependencies = Files.createDirectory(root.resolve("dependencies"));
    }

    @Test
    public void places_a_carrier_backed_module_of_the_overridden_name() throws IOException {
        modularLib("carrier-lib", "1.0", "lib.carrier", "shaded.api");
        module("lib.carrier", "org.example/carrier-lib");

        BuildStepResult result = resolve(Map.of("lib.shaded", "lib.carrier"), "lib.carrier", "lib.shaded");

        assertThat(result.next()).isTrue();
        Path placed = next.resolve(Dependencies.RESOLVED + "lib.shaded.jar");
        assertThat(placed).exists();
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(placed);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.name()).isEqualTo("lib.shaded");
        assertThat(descriptor.packages())
                .as("the placed module carries no packages of its own, only readability to the carrier")
                .isEmpty();
        assertThat(descriptor.requires())
                .anySatisfy(requires -> {
                    assertThat(requires.name()).isEqualTo("lib.carrier");
                    assertThat(requires.modifiers())
                            .contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE);
                });
        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .contains("main/compile/module/lib.shaded");
    }

    @Test
    public void drops_the_artifact_that_declares_the_overridden_module() throws IOException {
        modularLib("carrier-lib", "1.0", "lib.carrier", "shaded.api");
        modularLib("shaded-lib", "1.0", "lib.shaded", "shaded.api");
        module("lib.carrier", "org.example/carrier-lib");
        module("lib.shaded", "org.example/shaded-lib");

        resolve(Map.of("lib.shaded", "lib.carrier"), "lib.carrier", "lib.shaded");

        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .as("the artifact whose packages the carrier already ships never reaches the closure,"
                        + " so neither the module path nor the generated pom carries them twice")
                .doesNotContain("main/compile/maven/org.example/shaded-lib/1.0")
                .contains("main/compile/maven/org.example/carrier-lib/1.0");
    }

    @Test
    public void rejects_a_carrier_no_dependency_declares() throws IOException {
        modularLib("carrier-lib", "1.0", "lib.carrier", "shaded.api");
        module("lib.carrier", "org.example/carrier-lib");

        assertThatThrownBy(() -> resolve(Map.of("lib.shaded", "lib.absent"), "lib.carrier"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Module override lib.shaded declared by"
                        + " a local @jenesis.override declaration names lib.absent"
                        + " which no resolved dependency carries - require the carrier or drop the override");
    }

    @Test
    public void rejects_an_override_where_modules_do_not_resolve_to_coordinates() throws IOException {
        assertThatThrownBy(() -> new Dependencies(
                Map.of("module", discovery()),
                Map.of("module", new ModularJarResolver(false))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments(Map.of("lib.shaded", "lib.carrier"), "lib.carrier"))
                .toCompletableFuture()
                .join())
                .as("only a layout that maps module names onto Maven coordinates can drop the shaded artifact")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot override [lib.shaded]")
                .hasMessageContaining("jenesis.project.layout=modular_to_maven");
    }

    private void modularLib(String artifactId, String version, String module, String name) throws IOException {
        Path sources = Files.createDirectories(work.resolve(artifactId + "-sources"));
        Path classes = Files.createDirectories(work.resolve(artifactId + "-classes"));
        Path folder = Files.createDirectories(sources.resolve(name.replace('.', '/')));
        Files.writeString(folder.resolve("Value.java"), """
                package %s;
                public class Value {
                }
                """.formatted(name));
        Files.writeString(sources.resolve("module-info.java"), """
                module %s {
                    exports %s;
                }
                """.formatted(module, name));
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                "--module-version", version,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                folder.resolve("Value.java").toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        Files.writeString(Files
                        .createDirectories(mavenRepoFolder.resolve("org/example/" + artifactId + "/" + version))
                        .resolve(artifactId + "-" + version + ".pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                </project>
                """.formatted(artifactId, version));
        Path file = mavenRepoFolder.resolve("org/example/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file));
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

    private void module(String module, String coordinate) {
        discovered.put(module + ":pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0</version>
                </project>
                """.formatted(coordinate.split("/")[0], coordinate.split("/")[1]));
    }

    private Repository discovery() {
        return (_, coordinate) -> Optional.ofNullable(discovered.get(coordinate))
                .map(body -> (RepositoryItem) () -> new ByteArrayInputStream(
                        body.getBytes(StandardCharsets.UTF_8)));
    }

    private SequencedMap<String, BuildStepArgument> arguments(Map<String, String> overrides, String... modules)
            throws IOException {
        SequencedProperties requires = new SequencedProperties();
        for (String module : modules) {
            requires.setProperty("main/compile/module/" + module, "");
        }
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties declared = new SequencedProperties();
        overrides.forEach((module, carriers) -> declared.setProperty("main/module/" + module, carriers));
        declared.store(dependencies.resolve(BuildStep.OVERRIDES));
        return new LinkedHashMap<>(Map.of("dependencies", new BuildStepArgument(dependencies, new LinkedHashMap<>(
                Map.of(Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED),
                        Path.of(BuildStep.OVERRIDES), Checksum.of(ChecksumStatus.ADDED))))));
    }

    private BuildStepResult resolve(Map<String, String> overrides, String... modules) throws IOException {
        MavenDefaultRepository maven = new MavenDefaultRepository(
                mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {
        });
        return new Dependencies(
                Map.of("maven", maven, "module", discovery()),
                Map.of("module", new MavenModuleResolver("maven",
                        new MavenPomResolver(MavenDefaultVersionNegotiator.maven()),
                        discovery()))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments(overrides, modules))
                .toCompletableFuture()
                .join();
    }
}
