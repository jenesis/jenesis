package build.jenesis.test.maven;

import module java.base;
import build.jenesis.DependencyScope;
import module org.junit.jupiter.api;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenModuleResolverTest {

    @TempDir
    private Path mavenRepoFolder;

    private MavenPomResolver mavenPomResolver;

    @BeforeEach
    public void setUp() {
        mavenPomResolver = new MavenPomResolver(MavenDefaultVersionNegotiator.maven());
    }

    @Test
    public void resolves_from_unpinned_discovery_pom() throws IOException {
        addJarToMavenRepository("org.example", "example-core", "1.2.3");
        Map<String, String> fetched = new LinkedHashMap<>();
        Repository discovery = stubRepository(fetched, Map.of("foo.bar:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.2.3</version>
                </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsOnlyKeys("maven/org.example/example-core/1.2.3", "module/foo.bar/1.2.3");
        assertThat(resolved.get("maven/org.example/example-core/1.2.3").checksum()).isEmpty();
        assertThat(fetched).containsOnlyKeys("foo.bar:pom");
    }

    @Test
    public void rejects_a_jar_that_declares_a_different_module() throws IOException {
        addModuleJarToMavenRepository("org.example", "example-core", "1.2.3", "evil.other");
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of("foo.bar:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.2.3</version>
                </project>"""));

        assertThatThrownBy(() -> new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected module foo.bar")
                .hasMessageContaining("evil.other");
    }

    @Test
    public void accepts_a_jar_that_declares_the_requested_module() throws IOException {
        addModuleJarToMavenRepository("org.example", "example-core", "1.2.3", "foo.bar");
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of("foo.bar:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.2.3</version>
                </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsKey("module/foo.bar/1.2.3");
    }

    @Test
    public void pinned_version_forces_versioned_fetch() throws IOException {
        addJarToMavenRepository("org.example", "example-core", "9.9");
        Map<String, String> fetched = new LinkedHashMap<>();
        Repository discovery = stubRepository(fetched, Map.of("foo.bar/9.9:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>9.9</version>
                </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("foo.bar", "9.9")),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsOnlyKeys("maven/org.example/example-core/9.9", "module/foo.bar/9.9");
        assertThat(fetched).containsOnlyKeys("foo.bar/9.9:pom");
    }

    @Test
    public void threads_pinned_checksum_into_resolved_root() throws IOException, NoSuchAlgorithmException {
        String checksum = "SHA-256/" + addJarToMavenRepository("org.example", "example-core", "1.0");
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of("foo.bar/1.0:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.0</version>
                </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("foo.bar", "1.0 " + checksum)),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsOnlyKeys("maven/org.example/example-core/1.0", "module/foo.bar/1.0");
        assertThat(resolved.get("maven/org.example/example-core/1.0").checksum()).isEqualTo(checksum);
    }

    @Test
    public void walks_transitives_via_maven_repository() throws IOException {
        addToMavenRepository("org.transitive", "lib", "2.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.transitive</groupId>
                    <artifactId>lib</artifactId>
                    <version>2.0</version>
                </project>""");
        addJarToMavenRepository("org.example", "example-core", "1.0");
        addJarToMavenRepository("org.transitive", "lib", "2.0");
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of("foo.bar:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.transitive</groupId>
                            <artifactId>lib</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsOnlyKeys(
                "maven/org.example/example-core/1.0",
                "maven/org.transitive/lib/2.0",
                "module/foo.bar/1.0");
    }

    @Test
    public void does_not_refetch_root_pom_from_maven_repository() throws IOException {
        addJarToMavenRepository("org.example", "example-core", "1.0");
        Map<String, String> fetched = new LinkedHashMap<>();
        Repository discovery = stubRepository(fetched, Map.of("foo.bar:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.0</version>
                </project>"""));

        new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);

        assertThat(Files.exists(mavenRepoFolder.resolve("org.example/example-core/1.0/example-core-1.0.pom"))).isFalse();
    }

    @Test
    public void throws_when_discovery_pom_is_missing() {
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of());

        assertThatThrownBy(() -> new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No POM found for foo.bar");
    }

    @Test
    public void rejects_exclusions_on_module_coordinates() {
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of());

        assertThatThrownBy(() -> new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", new LinkedHashSet<>(List.of("org.x/y")))),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exclusions");
    }

    @Test
    public void does_not_hoist_declared_module_dependency_management() throws IOException {
        addToMavenRepository("org.mid", "mid", "1.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.mid</groupId>
                    <artifactId>mid</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.transitive</groupId>
                            <artifactId>lib</artifactId>
                            <version>2.0</version>
                        </dependency>
                    </dependencies>
                </project>""");
        addToMavenRepository("org.transitive", "lib", "2.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.transitive</groupId>
                    <artifactId>lib</artifactId>
                    <version>2.0</version>
                </project>""");
        addJarToMavenRepository("org.example", "example-core", "1.0");
        addJarToMavenRepository("org.mid", "mid", "1.0");
        addJarToMavenRepository("org.transitive", "lib", "2.0");
        Repository discovery = stubRepository(new LinkedHashMap<>(), Map.of("foo.bar:pom", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.example</groupId>
                    <artifactId>example-core</artifactId>
                    <version>1.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.transitive</groupId>
                                <artifactId>lib</artifactId>
                                <version>1.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.mid</groupId>
                            <artifactId>mid</artifactId>
                            <version>1.0</version>
                        </dependency>
                    </dependencies>
                </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsOnlyKeys(
                "maven/org.example/example-core/1.0",
                "maven/org.mid/mid/1.0",
                "maven/org.transitive/lib/2.0",
                "module/foo.bar/1.0");
    }

    @Test
    public void applies_non_declared_pin_as_dependency_management() throws IOException {
        addToMavenRepository("org.mid", "mid", "1.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.mid</groupId>
                    <artifactId>mid</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.transitive</groupId>
                            <artifactId>lib</artifactId>
                            <version>1.0</version>
                        </dependency>
                    </dependencies>
                </project>""");
        addToMavenRepository("org.transitive", "lib", "2.0", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>org.transitive</groupId>
                    <artifactId>lib</artifactId>
                    <version>2.0</version>
                </project>""");
        addJarToMavenRepository("org.example", "example-core", "1.0");
        addJarToMavenRepository("org.mid", "mid", "1.0");
        addJarToMavenRepository("org.transitive", "lib", "2.0");
        Map<String, String> fetched = new LinkedHashMap<>();
        Repository discovery = stubRepository(fetched, Map.of(
                "foo.bar:pom", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <groupId>org.example</groupId>
                            <artifactId>example-core</artifactId>
                            <version>1.0</version>
                            <dependencies>
                                <dependency>
                                    <groupId>org.mid</groupId>
                                    <artifactId>mid</artifactId>
                                    <version>1.0</version>
                                </dependency>
                            </dependencies>
                        </project>""",
                "lib.module/2.0:pom", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <groupId>org.transitive</groupId>
                            <artifactId>lib</artifactId>
                            <version>2.0</version>
                        </project>"""));

        SequencedMap<String, Resolver.Resolved> resolved = new MavenModuleResolver("maven", mavenPomResolver, discovery).dependencies(
                Runnable::run,
                "module",
                Map.of("maven", new MavenDefaultRepository(mavenRepoFolder.toUri(), mavenRepoFolder, Map.of(), _ -> {})),
                new LinkedHashMap<>(Map.of("foo.bar", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("lib.module", "2.0")),
                DependencyScope.COMPILE).artifacts();

        assertThat(resolved).containsOnlyKeys(
                "maven/org.example/example-core/1.0",
                "maven/org.mid/mid/1.0",
                "maven/org.transitive/lib/2.0",
                "module/foo.bar/1.0");
        assertThat(fetched).containsOnlyKeys("foo.bar:pom", "lib.module/2.0:pom");
    }

    @Test
    public void bom_floats_to_discovery_pom_version() throws IOException {
        Path properties = Files.writeString(mavenRepoFolder.resolve("acme.platform-2.0.properties"), "bar = 2.0\n");
        Map<String, String> fetched = new LinkedHashMap<>();
        Repository repository = (_, coordinate) -> {
            fetched.put(coordinate, "");
            return switch (coordinate) {
                case "acme.platform:pom" -> Optional.of((RepositoryItem) () -> new ByteArrayInputStream("""
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <groupId>org.example</groupId>
                            <artifactId>example-platform</artifactId>
                            <version>2.0</version>
                        </project>""".getBytes(StandardCharsets.UTF_8)));
                case "acme.platform/2.0:properties" -> Optional.of(RepositoryItem.ofFile(properties));
                default -> Optional.empty();
            };
        };
        Resolver.Bom bom = new MavenModuleResolver("maven", mavenPomResolver, null).bom(
                Runnable::run,
                "module",
                Map.of("module", repository),
                "acme.platform",
                "1.0",
                null,
                true);
        assertThat(bom.verifiable()).isTrue();
        assertThat(bom.version()).isEqualTo("2.0");
        assertThat(bom.entries()).containsExactly(Map.entry("module/bar", "2.0"));
        assertThat(fetched).containsOnlyKeys("acme.platform:pom", "acme.platform/2.0:properties");
    }

    @Test
    public void bom_without_discovery_pom_keeps_declared_version() throws IOException {
        Path properties = Files.writeString(mavenRepoFolder.resolve("acme.platform-1.0.properties"), "bar = 1.0\n");
        Repository repository = (_, coordinate) -> switch (coordinate) {
            case "acme.platform/1.0:properties" -> Optional.of(RepositoryItem.ofFile(properties));
            default -> Optional.empty();
        };
        Resolver.Bom bom = new MavenModuleResolver("maven", mavenPomResolver, null).bom(
                Runnable::run,
                "module",
                Map.of("module", repository),
                "acme.platform",
                "1.0",
                null,
                true);
        assertThat(bom.version()).isEqualTo("1.0");
        assertThat(bom.entries()).containsExactly(Map.entry("module/bar", "1.0"));
    }

    private static Repository stubRepository(Map<String, String> fetched, Map<String, String> bodies) {
        return (_, coordinate) -> {
            fetched.put(coordinate, "");
            String body = bodies.get(coordinate);
            if (body == null) {
                return Optional.empty();
            }
            return Optional.of((RepositoryItem) () -> new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        };
    }

    private void addToMavenRepository(String groupId, String artifactId, String version, String pom) throws IOException {
        Files.writeString(Files
                .createDirectories(mavenRepoFolder.resolve(groupId.replace('.', '/') + "/" + artifactId + "/" + version))
                .resolve(artifactId + "-" + version + ".pom"), pom);
    }

    private void addModuleJarToMavenRepository(String groupId, String artifactId, String version, String module) throws IOException {
        Path jar = Files.createDirectories(mavenRepoFolder.resolve(groupId.replace('.', '/') + "/" + artifactId + "/" + version))
                .resolve(artifactId + "-" + version + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("module-info.class"));
            out.write(ClassFile.of().buildModule(ModuleAttribute.of(
                    ModuleDesc.of(module),
                    builder -> builder.requires(ModuleRequireInfo.of(ModuleDesc.of("java.base"), 0, null)))));
            out.closeEntry();
        }
    }

    private String addJarToMavenRepository(String groupId, String artifactId, String version) throws IOException {
        byte[] content = (groupId + ":" + artifactId + ":" + version).getBytes(StandardCharsets.UTF_8);
        Files.write(Files
                .createDirectories(mavenRepoFolder.resolve(groupId.replace('.', '/') + "/" + artifactId + "/" + version))
                .resolve(artifactId + "-" + version + ".jar"), content);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
