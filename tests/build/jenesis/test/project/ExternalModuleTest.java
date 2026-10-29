package build.jenesis.test.project;

import module java.base;
import module java.compiler;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.project.ExternalModule;
import build.jenesis.step.ProcessHandler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExternalModuleTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    private static Path shared;

    private static Path jenesisJar;

    @TempDir(cleanup = CleanupMode.NEVER)
    private Path root, work;

    private BuildExecutor buildExecutor;

    @BeforeAll
    public static void locateJenesisJar() throws Exception {
        Path location = Path.of(BuildExecutor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
            Path target = shared.resolve("build.jenesis.jar");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(target))) {
                Files.walkFileTree(location, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entry = location.relativize(file).toString().replace(File.separatorChar, '/');
                        jar.putNextEntry(new JarEntry(entry));
                        Files.copy(file, jar);
                        jar.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            jenesisJar = target;
        } else {
            jenesisJar = location;
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        buildExecutor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }

    @AfterEach
    public void tearDown() {
        buildExecutor = null;
        System.gc();
    }

    @Test
    public void can_load_and_run_modular_plugin() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), "hello");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps).containsKey("external/marker");
        assertThat(steps.get("external/marker").resolve("out.txt")).content().isEqualTo("hello");
    }

    @Test
    public void loads_a_plugin_named_by_a_maven_coordinate_with_its_closure_on_a_class_path() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                null,
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"),
                                            String.valueOf(Plugin.class.getModule().isNamed()));
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """,
                        "META-INF/services/build.jenesis.BuildModuleFactory", "test.plugin.Plugin\n"));
        Path maven = work.resolve("maven");
        Files.copy(jenesisJar, Files.createDirectories(maven.resolve("build/jenesis/build.jenesis/1.0"))
                .resolve("build.jenesis-1.0.jar"));
        Files.writeString(maven.resolve("build/jenesis/build.jenesis/1.0/build.jenesis-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>build.jenesis</groupId>
                    <artifactId>build.jenesis</artifactId>
                    <version>1.0</version>
                </project>""");
        Files.copy(pluginJar, Files.createDirectories(maven.resolve("test/plugin/1.0")).resolve("plugin-1.0.jar"));
        Files.writeString(maven.resolve("test/plugin/1.0/plugin-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>test</groupId>
                    <artifactId>plugin</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>build.jenesis</groupId>
                            <artifactId>build.jenesis</artifactId>
                            <version>1.0</version>
                        </dependency>
                    </dependencies>
                </project>""");

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "maven/test/plugin/1.0",
                "main",
                Map.of("maven", new MavenDefaultRepository(maven.toUri(), null, Map.of(), null)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))));

        assertThat(buildExecutor.execute().get("external/marker").resolve("out.txt"))
                .as("a plugin named by a Maven coordinate runs from a class path")
                .hasContent("false");
        assertThat(ProcessHandler.nest(() -> { }))
                .as("a plugin linked against the engine's own classes leaves no process scope of the build open")
                .isFalse();
    }

    @Test
    public void keeps_running_a_warm_plugin_after_the_folder_the_build_that_warmed_it_resolved_it_into_is_gone()
            throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                null,
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"),
                                            Plugin.class.getProtectionDomain().getCodeSource().getLocation().toString());
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """,
                        "META-INF/services/build.jenesis.BuildModuleFactory", "test.plugin.Plugin\n"));
        Path maven = work.resolve("maven");
        Files.copy(jenesisJar, Files.createDirectories(maven.resolve("build/jenesis/build.jenesis/1.0"))
                .resolve("build.jenesis-1.0.jar"));
        Files.writeString(maven.resolve("build/jenesis/build.jenesis/1.0/build.jenesis-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>build.jenesis</groupId>
                    <artifactId>build.jenesis</artifactId>
                    <version>1.0</version>
                </project>""");
        Files.copy(pluginJar, Files.createDirectories(maven.resolve("test/warmed/1.0")).resolve("warmed-1.0.jar"));
        Files.writeString(maven.resolve("test/warmed/1.0/warmed-1.0.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <groupId>test</groupId>
                    <artifactId>warmed</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>build.jenesis</groupId>
                            <artifactId>build.jenesis</artifactId>
                            <version>1.0</version>
                        </dependency>
                    </dependencies>
                </project>""");

        List<String> loaded = new ArrayList<>();
        for (String build : List.of("first", "second")) {
            if (build.equals("second")) {
                try (Stream<Path> walk = Files.walk(work.resolve("first"))) {
                    for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
            BuildExecutor executor = BuildExecutor.of(Files.createDirectories(work.resolve(build)),
                    Duration.ZERO,
                    new HashDigestFunction("MD5"),
                    BuildStepHashFunction.ofSerializationDigest("MD5"),
                    BuildExecutorCallback.nop(),
                    BuildExecutorCache.nop(),
                    false,
                    false,
                    0);
            executor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                    "maven/test/warmed/1.0",
                    "warmed",
                    Map.of("maven", new MavenDefaultRepository(maven.toUri(), null, Map.of(), null)),
                    Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))));
            loaded.add(Files.readString(executor.execute().get("external/marker").resolve("out.txt")));
        }

        assertThat(loaded)
                .as("a warm plugin reads its jars from a copy of its own, not from the folder of the build that warmed it")
                .hasSize(2)
                .allSatisfy(location -> assertThat(location).doesNotContain("/first/").doesNotContain("/second/"));
    }

    @Test
    public void loads_a_modular_plugin_into_its_named_module_with_its_own_resources() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.io.InputStream;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    try (InputStream greeting = Plugin.class.getResourceAsStream("greeting.txt")) {
                                        Files.writeString(context.next().resolve("out.txt"),
                                                Plugin.class.getModule().getName() + " " + new String(greeting.readAllBytes()));
                                    }
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """,
                        "test/plugin/greeting.txt", "hello"));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThat(buildExecutor.execute().get("external/marker").resolve("out.txt"))
                .as("a plugin declaring a module is loaded as that module and finds its resources there")
                .hasContent("test.plugin hello");
    }

    @Test
    public void plugin_step_can_read_predecessor_argument_folder() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("first", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("payload.txt"), "produced");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                                executor.addStep("second", (_, context, args) -> {
                                    Path predecessor = args.get("first").folder();
                                    String content = Files.readString(predecessor.resolve("payload.txt"));
                                    Files.writeString(context.next().resolve("out.txt"), "seen:" + content);
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                }, "first");
                            }
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/second").resolve("out.txt")).content().isEqualTo("seen:produced");
    }

    @Test
    public void plugin_reads_the_folder_an_input_binds() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepArgument;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.Map;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("seen", (_, context, args) -> {
                                    StringBuilder seen = new StringBuilder();
                                    for (Map.Entry<String, BuildStepArgument> arg : args.entrySet()) {
                                        Path schema = arg.getValue().folder().resolve("xjc/schema.xsd");
                                        if (Files.isRegularFile(schema)) {
                                            seen.append(arg.getKey()).append('=').append(Files.readString(schema));
                                        }
                                    }
                                    Files.writeString(context.next().resolve("out.txt"), seen);
                                    Files.writeString(context.next().resolve("order.txt"), String.join(",", args.keySet()));
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                }, inherited.sequencedKeySet());
                            }
                        }
                        """));
        Path contracts = Files.createDirectories(work.resolve("contracts"));
        Files.writeString(contracts.resolve("schema.xsd"), "<schema/>");
        buildExecutor.addSource("handed", Files.createDirectories(work.resolve("handed")));

        buildExecutor.addModule("external",
                ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .inputs(new LinkedHashMap<>(Map.of("schemas", new LinkedHashMap<>(Map.of(Path.of("xjc"), contracts))))),
                "handed");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/seen").resolve("out.txt")).content().isEqualTo("../inputs/schemas=<schema/>");
        assertThat(steps.get("external/seen").resolve("order.txt"))
                .as("what the hook point hands the plugin comes first, as a transform reads its classes there")
                .content()
                .startsWith("../../handed,");
    }

    @Test
    public void resolves_a_plugin_without_running_it() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                throw new IllegalStateException("must not be loaded");
                            }
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .resolution());

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.keySet()).anyMatch(key -> key.startsWith("external/dependencies"));
        assertThat(steps.keySet()).noneMatch(key -> key.startsWith("external/delegate"));
    }

    @Test
    public void refuses_a_plugin_without_a_pinned_checksum_under_strict_pinning() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; }",
                Map.of());

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(
                new Environment(Map.of("dependency.pin", "strict")),
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .resolution());

        assertThatThrownBy(buildExecutor::execute)
                .as("the plugin's closure is checked as strictly as the project's own dependencies")
                .hasStackTraceContaining("No checksum pinned for");
    }

    @Test
    public void plugin_step_can_query_predecessor_argument_change_status() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("first", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("payload.txt"), "produced");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                                executor.addStep("second", (_, context, args) -> {
                                    boolean changed = args.get("first").hasChanged();
                                    Files.writeString(context.next().resolve("out.txt"), "changed:" + changed);
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                }, "first");
                            }
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/second").resolve("out.txt")).content().isEqualTo("changed:true");
    }

    @Test
    public void plugin_can_add_nested_module() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addModule("inner", (sub, _) -> {
                                    sub.addStep("marker", (_, context, _) -> {
                                        Files.writeString(context.next().resolve("out.txt"), "nested");
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    });
                                });
                            }
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/inner/marker").resolve("out.txt")).content().isEqualTo("nested");
    }

    @Test
    public void resolves_named_provider_when_load_build_module_set() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildModuleName;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        @BuildModuleName("foo")
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), "named");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .buildModuleName("foo"));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/marker").resolve("out.txt")).content().isEqualTo("named");
    }

    @Test
    public void fails_when_no_provider_with_requested_name() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildModuleName;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        @BuildModuleName("foo")
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {}
                        }
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .buildModuleName("bar"));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("named bar");
    }

    @Test
    public void fails_when_module_has_no_provider() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; }",
                Map.of("test/plugin/Empty.java", """
                        package test.plugin;
                        public class Empty {}
                        """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No unnamed");
    }

    @Test
    public void fails_when_multiple_unnamed_providers() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.PluginA, test.plugin.PluginB; }",
                Map.of(
                        "test/plugin/PluginA.java", """
                                package test.plugin;
                                import build.jenesis.BuildExecutor;
                                import build.jenesis.BuildExecutorModule;
                                import java.nio.file.Path;
                                import java.util.SequencedMap;
                                public class PluginA implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                                    public BuildExecutorModule create(build.jenesis.Environment environment,
                                                                      java.util.SequencedMap<String, String> properties,
                                                                      build.jenesis.Permits permits,
                                                                      String group,
                                                                      java.util.Map<String, build.jenesis.Repository> repositories,
                                                                      java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                        return this;
                                    }
                                    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {}
                                }
                                """,
                        "test/plugin/PluginB.java", """
                                package test.plugin;
                                import build.jenesis.BuildExecutor;
                                import build.jenesis.BuildExecutorModule;
                                import java.nio.file.Path;
                                import java.util.SequencedMap;
                                public class PluginB implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                                    public BuildExecutorModule create(build.jenesis.Environment environment,
                                                                      java.util.SequencedMap<String, String> properties,
                                                                      build.jenesis.Permits permits,
                                                                      String group,
                                                                      java.util.Map<String, build.jenesis.Repository> repositories,
                                                                      java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                        return this;
                                    }
                                    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {}
                                }
                                """));

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple");
    }

    @Test
    public void honors_pinned_version_and_checksum() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), "hello");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        buildExecutor.addStep("manifests", (_, context, _) -> {
            Files.writeString(context.next().resolve("versions.properties"),
                    "main/module/build.jenesis=1.0.0 SHA-256/" + sha256(jenesisJar) + "\n");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        });
        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))), "manifests");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/marker").resolve("out.txt")).content().isEqualTo("hello");
    }

    @Test
    public void fails_when_pinned_checksum_mismatches() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), "hello");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        buildExecutor.addStep("manifests", (_, context, _) -> {
            Files.writeString(context.next().resolve("versions.properties"),
                    "main/module/build.jenesis=1.0.0 SHA-256/" + "00".repeat(32) + "\n");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        });
        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))), "manifests");

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mismatched digest");
    }

    @Test
    public void fails_clearly_when_module_uses_newer_build_executor_api() throws IOException {
        Path newerJenesisJar = jenesisJarWithExtraBuildExecutorMethod(
                work.resolve("build.jenesis-newer.jar"), "jenesisFutureMethod");
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return this;
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                executor.jenesisFutureMethod();
                            }
                        }
                        """),
                newerJenesisJar);

        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", newerJenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("jenesisFutureMethod")
                .hasMessageContaining("upgrade Jenesis");
    }

    @Test
    public void creates_a_plugin_from_the_values_the_environment_the_permits_and_the_group_of_its_build() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildModuleFactory;
                        import build.jenesis.BuildStepResult;
                        import build.jenesis.Environment;
                        import build.jenesis.Repository;
                        import build.jenesis.Resolver;
                        import java.io.IOException;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.Map;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements BuildModuleFactory {
                            public BuildExecutorModule create(Environment environment,
                                                              SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) throws IOException {
                                String text = properties.get("greeting")
                                        + " " + environment.value("sample.name", build.jenesis.Make.Setting.PROJECT)
                                        + " " + (permits instanceof build.jenesis.Permits.Counted counted
                                                ? counted.semaphore().availablePermits()
                                                : -1)
                                        + " " + group;
                                environment.out().accept(text);
                                return new Module(text);
                            }
                            record Module(String text) implements BuildExecutorModule {
                                public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                    String text = this.text;
                                    executor.addStep("marker", (_, context, _) -> {
                                        Files.writeString(context.next().resolve("out.txt"), text);
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    });
                                }
                            }
                        }
                        """));
        List<String> printed = new ArrayList<>();
        buildExecutor.addModule("external", ExternalModule.ofEnvironment(
                new Environment(Map.of("sample.name", "world", "process.concurrency", "3")).out(printed::add),
                "module/test.plugin",
                "greeter",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(new LinkedHashMap<>(Map.of("greeting", "hello"))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/marker").resolve("out.txt"))
                .as("the factory creates the plugin from its values, the settings, the process permits and the group"
                        + " it is handed by the build that loads it")
                .content()
                .isEqualTo("hello world 3 greeter");
        assertThat(printed)
                .as("what the plugin prints reaches the output of the build that loads it")
                .containsExactly("hello world 3 greeter");
    }

    @Test
    public void creates_a_plugin_that_resolves_its_tool_with_the_repositories_and_resolvers_of_its_build() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildModuleFactory;
                        import build.jenesis.BuildStep;
                        import build.jenesis.BuildStepArgument;
                        import build.jenesis.BuildStepResult;
                        import build.jenesis.Environment;
                        import build.jenesis.Repository;
                        import build.jenesis.Resolver;
                        import build.jenesis.SequencedProperties;
                        import build.jenesis.step.Dependencies;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.Map;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements BuildModuleFactory {
                            public BuildExecutorModule create(Environment environment,
                                                              SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
                                Dependencies dependencies = new Dependencies(repositories, resolvers).group(group + ":tool");
                                return (executor, inherited) -> {
                                    executor.addStep("required", (_, context, _) -> {
                                        SequencedProperties requires = new SequencedProperties();
                                        requires.setProperty(group + ":tool/runtime/module/test.plugin", "");
                                        requires.store(context.next().resolve(BuildStep.REQUIRES));
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    });
                                    executor.addModule("dependencies", dependencies, "required");
                                    executor.addStep("check", (_, context, arguments) -> {
                                        StringBuilder names = new StringBuilder();
                                        for (BuildStepArgument argument : arguments.values()) {
                                            for (Path jar : Dependencies.select(argument.folder(), group + ":tool", "runtime")) {
                                                names.append(jar.getFileName()).append(',');
                                            }
                                        }
                                        Files.writeString(context.next().resolve("out.txt"), names);
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    }, "dependencies");
                                };
                            }
                        }
                        """));
        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "tool",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("external/check").resolve("out.txt"))
                .as("the plugin resolves its tool through the repositories and resolvers of the build that loads it")
                .content()
                .contains("test.plugin");
    }

    @Test
    public void hands_on_a_plugin_whose_own_child_is_named_dependencies() throws IOException {
        Path pluginJar = compileModule(work.resolve("plugin"),
                work.resolve("plugin.jar"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildModuleFactory;
                        import build.jenesis.BuildStepResult;
                        import build.jenesis.Environment;
                        import build.jenesis.Repository;
                        import build.jenesis.Resolver;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.Map;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements BuildModuleFactory {
                            public BuildExecutorModule create(Environment environment,
                                                              SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
                                return (executor, inherited) -> {
                                    executor.addModule("dependencies", (nested, _) -> nested.addStep("resolve", (_, context, _) -> {
                                        Files.writeString(context.next().resolve("resolved.txt"), "resolved");
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    }));
                                    executor.addStep("generate", (_, context, _) -> {
                                        Files.writeString(context.next().resolve("generated.txt"), "generated");
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    }, "dependencies");
                                };
                            }
                        }
                        """));
        buildExecutor.addModule("external", ExternalModule.ofEnvironment(Environment.NONE,
                "module/test.plugin",
                "main",
                Map.of("module", versionInsensitive(Map.of(
                        "test.plugin", pluginJar,
                        "build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));
        buildExecutor.addModule("consumer", (executor, inherited) -> executor.addStep("read",
                (_, context, arguments) -> {
                    Files.writeString(context.next().resolve("read.txt"), String.join(",", arguments.sequencedKeySet()));
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                },
                inherited.sequencedKeySet()), "external");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("consumer/read").resolve("read.txt"))
                .as("what depends on the plugin reads its steps by their names and the closure it was loaded from apart")
                .content()
                .contains("../external/generate")
                .contains("../external/dependencies/resolve")
                .contains("../external/" + "+closure");
    }

    private static Path jenesisJarWithExtraBuildExecutorMethod(Path out, String methodName) throws IOException {
        String entry = BuildExecutor.class.getName().replace('.', '/') + ".class";
        byte[] original;
        try (FileSystem fileSystem = FileSystems.newFileSystem(jenesisJar)) {
            original = Files.readAllBytes(fileSystem.getPath(entry));
        }
        ClassFile classFile = ClassFile.of();
        byte[] modified = classFile.transformClass(classFile.parse(original),
                ((ClassTransform) (builder, element) -> builder.with(element))
                        .andThen(ClassTransform.endHandler(builder -> builder.withMethod(
                                methodName,
                                MethodTypeDesc.of(ConstantDescs.CD_void),
                                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT,
                                _ -> {}))));
        Files.copy(jenesisJar, out);
        try (FileSystem fileSystem = FileSystems.newFileSystem(out)) {
            Files.write(fileSystem.getPath(entry), modified);
        }
        return out;
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path compileModule(Path sourceDir,
                                      Path jarOut,
                                      String moduleInfo,
                                      Map<String, String> sources) throws IOException {
        return compileModule(sourceDir, jarOut, moduleInfo, sources, jenesisJar);
    }

    private static Path compileModule(Path sourceDir,
                                      Path jarOut,
                                      String moduleInfo,
                                      Map<String, String> sources,
                                      Path jenesis) throws IOException {
        Files.createDirectories(sourceDir);
        List<Path> files = new ArrayList<>();
        if (moduleInfo != null) {
            Files.writeString(sourceDir.resolve("module-info.java"), moduleInfo);
            files.add(sourceDir.resolve("module-info.java"));
        }
        Path classes = Files.createDirectories(sourceDir.resolveSibling(sourceDir.getFileName() + "-classes"));
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            boolean source = entry.getKey().endsWith(".java");
            Path file = (source ? sourceDir : classes).resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
            if (source) {
                files.add(file);
            }
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
            fileManager.setLocationFromPaths(moduleInfo == null
                    ? StandardLocation.CLASS_PATH
                    : StandardLocation.MODULE_PATH, List.of(jenesis));
            boolean success = compiler.getTask(null,
                    fileManager,
                    diagnostics,
                    null,
                    null,
                    fileManager.getJavaFileObjectsFromPaths(files)).call();
            if (!Boolean.TRUE.equals(success)) {
                throw new IllegalStateException("Compilation failed: " + diagnostics.getDiagnostics());
            }
        }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarOut))) {
            Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entry = classes.relativize(file).toString().replace(File.separatorChar, '/');
                    jar.putNextEntry(new JarEntry(entry));
                    Files.copy(file, jar);
                    jar.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return jarOut;
    }

    private static Repository versionInsensitive(Map<String, Path> files) {
        return (_, coordinate, _) -> {
            int slash = coordinate.indexOf('/');
            String name = slash < 0 ? coordinate : coordinate.substring(0, slash);
            Path file = files.get(name);
            return file == null ? Optional.empty() : Optional.of(RepositoryItem.ofFile(file));
        };
    }
}
