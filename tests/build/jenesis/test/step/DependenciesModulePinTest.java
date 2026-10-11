package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependenciesModulePinTest {

    @TempDir
    private Path root, mavenRepoFolder, work;
    private final SequencedMap<String, String> discovered = new LinkedHashMap<>();
    private Path dependencies, build;

    @BeforeEach
    public void setUp() throws IOException {
        dependencies = Files.createDirectory(root.resolve("dependencies"));
        build = Files.createDirectory(root.resolve("build"));
        modularLib("leaf-lib", "1.0", "lib.leaf", "leaf.api", "");
        modularLib("leaf-lib", "2.0", "lib.leaf", "leaf.api", "");
        modularLib("root-lib", "1.0", "lib.root", "root.api", """
                <dependencies>
                    <dependency>
                        <groupId>org.example</groupId>
                        <artifactId>leaf-lib</artifactId>
                        <version>1.0</version>
                    </dependency>
                </dependencies>
                """);
        discovered.put("lib.root:pom", Files.readString(
                mavenRepoFolder.resolve("org/example/root-lib/1.0/root-lib-1.0.pom")));
        discovered.put("lib.leaf/2.0:pom", Files.readString(
                mavenRepoFolder.resolve("org/example/leaf-lib/2.0/leaf-lib-2.0.pom")));
    }

    @Test
    public void rejects_a_module_name_pin_for_a_module_a_pom_brings_in_under_its_coordinate() throws IOException {
        assertThatThrownBy(() -> resolve(null, Map.of("main/module/lib.leaf", "2.0"), "lib.root"))
                .hasStackTraceContaining(IllegalArgumentException.class.getName())
                .hasStackTraceContaining("@jenesis.pin lib.leaf 2.0 matches no dependency")
                .hasStackTraceContaining("org.example/leaf-lib in version 1.0")
                .hasStackTraceContaining("@jenesis.pin org.example/leaf-lib 2.0");
    }

    @Test
    public void rejects_a_misplaced_module_name_pin_whatever_the_pinning_mode_that_applies_pins() throws IOException {
        assertThatThrownBy(() -> resolve(Pinning.VERSIONS, Map.of("main/module/lib.leaf", "2.0"), "lib.root"))
                .hasStackTraceContaining("@jenesis.pin lib.leaf 2.0 matches no dependency");
    }

    @Test
    public void applies_the_coordinate_pin_the_failure_names() throws IOException {
        Path next = resolve(null, Map.of("main/maven/org.example/leaf-lib", "2.0"), "lib.root");

        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .contains("main/compile/maven/org.example/leaf-lib/2.0")
                .doesNotContain("main/compile/maven/org.example/leaf-lib/1.0");
    }

    @Test
    public void accepts_a_module_name_pin_on_a_required_module() throws IOException {
        Path next = resolve(null, Map.of("main/module/lib.leaf", "2.0"), "lib.root", "lib.leaf");

        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .contains("main/compile/maven/org.example/leaf-lib/2.0");
    }

    @Test
    public void accepts_a_module_name_pin_whose_version_the_closure_resolves_anyway() throws IOException {
        Path next = resolve(null, Map.of("main/module/lib.leaf", "1.0"), "lib.root");

        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .contains("main/compile/maven/org.example/leaf-lib/1.0");
    }

    @Test
    public void ignored_pins_are_not_checked() throws IOException {
        Path next = resolve(Pinning.IGNORE, Map.of("main/module/lib.leaf", "2.0"), "lib.root");

        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .contains("main/compile/maven/org.example/leaf-lib/1.0");
    }

    private void modularLib(String artifactId,
                            String version,
                            String module,
                            String name,
                            String dependencies) throws IOException {
        Path sources = Files.createDirectories(work.resolve(artifactId + "-" + version + "-sources"));
        Path classes = Files.createDirectories(work.resolve(artifactId + "-" + version + "-classes"));
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
                "--module-version",
                version,
                "-d",
                classes.toString(),
                sources.resolve("module-info.java").toString(),
                folder.resolve("Value.java").toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        Path target = Files.createDirectories(mavenRepoFolder.resolve("org/example/" + artifactId + "/" + version));
        Files.writeString(target.resolve(artifactId + "-" + version + ".pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                    %s
                </project>
                """.formatted(artifactId, version, dependencies));
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(
                target.resolve(artifactId + "-" + version + ".jar")));
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

    private Repository discovery() {
        return (_, coordinate, _) -> Optional.ofNullable(discovered.get(coordinate))
                .map(body -> (RepositoryItem) () -> new ByteArrayInputStream(
                        body.getBytes(StandardCharsets.UTF_8)));
    }

    private Path resolve(Pinning pinning, Map<String, String> pins, String... modules) throws IOException {
        SequencedProperties requires = new SequencedProperties();
        for (String module : modules) {
            requires.setProperty("main/compile/module/" + module, "");
        }
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties versions = new SequencedProperties();
        pins.forEach(versions::setProperty);
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
        MavenDefaultRepository maven = new MavenDefaultRepository(
                mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), null);
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("dependencies", dependencies);
        executor.addModule("resolved", new Dependencies(
                Map.of("maven", maven, "module", discovery()),
                Map.of("module", new MavenModuleResolver("maven",
                        new MavenPomResolver(MavenDefaultVersionNegotiator.maven()),
                        discovery()))).pinning(pinning), "dependencies");
        return executor.execute().get("resolved");
    }
}
