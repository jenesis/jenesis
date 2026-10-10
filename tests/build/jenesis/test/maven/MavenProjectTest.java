package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.PathPlacement;
import build.jenesis.Platform;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenProject;
import build.jenesis.maven.MavenRepository;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.JavaToolchainModule;
import build.jenesis.step.Bind;
import build.jenesis.step.Versions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

public class MavenProjectTest {

    @TempDir
    private Path project, build, repository;
    private MavenRepository mavenRepository;
    private MavenPomResolver mavenPomResolver;

    @BeforeEach
    public void setUp() throws Exception {
        mavenRepository = new MavenDefaultRepository(repository.toUri(),
                null,
                Map.of(),
                null);
        mavenPomResolver = new MavenPomResolver(MavenDefaultVersionNegotiator.maven());
    }

    @Test
    public void infers_no_subproject_from_a_folder_marked_to_be_skipped() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.1.0">
                    <modelVersion>4.1.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                </project>
                """);
        for (String name : List.of("kept", "skipped")) {
            Path subproject = Files.createDirectory(project.resolve(name));
            Files.writeString(Files.createDirectories(subproject.resolve("src/main/java")).resolve("source"), "foo");
            Files.writeString(subproject.resolve("pom.xml"), """
                    <project xmlns="http://maven.apache.org/POM/4.1.0">
                        <modelVersion>4.1.0</modelVersion>
                        <parent>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </parent>
                        <artifactId>%s</artifactId>
                    </project>
                    """.formatted(name));
        }
        Files.createFile(project.resolve("skipped").resolve(BuildExecutor.SKIP_MARKER));
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKey("maven/module-kept/manifests");
        assertThat(results.keySet())
                .as("a folder carrying the skip marker is not discovered as a subproject")
                .noneMatch(key -> key.contains("skipped"));
    }

    @Test
    public void skips_a_listed_module_whose_folder_is_marked_to_be_skipped() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>kept</module>
                        <module>skipped</module>
                    </modules>
                </project>
                """);
        for (String name : List.of("kept", "skipped")) {
            Path subproject = Files.createDirectory(project.resolve(name));
            Files.writeString(Files.createDirectories(subproject.resolve("src/main/java")).resolve("source"), "foo");
            Files.writeString(subproject.resolve("pom.xml"), """
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </parent>
                        <artifactId>%s</artifactId>
                    </project>
                    """.formatted(name));
        }
        Files.createFile(project.resolve("skipped").resolve(BuildExecutor.SKIP_MARKER));
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKey("maven/module-kept/manifests");
        assertThat(results.keySet())
                .as("a module the aggregator lists is left out when its folder carries the skip marker")
                .noneMatch(key -> key.contains("skipped"));
    }

    @Test
    public void names_the_module_entry_that_points_at_a_folder_without_a_pom() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>missing</module>
                    </modules>
                </project>
                """);
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<module>missing</module>")
                .hasMessageContaining("no pom.xml");
    }

    @Test
    public void can_resolve_pom() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("maven/module-/manifests",
                "maven/module-/coordinates",
                "maven/test-module-/manifests",
                "maven/test-module-/coordinates");
        Path module = results.get("maven/module-/manifests");
        assertThat(module.resolve(BuildStep.IDENTITY)).doesNotExist();
        Path moduleCoordinates = results.get("maven/module-/coordinates");
        SequencedProperties coordinates = SequencedProperties.ofFiles(moduleCoordinates.resolve(BuildStep.IDENTITY));
        assertThat(coordinates).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/group/artifact/pom/1");
        assertThat(coordinates.getProperty("maven/group/artifact/1")).isEmpty();
        Path moduleRequires = module.resolve(BuildStep.REQUIRES);
        assertThat(moduleRequires).exists();
        SequencedProperties dependencies = SequencedProperties.ofFiles(moduleRequires);
        assertThat(dependencies).containsOnlyKeys("main/compile/maven/other/artifact/1", "main/runtime/maven/other/artifact/1");
        assertThat(dependencies.getProperty("main/compile/maven/other/artifact/1")).isEmpty();
        Path testModule = results.get("maven/test-module-/manifests");
        assertThat(testModule.resolve(BuildStep.IDENTITY)).doesNotExist();
        Path testModuleCoordinates = results.get("maven/test-module-/coordinates");
        SequencedProperties testCoordinates = SequencedProperties.ofFiles(testModuleCoordinates.resolve(BuildStep.IDENTITY));
        assertThat(testCoordinates).containsOnlyKeys(
                "maven/group/artifact/jar/tests/1",
                "maven/group/artifact/pom/1");
        assertThat(testCoordinates.getProperty("maven/group/artifact/jar/tests/1")).isEmpty();
        SequencedProperties testModuleProperties = SequencedProperties.ofFiles(testModule.resolve(BuildStep.MODULE));
        assertThat(testModuleProperties.getProperty("test")).isEqualTo("artifact");
        Path testModuleRequires = testModule.resolve(BuildStep.REQUIRES);
        assertThat(testModuleRequires).exists();
        SequencedProperties testDependencies = SequencedProperties.ofFiles(testModuleRequires);
        assertThat(testDependencies).containsOnlyKeys(
                "main/compile/maven/other/artifact/1",
                "main/runtime/maven/other/artifact/1",
                "main/compile/maven/group/artifact/1",
                "main/runtime/maven/group/artifact/1");
        assertThat(testDependencies.getProperty("main/compile/maven/group/artifact/1")).isEmpty();
    }

    @Test
    public void attach_of_declared_dependency_seeds_versioned_agent_requires() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.mockito/mockito-core
                    -->
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>5.11.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        for (String manifests : List.of("maven/module-/manifests", "maven/test-module-/manifests")) {
            Path module = results.get(manifests);
            SequencedProperties requires = SequencedProperties.ofFiles(module.resolve(BuildStep.REQUIRES));
            assertThat(requires.stringPropertyNames())
                    .contains("main/runtime/maven/org.mockito/mockito-core/5.11.0");
            assertThat(requires.stringPropertyNames())
                    .filteredOn(key -> key.contains("org.mockito"))
                    .containsExactlyInAnyOrder("main/compile/maven/org.mockito/mockito-core/5.11.0",
                            "main/runtime/maven/org.mockito/mockito-core/5.11.0");
            assertThat(SequencedProperties.ofFiles(module.resolve(BuildStep.ATTACHMENTS))).containsOnly(
                    Map.entry("main/agent/maven/org.mockito/mockito-core", ""));
        }
    }

    @Test
    public void accepts_a_library_and_its_tests_jar_carrying_one_module_name_as_test_dependencies() throws IOException {
        SequencedMap<String, Path> results = sharedModuleProject("test").execute(Runnable::run).toCompletableFuture().join();
        assertThat(results.get("maven/test-module-/dependencies/artifacts").resolve(BuildStep.DEPENDENCIES))
                .as("the tests of a pom.xml module run on the class path, where a module name means nothing")
                .content()
                .contains("maven/org.example/lib/1.0", "maven/org.example/lib/jar/tests/1.0");
    }

    @Test
    public void refuses_a_library_and_its_tests_jar_carrying_one_module_name_as_main_dependencies() throws IOException {
        BuildExecutor executor = sharedModuleProject("compile");
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .as("the main half compiles a module-info.java on the module path, where it resolves only one of them")
                .hasStackTraceContaining("maven/org.example/lib/1.0 and maven/org.example/lib/jar/tests/1.0"
                        + " both carry module lib.shared in group main")
                .hasStackTraceContaining("an <exclusions> entry in pom.xml");
    }

    private BuildExecutor sharedModuleProject(String scope) throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>lib</artifactId>
                            <version>1.0</version>
                            <scope>%1$s</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>lib</artifactId>
                            <version>1.0</version>
                            <type>test-jar</type>
                            <scope>%1$s</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(scope));
        Files.writeString(Files.createDirectories(project.resolve("src/main/java/sample")).resolve("Sample.java"),
                "package sample; public class Sample { }");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java/sample")).resolve("SampleTest.java"),
                "package sample; public class SampleTest { }");
        Path folder = Files.createDirectories(repository.resolve("org/example/lib/1.0"));
        Files.writeString(folder.resolve("lib-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>lib</artifactId>
                    <version>1.0</version>
                </project>
                """);
        for (String name : List.of("lib-1.0.jar", "lib-1.0-tests.jar")) {
            Manifest manifest = new Manifest(new ByteArrayInputStream(
                    "Manifest-Version: 1.0\nAutomatic-Module-Name: lib.shared\n\n".getBytes(StandardCharsets.UTF_8)));
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(folder.resolve(name)), manifest)) {
                jar.putNextEntry(new JarEntry(name.contains("tests") ? "lib/ValueTest.class" : "lib/Value.class"));
                jar.closeEntry();
            }
        }
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", MavenProject.make(Environment.NONE,
                project,
                "main",
                "maven",
                Map.of("maven", new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE)),
                null,
                Collections.emptyNavigableSet(),
                (_, _, _) -> new AssemblyDescriptor((buildExecutor, _) -> buildExecutor.addModule("java",
                        new JavaToolchainModule(),
                        "../sources",
                        "../manifests",
                        "../dependencies/artifacts"))));
        return executor;
    }

    @Test
    public void native_access_the_project_names_is_recorded_in_its_jar_and_granted_in_its_tests()
            throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.native group/artifact org.example/jni-->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        Path main = results.get("maven/module-/manifests"), test = results.get("maven/test-module-/manifests");
        assertThat(SequencedProperties.ofFiles(main.resolve(BuildStep.NATIVES))).containsOnlyKeys(
                "main/native/maven/org.example/jni");
        assertThat(SequencedProperties.ofFiles(main.resolve(BuildStep.MODULE)).getProperty("native"))
                .isEqualTo("true");
        Manifest manifest = new Manifest();
        try (InputStream input = Files.newInputStream(main.resolve("manifest.mf"))) {
            manifest.read(input);
        }
        assertThat(manifest.getMainAttributes().getValue(PathPlacement.NATIVE_ACCESS))
                .isEqualTo("group/artifact,org.example/jni");
        assertThat(SequencedProperties.ofFiles(test.resolve(BuildStep.NATIVES)))
                .as("the tests run the project's jar as a dependency, so they grant it rather than themselves")
                .containsOnlyKeys("main/native/maven/group/artifact", "main/native/maven/org.example/jni");
        assertThat(SequencedProperties.ofFiles(test.resolve(BuildStep.MODULE)).getProperty("native")).isNull();
        assertThat(test.resolve("manifest.mf")).doesNotExist();
    }

    @Test
    public void compiles_the_tests_for_the_release_maven_compiler_test_release_names() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>8</maven.compiler.release>
                        <maven.compiler.testRelease>17</maven.compiler.testRelease>
                    </properties>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve("process/javac.properties"))
                .getProperty("--release")).isEqualTo("8");
        assertThat(SequencedProperties.ofFiles(results.get("maven/test-module-/manifests").resolve("process/javac.properties"))
                .getProperty("--release")).isEqualTo("17");
    }

    @Test
    public void compiles_for_the_compiler_target_when_no_release_is_set_and_names_a_module_that_sets_neither() throws IOException {
        for (String name : List.of("targeted", "unset")) {
            Path module = Files.createDirectory(project.resolve(name));
            Files.writeString(Files.createDirectories(module.resolve("src/main/java")).resolve("source"), "foo");
            Files.writeString(module.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>group</groupId>
                        <artifactId>%s</artifactId>
                        <version>1</version>
                        %s
                    </project>
                    """.formatted(name, name.equals("targeted")
                    ? "<properties><maven.compiler.source>1.8</maven.compiler.source><maven.compiler.target>1.8</maven.compiler.target></properties>"
                    : ""));
        }
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>targeted</module>
                        <module>unset</module>
                    </modules>
                </project>
                """);
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        List<String> printed = new ArrayList<>();
        executor.addModule("maven", MavenProject.ofEnvironment(new Environment(Map.of("palette.colors", "none")).out(printed::add),
                project,
                "maven",
                mavenRepository,
                mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-targeted/manifests").resolve("process/javac.properties"))
                .getProperty("--release"))
                .as("maven.compiler.target 1.8 compiles for release 8, as the compiler plugin does")
                .isEqualTo("8");
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-unset/manifests").resolve("process/javac.properties"))
                .getProperty("--release")).isEqualTo(Integer.toString(Runtime.version().feature()));
        assertThat(printed).containsExactly("[RELEASE]   group:unset compiles for release " + Runtime.version().feature()
                + ", the JDK the build runs on, as " + Path.of("unset", "pom.xml") + " sets neither maven.compiler.release nor its target or source"
                + " - maven.compiler.release sets it");
    }

    @Test
    public void an_unversioned_plugin_takes_its_newest_release_until_it_is_pinned() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.plugin
                    javac maven/com.google.errorprone/error_prone_core
                    maven/org.example/processor/1.0
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(BuildStep.REQUIRES))
                .stringPropertyNames())
                .as("a plugin declared without a version floats as a tool the build resolves itself does, until a pin settles it")
                .contains("javac/plugin/maven/com.google.errorprone/error_prone_core/RELEASE",
                        "plugin/plugin/maven/org.example/processor/1.0");
    }

    @Test
    public void a_processor_dependency_reaches_only_the_half_that_declares_it() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>processor</artifactId>
                            <version>1.0</version>
                            <type>processor</type>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(BuildStep.REQUIRES))
                .stringPropertyNames())
                .contains("plugin/plugin/maven/org.example/processor/1.0");
        assertThat(SequencedProperties.ofFiles(results.get("maven/test-module-/manifests").resolve(BuildStep.REQUIRES))
                .stringPropertyNames())
                .as("the main half's processor does not compile the tests")
                .noneMatch(key -> key.contains("org.example/processor"));
    }

    @Test
    public void a_module_alias_comment_names_the_jar_for_the_dependency_resolution_and_the_manifest() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.alias jline jline/jline-->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        Path manifests = results.get("maven/module-/manifests");
        assertThat(SequencedProperties.ofFiles(manifests.resolve(BuildStep.ALIASES)))
                .containsOnly(Map.entry("main/module/jline", "jline/jline"));
        try (InputStream input = Files.newInputStream(manifests.resolve(Versions.MANIFEST))) {
            assertThat(new Manifest(input).getMainAttributes().getValue(PathPlacement.ALIASES))
                    .as("the jar tells a consumer which name its descriptor requires the dependency by")
                    .isEqualTo("jline=jline/jline");
        }
    }

    @Test
    public void a_module_alias_of_a_test_dependency_reaches_only_the_test_module() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.alias truth com.google.truth/truth-->
                    <!--jenesis.alias jline jline/jline-->
                    <dependencies>
                        <dependency>
                            <groupId>com.google.truth</groupId>
                            <artifactId>truth</artifactId>
                            <version>1.4.5</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(BuildStep.ALIASES)))
                .as("the main module does not resolve a test dependency, so an alias of one is no alias of it")
                .containsOnly(Map.entry("main/module/jline", "jline/jline"));
        assertThat(SequencedProperties.ofFiles(results.get("maven/test-module-/manifests").resolve(BuildStep.ALIASES)))
                .containsOnly(Map.entry("main/module/truth", "com.google.truth/truth"),
                        Map.entry("main/module/jline", "jline/jline"));
    }

    @Test
    public void test_scoped_attach_is_routed_to_test_module_only() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.mockito/mockito-core
                    -->
                    <dependencies>
                        <dependency>
                            <groupId>org.mockito</groupId>
                            <artifactId>mockito-core</artifactId>
                            <version>5.11.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        Path module = results.get("maven/module-/manifests");
        SequencedProperties requires = SequencedProperties.ofFiles(module.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .doesNotContain("main/runtime/maven/org.mockito/mockito-core/5.11.0");
        assertThat(module.resolve(BuildStep.ATTACHMENTS)).doesNotExist();
        Path testModule = results.get("maven/test-module-/manifests");
        SequencedProperties testRequires = SequencedProperties.ofFiles(testModule.resolve(BuildStep.REQUIRES));
        assertThat(testRequires.stringPropertyNames())
                .contains("main/runtime/maven/org.mockito/mockito-core/5.11.0");
        assertThat(SequencedProperties.ofFiles(testModule.resolve(BuildStep.ATTACHMENTS))).containsOnly(
                Map.entry("main/agent/maven/org.mockito/mockito-core", ""));
    }

    @Test
    public void attach_of_managed_dependency_uses_managed_version() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.example/agent
                    -->
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>agent</artifactId>
                                <version>2.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        for (String manifests : List.of("maven/module-/manifests", "maven/test-module-/manifests")) {
            Path module = results.get(manifests);
            SequencedProperties requires = SequencedProperties.ofFiles(module.resolve(BuildStep.REQUIRES));
            assertThat(requires.stringPropertyNames()).contains("main/runtime/maven/org.example/agent/2.0");
        }
    }

    @Test
    public void attach_without_matching_dependency_seeds_versionless() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    io.opentelemetry.javaagent/opentelemetry-javaagent otel.option=value
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        for (String manifests : List.of("maven/module-/manifests", "maven/test-module-/manifests")) {
            Path module = results.get(manifests);
            SequencedProperties requires = SequencedProperties.ofFiles(module.resolve(BuildStep.REQUIRES));
            assertThat(requires.stringPropertyNames())
                    .contains("main/runtime/maven/io.opentelemetry.javaagent/opentelemetry-javaagent");
            assertThat(SequencedProperties.ofFiles(module.resolve(BuildStep.ATTACHMENTS))).containsOnly(
                    Map.entry("main/agent/maven/io.opentelemetry.javaagent/opentelemetry-javaagent", "otel.option=value"));
        }
    }

    @Test
    public void attach_with_classifier_without_version_fails() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    main/maven/org.example/agent/jar/all
                    -->
                </project>
                """);
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Cannot determine version for jenesis.attach main/maven/org.example/agent/jar/all:"
                        + " declare it as a dependency or manage its version");
    }

    @Test
    public void scopes_are_routed_to_correct_requires_files() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>scope</groupId>
                            <artifactId>compile-dep</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>scope</groupId>
                            <artifactId>provided-dep</artifactId>
                            <version>1</version>
                            <scope>provided</scope>
                        </dependency>
                        <dependency>
                            <groupId>scope</groupId>
                            <artifactId>runtime-dep</artifactId>
                            <version>1</version>
                            <scope>runtime</scope>
                        </dependency>
                        <dependency>
                            <groupId>scope</groupId>
                            <artifactId>test-dep</artifactId>
                            <version>1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();

        Path mainRequires = results.get("maven/module-/manifests")
                .resolve(BuildStep.REQUIRES);
        SequencedProperties mainRequiresProps = SequencedProperties.ofFiles(mainRequires);
        assertThat(mainRequiresProps.stringPropertyNames()).containsExactlyInAnyOrder(
                "main/compile/maven/scope/compile-dep/1",
                "main/runtime/maven/scope/compile-dep/1",
                "main/compile/maven/scope/provided-dep/1",
                "main/runtime/maven/scope/runtime-dep/1");

        Path testRequires = results.get("maven/test-module-/manifests")
                .resolve(BuildStep.REQUIRES);
        SequencedProperties testRequiresProps = SequencedProperties.ofFiles(testRequires);
        assertThat(testRequiresProps.stringPropertyNames()).containsExactlyInAnyOrder(
                "main/compile/maven/scope/compile-dep/1",
                "main/runtime/maven/scope/compile-dep/1",
                "main/compile/maven/scope/runtime-dep/1",
                "main/runtime/maven/scope/runtime-dep/1",
                "main/compile/maven/scope/provided-dep/1",
                "main/runtime/maven/scope/provided-dep/1",
                "main/compile/maven/scope/test-dep/1",
                "main/runtime/maven/scope/test-dep/1",
                "main/compile/maven/group/artifact/1",
                "main/runtime/maven/group/artifact/1");
    }

    @Test
    public void reads_a_version_range_of_the_project_s_own_pom_whole() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <tool.version>[8.1,)</tool.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>range</groupId>
                                <artifactId>managed-dep</artifactId>
                                <version>[3.0,4.0)</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>range</groupId>
                            <artifactId>compile-dep</artifactId>
                            <version>[1.0,2.0)</version>
                            <optional>true</optional>
                        </dependency>
                        <dependency>
                            <groupId>range</groupId>
                            <artifactId>test-dep</artifactId>
                            <version>${tool.version}</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();

        Path module = results.get("maven/module-/manifests");
        assertThat(SequencedProperties.ofFiles(module.resolve(BuildStep.REQUIRES)).stringPropertyNames())
                .containsExactlyInAnyOrder(
                        "main/compile/maven/range/compile-dep/[1.0,2.0)",
                        "main/runtime/maven/range/compile-dep/[1.0,2.0)");
        assertThat(SequencedProperties.ofFiles(module.resolve(BuildStep.OPTIONALS)).stringPropertyNames())
                .containsExactlyInAnyOrder(
                        "main/compile/maven/range/compile-dep/[1.0,2.0)",
                        "main/runtime/maven/range/compile-dep/[1.0,2.0)");
        assertThat(SequencedProperties.ofFiles(module.resolve(BuildStep.VERSIONS)))
                .containsOnly(Map.entry("main/maven/range/managed-dep", "[3.0,4.0)"));
        assertThat(SequencedProperties.ofFiles(results.get("maven/test-module-/manifests").resolve(BuildStep.REQUIRES))
                .stringPropertyNames())
                .containsExactlyInAnyOrder(
                        "main/compile/maven/range/compile-dep/[1.0,2.0)",
                        "main/runtime/maven/range/compile-dep/[1.0,2.0)",
                        "main/compile/maven/range/test-dep/[8.1,)",
                        "main/runtime/maven/range/test-dep/[8.1,)",
                        "main/compile/maven/group/artifact/1",
                        "main/runtime/maven/group/artifact/1");
    }

    @Test
    public void the_exclusions_of_a_managed_dependency_are_recorded_for_the_pin_that_replaces_its_entry() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>lib</artifactId>
                                <version>1</version>
                                <exclusions>
                                    <exclusion>
                                        <groupId>excluded</groupId>
                                        <artifactId>transitive</artifactId>
                                    </exclusion>
                                </exclusions>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(BuildStep.MANAGED))
                .getProperty("main/maven/other/lib"))
                .as("a managed entry's exclusions are what a pinned entry replacing it has to repeat")
                .isEqualTo("excluded/transitive");
    }

    @Test
    public void the_property_expressions_of_a_dependency_are_recorded_for_the_pin_that_rewrites_its_entry() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <javafx.platform>linux</javafx.platform>
                        <javafx.version>17</javafx.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.openjfx</groupId>
                                <artifactId>javafx-base</artifactId>
                                <version>${javafx.version}</version>
                                <classifier>${javafx.platform}</classifier>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.openjfx</groupId>
                            <artifactId>javafx-base</artifactId>
                            <classifier>${javafx.platform}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(MavenProject.EXPRESSIONS)))
                .as("pin writes the entry with the expressions the POM wrote, and the version only where it still is the property's")
                .containsExactly(Map.entry("org.openjfx/javafx-base/jar/linux",
                        "org.openjfx/javafx-base/jar/${javafx.platform} ${javafx.version} 17"));
    }

    @Test
    public void exclusions_are_written_for_both_main_and_test_modules() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>lib</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>excluded</groupId>
                                    <artifactId>transitive</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties mainExclusions = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.EXCLUSIONS));
        assertThat(mainExclusions.getProperty("main/compile/maven/other/lib/1")).isEqualTo("excluded/transitive");
        assertThat(mainExclusions.getProperty("main/runtime/maven/other/lib/1")).isEqualTo("excluded/transitive");
        Path testExclusions = results.get("maven/test-module-/manifests").resolve(BuildStep.EXCLUSIONS);
        assertThat(testExclusions).exists();
        SequencedProperties testExclusionProps = SequencedProperties.ofFiles(testExclusions);
        assertThat(testExclusionProps.getProperty("main/compile/maven/other/lib/1")).isEqualTo("excluded/transitive");
        assertThat(testExclusionProps.getProperty("main/runtime/maven/other/lib/1")).isEqualTo("excluded/transitive");

        SequencedProperties testRequires = SequencedProperties.ofFiles(
                results.get("maven/test-module-/manifests").resolve(BuildStep.REQUIRES));
        assertThat(testRequires.stringPropertyNames()).contains("main/compile/maven/other/lib/1", "main/runtime/maven/other/lib/1");
    }

    @Test
    public void marks_an_optional_dependency_of_the_main_module_and_none_of_the_test_module() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>optional</artifactId>
                            <version>1</version>
                            <optional>true</optional>
                        </dependency>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>required</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(BuildStep.OPTIONALS))
                .stringPropertyNames())
                .as("the published POM keeps the dependency optional, so a consumer does not inherit it")
                .containsExactlyInAnyOrder("main/compile/maven/other/optional/1", "main/runtime/maven/other/optional/1");
        assertThat(results.get("maven/test-module-/manifests").resolve(BuildStep.OPTIONALS))
                .as("the test module publishes no POM")
                .doesNotExist();
    }

    @Test
    public void describes_a_bom_by_its_own_dependency_management_with_its_properties_resolved() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <url>https://example.com</url>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>inherited</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <modules>
                        <module>bom</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("bom")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>bom</artifactId>
                    <packaging>pom</packaging>
                    <properties>
                        <maven.deploy.skip>false</maven.deploy.skip>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>${project.groupId}</groupId>
                                <artifactId>library</artifactId>
                                <version>${project.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>other-bom</artifactId>
                                <version>3</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results.keySet())
                .as("neither the aggregator nor the BOM is a module that compiles")
                .noneMatch(key -> key.contains("module-"));
        Path boms = results.get("maven/boms");
        SequencedProperties inventory = SequencedProperties.ofFiles(boms.resolve("inventory.properties"));
        assertThat(inventory).containsEntry("module-bom.path", "bom").containsEntry("module-bom.packaging", "pom");
        String pom = Files.readString(boms.resolve(inventory.getProperty("module-bom.pom")));
        assertThat(pom)
                .contains("<groupId>group</groupId>", "<artifactId>bom</artifactId>", "<version>1</version>",
                        "<packaging>pom</packaging>", "<url>https://example.com/bom</url>")
                .contains("<artifactId>library</artifactId>", "<artifactId>other-bom</artifactId>", "<scope>import</scope>")
                .doesNotContain("${", "<parent>")
                .as("the parent's managed versions stay with the parent, which is not published")
                .doesNotContain("inherited");
    }

    @Test
    public void can_resolve_multi_pom() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <modules>
                      <module>subproject</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                        <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                </project>
                """);
        Files.writeString(Files.createDirectories(subproject.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(subproject.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("maven/module-/manifests", "maven/module-subproject/manifests");
        Path parent = results.get("maven/module-/manifests");
        assertThat(parent.resolve(BuildStep.IDENTITY)).doesNotExist();
        Path parentCoordinatesFolder = results.get("maven/module-/coordinates");
        SequencedProperties parentCoordinates = SequencedProperties.ofFiles(parentCoordinatesFolder.resolve(BuildStep.IDENTITY));
        assertThat(parentCoordinates).containsOnlyKeys(
                "maven/parent/artifact/1",
                "maven/parent/artifact/pom/1");
        assertThat(parentCoordinates.getProperty("maven/parent/artifact/1")).isEmpty();
        assertThat(parent.resolve(BuildStep.REQUIRES)).exists().content().isEmpty();
        Path parentTests = results.get("maven/test-module-/manifests");
        assertThat(parentTests.resolve(BuildStep.IDENTITY)).doesNotExist();
        Path parentTestCoordinatesFolder = results.get("maven/test-module-/coordinates");
        SequencedProperties parentTestCoordinates = SequencedProperties.ofFiles(parentTestCoordinatesFolder.resolve(BuildStep.IDENTITY));
        assertThat(parentTestCoordinates).containsOnlyKeys(
                "maven/parent/artifact/jar/tests/1",
                "maven/parent/artifact/pom/1");
        assertThat(parentTestCoordinates.getProperty("maven/parent/artifact/jar/tests/1")).isEmpty();
        SequencedProperties parentTestModule = SequencedProperties.ofFiles(parentTests.resolve(BuildStep.MODULE));
        assertThat(parentTestModule.getProperty("test")).isEqualTo("artifact");
        SequencedProperties parentTestDependencies = SequencedProperties.ofFiles(parentTests.resolve(BuildStep.REQUIRES));
        assertThat(parentTestDependencies).containsOnlyKeys(
                "main/compile/maven/parent/artifact/1",
                "main/runtime/maven/parent/artifact/1");
        assertThat(parentTestDependencies.getProperty("main/compile/maven/parent/artifact/1")).isEmpty();
        Path child = results.get("maven/module-subproject/manifests");
        assertThat(child.resolve(BuildStep.IDENTITY)).doesNotExist();
        Path childCoordinatesFolder = results.get("maven/module-subproject/coordinates");
        SequencedProperties childCoordinates = SequencedProperties.ofFiles(childCoordinatesFolder.resolve(BuildStep.IDENTITY));
        assertThat(childCoordinates).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/group/artifact/pom/1");
        assertThat(childCoordinates.getProperty("maven/group/artifact/1")).isEmpty();
        assertThat(child.resolve(BuildStep.REQUIRES)).exists().content().isEmpty();
        Path childTests = results.get("maven/test-module-subproject/manifests");
        assertThat(childTests.resolve(BuildStep.IDENTITY)).doesNotExist();
        Path childTestCoordinatesFolder = results.get("maven/test-module-subproject/coordinates");
        SequencedProperties childTestCoordinates = SequencedProperties.ofFiles(childTestCoordinatesFolder.resolve(BuildStep.IDENTITY));
        assertThat(childTestCoordinates).containsOnlyKeys(
                "maven/group/artifact/jar/tests/1",
                "maven/group/artifact/pom/1");
        assertThat(childTestCoordinates.getProperty("maven/group/artifact/jar/tests/1")).isEmpty();
        SequencedProperties childTestModule = SequencedProperties.ofFiles(childTests.resolve(BuildStep.MODULE));
        assertThat(childTestModule.getProperty("test")).isEqualTo("artifact");
        SequencedProperties childTestDependencies = SequencedProperties.ofFiles(childTests.resolve(BuildStep.REQUIRES));
        assertThat(childTestDependencies).containsOnlyKeys(
                "main/compile/maven/group/artifact/1",
                "main/runtime/maven/group/artifact/1");
        assertThat(childTestDependencies.getProperty("main/compile/maven/group/artifact/1")).isEmpty();
    }

    @Test
    public void can_resolve_sources_and_resources() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/main/resources")).resolve("resource"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("maven/module-/manifests",
                "maven/module-/sources",
                "maven/module-/resources-1");
        assertThat(results.get("maven/module-/sources").resolve(BuildStep.SOURCES + "source")).content().isEqualTo("foo");
        assertThat(results.get("maven/module-/resources-1").resolve(BuildStep.RESOURCES + "resource")).content().isEqualTo("bar");
    }

    @Test
    public void can_resolve_sources_and_resources_explicit() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <build>
                       <sourceDirectory>sources</sourceDirectory>
                       <resources>
                         <resource>
                           <directory>resources-1</directory>
                         </resource>
                         <resource>
                           <directory>resources-2</directory>
                         </resource>
                       </resources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("sources")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("resources-1")).resolve("resource1"), "bar");
        Files.writeString(Files.createDirectories(project.resolve("resources-2")).resolve("resource2"), "qux");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("maven/module-/manifests",
                "maven/module-/sources",
                "maven/module-/resources-1",
                "maven/module-/resources-2");
        assertThat(results.get("maven/module-/sources").resolve(BuildStep.SOURCES + "source")).content().isEqualTo("foo");
        assertThat(results.get("maven/module-/resources-1").resolve(BuildStep.RESOURCES + "resource1")).content().isEqualTo("bar");
        assertThat(results.get("maven/module-/resources-2").resolve(BuildStep.RESOURCES + "resource2")).content().isEqualTo("qux");
    }

    @Test
    public void reads_a_resource_directory_the_pom_names_twice_once() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <build>
                       <resources>
                         <resource>
                           <directory>src/main/resources</directory>
                           <filtering>true</filtering>
                           <includes>
                             <include>**/*.properties</include>
                           </includes>
                         </resource>
                         <resource>
                           <directory>src/main/resources/</directory>
                           <filtering>false</filtering>
                           <excludes>
                             <exclude>**/*.properties</exclude>
                           </excludes>
                         </resource>
                         <resource>
                           <directory>${project.basedir}/src/main/resources</directory>
                         </resource>
                       </resources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/main/resources")).resolve("resource"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKey("maven/module-/resources-1");
        assertThat(results.keySet())
                .as("a directory named twice is copied once, so that the jar holds each of its files once")
                .noneMatch(key -> key.startsWith("maven/module-/resources-") && !key.equals("maven/module-/resources-1"));
        assertThat(results.get("maven/module-/resources-1").resolve(BuildStep.RESOURCES + "resource")).content().isEqualTo("bar");
    }

    @Test
    public void resolves_the_basedir_of_an_inherited_resource_directory_in_the_inheriting_module() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                    <build>
                       <resources>
                         <resource>
                           <directory>${project.basedir}/src/main/resources</directory>
                         </resource>
                         <resource>
                           <directory>${basedir}/src/main/missing</directory>
                         </resource>
                       </resources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("child")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                    </properties>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("child/src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("child/src/main/resources")).resolve("resource"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        List<String> printed = new ArrayList<>();
        executor.addModule("maven", MavenProject.ofEnvironment(new Environment(Map.of("palette.colors", "none")).out(printed::add),
                project,
                "maven",
                mavenRepository,
                mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results.get("maven/module-child/resources-1").resolve(BuildStep.RESOURCES + "resource"))
                .as("Maven interpolates project.basedir in the module that inherits the resource directory")
                .content()
                .isEqualTo("bar");
        assertThat(printed).contains("[RESOURCES] " + Path.of("child", "pom.xml")
                + " names the resource directory ./src/main/missing, which does not exist beside it, so it adds no resources");
    }

    @Test
    public void refuses_a_resource_directory_that_contains_the_build_output() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <build>
                       <resources>
                         <resource>
                           <directory>./</directory>
                         </resource>
                       </resources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(project.resolve("target"),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .as("copying the project root would copy the build's own output into itself without end")
                .hasMessageContaining("The resource directory ./")
                .hasMessageContaining("contains target")
                .hasMessageContaining("-Djenesis.project.resources=<file>:<path in the jar>")
                .hasMessageContaining("move that resource into a <profile> activated by a property");
    }

    @Test
    public void refuses_a_resource_directory_that_contains_the_local_jenesis_folder() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <build>
                       <resources>
                         <resource>
                           <directory>.</directory>
                         </resource>
                       </resources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.createDirectories(project.resolve(".jenesis/artifacts"));
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("contains .jenesis");
    }

    @Test
    public void can_resolve_test_sources_and_resources() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/resources")).resolve("resource"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("maven/test-module-/manifests",
                "maven/test-module-/sources",
                "maven/test-module-/resources-1");
        assertThat(results.get("maven/test-module-/sources").resolve(BuildStep.SOURCES + "source")).content().isEqualTo("foo");
        assertThat(results.get("maven/test-module-/resources-1").resolve(BuildStep.RESOURCES + "resource")).content().isEqualTo("bar");
    }

    @Test
    public void builds_an_empty_main_module_for_a_pom_with_test_sources_alone() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results)
                .as("the test module requires the main artifact, which Maven builds as an empty jar")
                .containsKeys("maven/module-/manifests", "maven/module-/coordinates", "maven/test-module-/manifests");
        assertThat(results.get("maven/module-/sources")).isEmptyDirectory();
    }

    @Test
    public void builds_a_module_without_sources_that_configures_a_plugin_and_names_one_that_does_not() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                    </properties>
                    <modules>
                        <module>generated</module>
                        <module>empty</module>
                    </modules>
                </project>
                """);
        for (String name : List.of("generated", "empty")) {
            Files.writeString(Files.createDirectory(project.resolve(name)).resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </parent>
                        <artifactId>%s</artifactId>
                    </project>
                    """.formatted(name));
        }
        Files.writeString(Files.createDirectories(project.resolve("generated/src/main/build.jenesis"))
                .resolve("plugin-generator.properties"), "");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        List<String> printed = new ArrayList<>();
        executor.addModule("maven", MavenProject.ofEnvironment(new Environment(Map.of("palette.colors", "none")).out(printed::add),
                project,
                "maven",
                mavenRepository,
                mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results)
                .as("a plugin the module configures generates what it compiles")
                .containsKeys("maven/module-generated/manifests", "maven/module-generated/coordinates")
                .doesNotContainKeys("maven/module-empty/manifests");
        assertThat(printed).containsExactly("[SKIPPED]   group:empty builds no jar, as " + Path.of("empty", "pom.xml") + " has neither sources nor"
                + " resources: a plugin that generates them is configured by a plugin-<name>.properties in "
                + Path.of("empty", "src", "main", "build.jenesis") + ", which builds the module");
    }

    @Test
    public void builds_a_bundle_as_a_jar_and_names_a_module_whose_packaging_it_does_not_build() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                    </properties>
                    <modules>
                        <module>bundled</module>
                        <module>web</module>
                    </modules>
                </project>
                """);
        for (String name : List.of("bundled", "web")) {
            Files.writeString(Files.createDirectory(project.resolve(name)).resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </parent>
                        <artifactId>%s</artifactId>
                        <packaging>%s</packaging>
                    </project>
                    """.formatted(name, name.equals("web") ? "war" : "bundle"));
            Files.writeString(Files.createDirectories(project.resolve(name + "/src/main/java")).resolve("source"), "foo");
        }
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        List<String> printed = new ArrayList<>();
        executor.addModule("maven", MavenProject.ofEnvironment(new Environment(Map.of("palette.colors", "none")).out(printed::add),
                project,
                "maven",
                mavenRepository,
                mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results)
                .as("maven-bundle-plugin's packaging produces a jar")
                .containsKeys("maven/module-bundled/manifests", "maven/module-bundled/coordinates")
                .doesNotContainKeys("maven/module-web/manifests");
        assertThat(printed).containsExactly("[SKIPPED]   " + Path.of("web", "pom.xml") + " builds nothing, as its packaging war is none of jar"
                + " and bundle, the packagings this build builds: where it is a jar with more in it, declare"
                + " <packaging>jar</packaging> and let a plugin add the rest");
    }

    @Test
    public void can_resolve_test_sources_and_resources_explicit() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <build>
                       <testSourceDirectory>sources</testSourceDirectory>
                       <testResources>
                         <testResource>
                           <directory>resources-1</directory>
                         </testResource>
                         <testResource>
                           <directory>resources-2</directory>
                         </testResource>
                       </testResources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("sources")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("resources-1")).resolve("resource1"), "bar");
        Files.writeString(Files.createDirectories(project.resolve("resources-2")).resolve("resource2"), "qux");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(results).containsKeys("maven/test-module-/manifests",
                "maven/test-module-/sources",
                "maven/test-module-/resources-1",
                "maven/test-module-/resources-2");
        assertThat(results.get("maven/test-module-/sources").resolve(BuildStep.SOURCES + "source")).content().isEqualTo("foo");
        assertThat(results.get("maven/test-module-/resources-1").resolve(BuildStep.RESOURCES + "resource1")).content().isEqualTo("bar");
        assertThat(results.get("maven/test-module-/resources-2").resolve(BuildStep.RESOURCES + "resource2")).content().isEqualTo("qux");
    }

    @Test
    public void can_resolve_multi_module_project() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>foo</module>
                        <module>bar</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("foo")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>foo</artifactId>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("foo/src/main/java/foo")).resolve("Foo.java"), """
                package foo;
                public class Foo { }
                """);
        Files.writeString(Files.createDirectory(project.resolve("bar")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>bar</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>foo</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("bar/src/main/java/bar")).resolve("Bar.java"), """
                package bar;
                import foo.Foo;
                public class Bar extends Foo { }
                """);
        BuildExecutor root = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        root.addModule("maven", MavenProject.make(Environment.NONE,
                project,
                "main",
                "maven",
                Map.of("maven", new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE)),
                null,
                Collections.emptyNavigableSet(),
                (descriptor, _, _) -> {
                    switch (descriptor.name()) {
                        case "module-foo" -> {
                            assertThat(descriptor.dependencies()).isEmpty();
                            assertThat(descriptor.location()).isEqualTo(project.resolve("foo"));
                        }
                        case "module-bar" -> {
                            assertThat(descriptor.dependencies()).containsExactly("module-foo");
                            assertThat(descriptor.location()).isEqualTo(project.resolve("bar"));
                        }
                        default -> fail("Unexpected module: " + descriptor.name());
                    }
                    return new AssemblyDescriptor((buildExecutor, inherited) -> {
                        switch (descriptor.name()) {
                            case "module-foo" -> assertThat(inherited).containsOnlyKeys(
                                    "../sources",
                                    "../manifests",
                                    "../coordinates",
                                    "../dependencies/artifacts");
                            case "module-bar" -> assertThat(inherited).containsOnlyKeys(
                                    "../sources",
                                    "../manifests",
                                    "../coordinates",
                                    "../dependencies/artifacts",
                                    "../../module-foo/dependencies/prepare",
                                    "../../module-foo/dependencies/artifacts",
                                    "../../module-foo/produce/java/classes",
                                    "../../module-foo/produce/java/artifacts",
                                    "../../module-foo/assign",
                                    "../../module-foo/inventory");
                            default -> fail("Unexpected module: " + descriptor.name());
                        }
                        buildExecutor.addModule("java", new JavaToolchainModule(),
                                "../sources", "../manifests",
                                "../dependencies/artifacts");
                    });
                }));
        SequencedMap<String, Path> results = root.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties foo = SequencedProperties.ofFiles(results
                .get("maven/module-foo/assign")
                .resolve(BuildStep.IDENTITY));
        assertThat(foo.stringPropertyNames()).containsExactly("maven/group/foo/1", "maven/group/foo/pom/1");
        assertThat(foo.getProperty("maven/group/foo/1"))
                .isEqualTo("../../produce/java/artifacts/jar/output/artifacts/group%2Ffoo%2F1.jar");
        assertThat(foo.getProperty("maven/group/foo/pom/1"))
                .isEqualTo("../../../../../identifier/scan/output/pom/foo/pom.xml");
        SequencedProperties bar = SequencedProperties.ofFiles(results
                .get("maven/module-bar/assign")
                .resolve(BuildStep.IDENTITY));
        assertThat(bar.stringPropertyNames()).containsExactly("maven/group/bar/1", "maven/group/bar/pom/1");
        assertThat(bar.getProperty("maven/group/bar/1"))
                .isEqualTo("../../produce/java/artifacts/jar/output/artifacts/group%2Fbar%2F1.jar");
        assertThat(bar.getProperty("maven/group/bar/pom/1"))
                .isEqualTo("../../../../../identifier/scan/output/pom/bar/pom.xml");
        assertThat(results.keySet())
                .contains("maven/module-foo/inventory", "maven/module-bar/inventory")
                .doesNotContain("maven/module-foo/coordinates", "maven/module-bar/coordinates");
        SequencedProperties fooInventory = SequencedProperties.ofFiles(results
                .get("maven/module-foo/inventory")
                .resolve("inventory.properties"));
        assertThat(fooInventory.getProperty("module-foo.runtime.0"))
                .endsWith("/group%2Ffoo%2F1.jar");
        assertThat(fooInventory.getProperty("module-foo.artifacts.0"))
                .endsWith("/group%2Ffoo%2F1.jar");
        SequencedProperties barInventory = SequencedProperties.ofFiles(results
                .get("maven/module-bar/inventory")
                .resolve("inventory.properties"));
        assertThat(barInventory.getProperty("module-bar.runtime.0"))
                .endsWith("/group%2Fbar%2F1.jar");
        assertThat(barInventory.getProperty("module-bar.artifacts.0"))
                .endsWith("/group%2Fbar%2F1.jar");
    }

    @Test
    public void emits_versions_properties_from_dependency_management() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>pinned</groupId>
                                <artifactId>simple</artifactId>
                                <version>2.0</version>
                            </dependency>
                            <dependency>
                                <groupId>pinned</groupId>
                                <artifactId>typed</artifactId>
                                <version>3.0</version>
                                <type>war</type>
                            </dependency>
                            <dependency>
                                <groupId>pinned</groupId>
                                <artifactId>classified</artifactId>
                                <version>4.0</version>
                                <classifier>sources</classifier>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        Path module = results.get("maven/module-/manifests");
        Path versionsFile = module.resolve(BuildStep.VERSIONS);
        assertThat(versionsFile).exists();
        SequencedProperties versions = SequencedProperties.ofFiles(versionsFile);
        assertThat(versions).containsOnly(
                Map.entry("main/maven/pinned/simple", "2.0"),
                Map.entry("main/maven/pinned/typed/war", "3.0"),
                Map.entry("main/maven/pinned/classified/jar/sources", "4.0"));
        Path testModule = results.get("maven/test-module-/manifests");
        assertThat(testModule.resolve(BuildStep.VERSIONS)).exists();
        assertThat(testModule.resolve(BuildStep.VERSIONS)).exists();
    }

    @Test
    public void emits_group_qualified_versions_from_the_jenesis_pin_block() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    launcher/maven/build.jenesis/build.jenesis.launcher 0.2.0 SHA-256/abc
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties versions = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.VERSIONS));
        assertThat(versions)
                .as("a group-qualified block pin keeps its own group rather than being prefixed with main/")
                .containsEntry("launcher/maven/build.jenesis/build.jenesis.launcher", "0.2.0 SHA-256/abc");
        assertThat(versions.stringPropertyNames()).noneMatch(name -> name.startsWith("main/maven/launcher"));
    }

    @Test
    public void expands_short_form_block_pins_into_the_main_group() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    org.slf4j/slf4j-api 2.0.16 SHA-256/abc
                    some.module 1.2.3 SHA-256/def
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties versions = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.VERSIONS));
        assertThat(versions)
                .as("a one-slash short form expands to the main/maven group")
                .containsEntry("main/maven/org.slf4j/slf4j-api", "2.0.16 SHA-256/abc");
        assertThat(versions)
                .as("a bare module short form expands to the main/module group")
                .containsEntry("main/module/some.module", "1.2.3 SHA-256/def");
    }

    @Test
    public void rejects_malformed_block_pin_token() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    org.slf4j/ 2.0.16
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Malformed jenesis.pin token")
                .hasMessageContaining("org.slf4j/");
    }

    @Test
    public void rejects_prose_written_among_the_block_pins() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    org.slf4j/slf4j-api 2.0.16
                    Why this dependency is here and what it does for us.
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .as("the prose is named as the cause rather than surfacing later as a conflicting checksum")
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Why this dependency is here and what it does for us.")
                .hasMessageContaining("move it outside the comment");
    }

    @Test
    public void selects_guarded_block_pin_matching_platform() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    launcher/maven/build.jenesis/build.jenesis.launcher 0.3.0 SHA-256/win (windows)
                    launcher/maven/build.jenesis/build.jenesis.launcher 0.2.0 SHA-256/abc
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver)
                .platform(Platform.of("windows,x86_64")));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties versions = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.VERSIONS));
        assertThat(versions).containsEntry("launcher/maven/build.jenesis/build.jenesis.launcher", "0.3.0 SHA-256/win");
    }

    @Test
    public void falls_back_to_unguarded_block_pin_without_platform_match() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    launcher/maven/build.jenesis/build.jenesis.launcher 0.3.0 SHA-256/win (windows)
                    launcher/maven/build.jenesis/build.jenesis.launcher 0.2.0 SHA-256/abc
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver)
                .platform(Platform.of("linux,x86_64")));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties versions = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.VERSIONS));
        assertThat(versions).containsEntry("launcher/maven/build.jenesis/build.jenesis.launcher", "0.2.0 SHA-256/abc");
        assertThat(versions.stringPropertyNames()).noneMatch(name -> name.contains("["));
    }

    @Test
    public void omits_versions_properties_when_no_dependency_management() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        Path module = results.get("maven/module-/manifests");
        assertThat(module.resolve(BuildStep.VERSIONS)).doesNotExist();
        assertThat(module.resolve(BuildStep.VERSIONS)).doesNotExist();
        Path testModule = results.get("maven/test-module-/manifests");
        assertThat(testModule.resolve(BuildStep.VERSIONS)).doesNotExist();
        assertThat(testModule.resolve(BuildStep.VERSIONS)).doesNotExist();
    }

    @Test
    public void extracts_pom_metadata_into_manifests() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <name>Project Name</name>
                    <description>Project description.</description>
                    <url>https://example.com/project</url>
                    <organization>
                        <name>Example Ltd</name>
                        <url>https://example.com</url>
                    </organization>
                    <licenses>
                        <license>
                            <name>Apache-2.0</name>
                            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                        </license>
                    </licenses>
                    <developers>
                        <developer>
                            <id>alice</id>
                            <name>Alice Example</name>
                            <email>alice@example.com</email>
                        </developer>
                        <developer>
                            <id>bob</id>
                            <name>Bob Example</name>
                            <email>bob@example.com</email>
                        </developer>
                    </developers>
                    <scm>
                        <connection>scm:git:https://example.com/project.git</connection>
                        <developerConnection>scm:git:git@example.com:project.git</developerConnection>
                        <tag>v1</tag>
                        <url>https://example.com/project</url>
                    </scm>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        Path module = results.get("maven/module-/manifests");
        Path moduleFile = module.resolve(BuildStep.MODULE);
        assertThat(moduleFile).exists();
        SequencedProperties moduleProperties = SequencedProperties.ofFiles(moduleFile);
        assertThat(moduleProperties).containsOnly(
                Map.entry("path", ""),
                Map.entry("sources", "src/main/java"),
                Map.entry("modular", "false"));
        Path metadataFile = module.resolve(BuildStep.METADATA);
        assertThat(metadataFile).exists();
        SequencedProperties metadata = SequencedProperties.ofFiles(metadataFile);
        assertThat(metadata).containsOnly(
                Map.entry("project", "group"),
                Map.entry("artifact", "artifact"),
                Map.entry("version", "1"),
                Map.entry("name", "Project Name"),
                Map.entry("description", "Project description."),
                Map.entry("url", "https://example.com/project"),
                Map.entry("organization.name", "Example Ltd"),
                Map.entry("organization.url", "https://example.com"),
                Map.entry("license.apache-2_0.name", "Apache-2.0"),
                Map.entry("license.apache-2_0.url", "https://www.apache.org/licenses/LICENSE-2.0.txt"),
                Map.entry("developer.alice.name", "Alice Example"),
                Map.entry("developer.alice.email", "alice@example.com"),
                Map.entry("developer.bob.name", "Bob Example"),
                Map.entry("developer.bob.email", "bob@example.com"),
                Map.entry("scm.connection", "scm:git:https://example.com/project.git"),
                Map.entry("scm.developerConnection", "scm:git:git@example.com:project.git"),
                Map.entry("scm.tag", "v1"),
                Map.entry("scm.url", "https://example.com/project"));
    }

    @Test
    public void the_module_pom_wins_over_the_project_metadata_for_what_it_declares_and_the_command_line_over_both()
            throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <description>Own description.</description>
                    <licenses>
                        <license>
                            <name>MIT</name>
                        </license>
                    </licenses>
                    <scm>
                        <tag>HEAD</tag>
                    </scm>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Path file = Files.writeString(project.resolve("project.properties"), """
                description=Project description.
                url=https://example.com/project
                license.apache.name=Apache-2.0
                scm.url=https://example.com/project
                """);
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("file", Bind.asMetadata(), file);
        executor.addStep("command", (_, context, _) -> {
            SequencedProperties values = new SequencedProperties();
            values.setProperty("version", "2");
            values.setProperty("scm.tag", "v2");
            values.store(context.next().resolve(BuildStep.METADATA));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        });
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver), "file", "command");
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties metadata = SequencedProperties.ofFiles(results.get("maven/module-/manifests").resolve(BuildStep.METADATA));
        assertThat(metadata).containsOnly(
                Map.entry("project", "group"),
                Map.entry("artifact", "artifact"),
                Map.entry("version", "2"),
                Map.entry("description", "Own description."),
                Map.entry("url", "https://example.com/project"),
                Map.entry("license.mit.name", "MIT"),
                Map.entry("scm.tag", "v2"),
                Map.entry("scm.url", "https://example.com/project"));
    }

    @Test
    public void refuses_a_version_that_names_a_property_no_pom_defines_naming_the_property() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>${nisse.jgit.dynamicVersion}</version>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names the property nisse.jgit.dynamicVersion")
                .hasMessageContaining("jenesis.project.version");
    }

    @Test
    public void refuses_a_dependency_classifier_that_names_a_property_no_pom_defines_naming_the_property() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.openjfx</groupId>
                            <artifactId>javafx-base</artifactId>
                            <version>17</version>
                            <classifier>${javafx.platform}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        assertThatThrownBy(() -> executor.execute(Runnable::run).toCompletableFuture().join())
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The classifier ${javafx.platform} of the dependency org.openjfx:javafx-base")
                .hasMessageContaining("names the property javafx.platform, which no pom.xml defines");
    }

    @Test
    public void a_commanded_version_replaces_an_undefined_one_in_the_project_and_its_sibling_dependencies() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>${nisse.jgit.dynamicVersion}</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>foo</module>
                        <module>bar</module>
                    </modules>
                </project>
                """);
        for (String name : List.of("foo", "bar")) {
            Files.writeString(Files.createDirectory(project.resolve(name)).resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>${nisse.jgit.dynamicVersion}</version>
                        </parent>
                        <artifactId>%s</artifactId>
                        %s
                    </project>
                    """.formatted(name, name.equals("bar") ? """
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>foo</artifactId>
                            <version>${project.version}</version>
                        </dependency>
                    </dependencies>
                    """ : ""));
            Files.writeString(Files.createDirectories(project.resolve(name + "/src/main/java")).resolve("source"), name);
        }
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addStep("command", (_, context, _) -> {
            SequencedProperties values = new SequencedProperties();
            values.setProperty("version", "2");
            values.store(context.next().resolve(BuildStep.METADATA));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        });
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver), "command");
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-bar/manifests").resolve(BuildStep.REQUIRES)).stringPropertyNames())
                .as("a sibling is required at the version the build stamps on it, which is what its POM and SBOM then name")
                .contains("main/runtime/maven/group/foo/2");
        assertThat(SequencedProperties.ofFiles(results.get("maven/module-foo/coordinates").resolve(BuildStep.IDENTITY)).stringPropertyNames())
                .contains("maven/group/foo/2");
    }

    @Test
    public void keeps_a_developer_that_names_no_id_under_a_key_derived_from_its_name() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <developers>
                        <developer>
                            <id>alice</id>
                            <name>Alice Example</name>
                        </developer>
                        <developer>
                            <name>Bob Example</name>
                        </developer>
                        <developer>
                            <name>Bob Example</name>
                            <email>bob@example.org</email>
                        </developer>
                        <developer>
                            <email>carol@example.com</email>
                        </developer>
                    </developers>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties metadata = SequencedProperties.ofFiles(results.get("maven/module-/manifests")
                .resolve(BuildStep.METADATA));
        assertThat(metadata)
                .as("a developer without an id is kept under a key of its name, marked as having no id")
                .contains(
                        Map.entry("developer.alice.name", "Alice Example"),
                        Map.entry("developer.bob_example.id", ""),
                        Map.entry("developer.bob_example.name", "Bob Example"),
                        Map.entry("developer.bob_example_2.id", ""),
                        Map.entry("developer.bob_example_2.name", "Bob Example"),
                        Map.entry("developer.bob_example_2.email", "bob@example.org"),
                        Map.entry("developer.carol_example_com.id", ""),
                        Map.entry("developer.carol_example_com.email", "carol@example.com"))
                .doesNotContainKey("developer.alice.id");
    }

    @Test
    public void keeps_a_developer_that_names_only_its_id() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <developers>
                        <developer>
                            <id>google</id>
                            <organization>Google Inc.</organization>
                        </developer>
                    </developers>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties metadata = SequencedProperties.ofFiles(results.get("maven/module-/manifests")
                .resolve(BuildStep.METADATA));
        assertThat(metadata)
                .as("a developer that names neither a name nor an email is kept by its id")
                .containsEntry("developer.google.id", "google");
    }

    @Test
    public void checksum_comment_in_dependency_management_lands_in_versions_properties() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.example</groupId>
                                <artifactId>pinned</artifactId>
                                <version>2.0.0</version>
                                <!--Checksum/SHA256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties versions = SequencedProperties.ofFiles(results.get("maven/module-/manifests")
                .resolve(BuildStep.VERSIONS));
        assertThat(versions.getProperty("main/maven/com.example/pinned")).isEqualTo("2.0.0 SHA256/cafebabe");
    }

    @Test
    public void checksum_comment_inside_a_direct_dependency_is_ignored() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.11.3</version>
                            <scope>test</scope>
                            <!--Checksum/SHA256/cafebabe-->
                        </dependency>
                        <dependency>
                            <groupId>com.example</groupId>
                            <artifactId>no-pin</artifactId>
                            <version>1.0.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();

        SequencedProperties testRequires = SequencedProperties.ofFiles(results.get("maven/test-module-/manifests")
                .resolve(BuildStep.REQUIRES));
        assertThat(testRequires.getProperty("main/compile/maven/org.junit.jupiter/junit-jupiter/5.11.3")).isEmpty();
        assertThat(testRequires.getProperty("main/runtime/maven/org.junit.jupiter/junit-jupiter/5.11.3")).isEmpty();

        SequencedProperties mainRequires = SequencedProperties.ofFiles(results.get("maven/module-/manifests")
                .resolve(BuildStep.REQUIRES));
        assertThat(mainRequires.getProperty("main/compile/maven/com.example/no-pin/1.0.0")).isEmpty();
        assertThat(mainRequires.getProperty("main/runtime/maven/com.example/no-pin/1.0.0")).isEmpty();
    }

    @Test
    public void main_class_pom_property_lands_in_module_properties_main_module() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <mainClass>com.example.Entry</mainClass>
                    </properties>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        Files.writeString(Files.createDirectories(project.resolve("src/test/java")).resolve("source"), "bar");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties mainModule = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.MODULE));
        assertThat(mainModule.getProperty("main")).isEqualTo("com.example.Entry");
        SequencedProperties testModule = SequencedProperties.ofFiles(
                results.get("maven/test-module-/manifests").resolve(BuildStep.MODULE));
        assertThat(testModule.getProperty("main")).isNull();
    }

    @Test
    public void absent_main_class_pom_property_leaves_module_properties_without_main_key() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("src/main/java")).resolve("source"), "foo");
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule("maven", new MavenProject(project, "maven", mavenRepository, mavenPomResolver));
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        SequencedProperties mainModule = SequencedProperties.ofFiles(
                results.get("maven/module-/manifests").resolve(BuildStep.MODULE));
        assertThat(mainModule.getProperty("main")).isNull();
    }

    @Test
    public void module_descriptor_searches_the_scoped_configuration_folder_first() {
        MavenProject.MavenModuleDescriptor main = new MavenProject.MavenModuleDescriptor("module-app",
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Path.of("app"));
        assertThat(main.configurations()).containsExactly(
                Path.of("app/src/main/build.jenesis"),
                Path.of("app/build.jenesis"));
        MavenProject.MavenModuleDescriptor test = new MavenProject.MavenModuleDescriptor("test-module-app",
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Path.of("app"));
        assertThat(test.configurations()).containsExactly(
                Path.of("app/src/test/build.jenesis"),
                Path.of("app/build.jenesis"));
        MavenProject.MavenModuleDescriptor unlocated = new MavenProject.MavenModuleDescriptor("module-app",
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                null);
        assertThat(unlocated.configurations()).isEmpty();
    }
}
