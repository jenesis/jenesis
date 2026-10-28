package build.jenesis.test.project;

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
import build.jenesis.Language;
import build.jenesis.Observation;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.TestFramework;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.project.InternalModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InternalModuleTest {

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
    public void can_compile_and_run_modular_plugin() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps).containsKey("internal/marker");
        assertThat(steps.get("internal/marker").resolve("out.txt")).content().isEqualTo("hello");
    }

    @Test
    public void hands_on_the_output_of_a_plugin_that_is_a_single_step() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStep;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                BuildStep step = (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), "hello");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                };
                                return step.asModule("marker");
                            }
                        }
                        """));

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));
        buildExecutor.addStep("after", (_, context, arguments) -> {
            Files.copy(arguments.firstEntry().getValue().folder().resolve("out.txt"), context.next().resolve("out.txt"));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, "internal/marker");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("after").resolve("out.txt")).content().isEqualTo("hello");
    }

    @Test
    public void keeps_the_layer_of_a_plugin_warm_for_the_next_build_of_the_same_jars() throws Exception {
        assertThat(created("test.warm", List.of("first", "first"))).containsExactly("1", "2");
    }

    @Test
    public void replaces_the_warm_layer_of_a_plugin_whose_jars_changed() throws Exception {
        assertThat(created("test.edited", List.of("first", "second", "second")))
                .as("a plugin edited between two builds is loaded afresh, and its new layer is then kept")
                .containsExactly("1", "1", "2");
    }

    private List<String> created(String name, List<String> variants) throws Exception {
        List<String> created = new ArrayList<>();
        for (String variant : variants) {
            Path source = writeModuleSource(work.resolve(name),
                    "module " + name + " { requires build.jenesis; provides build.jenesis.BuildModuleFactory with "
                            + name + ".Plugin; }",
                    Map.of(name.replace('.', '/') + "/Plugin.java", """
                            package %s;
                            import build.jenesis.BuildExecutor;
                            import build.jenesis.BuildExecutorModule;
                            import build.jenesis.BuildStepResult;
                            import java.nio.file.Files;
                            import java.nio.file.Path;
                            import java.util.SequencedMap;
                            import java.util.concurrent.CompletableFuture;
                            public class Plugin implements build.jenesis.BuildModuleFactory {
                                private static final String VARIANT = "%s";
                                private static int created;
                                public BuildExecutorModule create(build.jenesis.Environment environment,
                                                                  java.util.SequencedMap<String, String> properties,
                                                                  build.jenesis.Permits permits,
                                                                  String group,
                                                                  java.util.Map<String, build.jenesis.Repository> repositories,
                                                                  java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                    String count = String.valueOf(++created);
                                    return (BuildExecutor executor, SequencedMap<String, Path> inherited) -> executor.addStep("marker", (_, context, _) -> {
                                        Files.writeString(context.next().resolve("created.txt"), count);
                                        return CompletableFuture.completedStage(new BuildStepResult(true));
                                    });
                                }
                            }
                            """.formatted(name, variant)));
            Path folder = Files.createTempDirectory(work, "build");
            BuildExecutor executor = BuildExecutor.of(folder,
                    Duration.ZERO,
                    new HashDigestFunction("MD5"),
                    BuildStepHashFunction.ofSerializationDigest("MD5"),
                    BuildExecutorCallback.nop(),
                    BuildExecutorCache.nop(),
                    false,
                    false,
                    0);
            executor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                    "module",
                    "main",
                    source,
                    Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                    Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));
            created.add(Files.readString(executor.execute().get("internal/marker").resolve("created.txt")));
        }
        return created;
    }

    @Test
    public void creates_the_observation_of_a_plugin_that_observes_the_tests() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.ObservabilityFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildStepResult;
                        import build.jenesis.ObservabilityEngine;
                        import build.jenesis.Observation;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.LinkedHashMap;
                        import java.util.List;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements build.jenesis.ObservabilityFactory {
                            public Observation create(build.jenesis.Environment environment,
                                                      SequencedMap<String, String> properties,
                                                      build.jenesis.Permits permits,
                                                      String group,
                                                      java.util.Map<String, build.jenesis.Repository> repositories,
                                                      java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return new Observation(new Engine(group, properties.get("option")),
                                        (executor, _) -> executor.addStep("report", (_, context, _) -> {
                                            Files.writeString(context.next().resolve("report.txt"), group);
                                            return CompletableFuture.completedStage(new BuildStepResult(true));
                                        }));
                            }
                            public record Engine(String name, String option) implements ObservabilityEngine {
                                public SequencedMap<String, String> coordinates() {
                                    return new LinkedHashMap<>();
                                }
                                public List<String> commands(SequencedMap<String, Path> resolved, Path output) {
                                    return List.of(option + "=" + output.getFileName());
                                }
                            }
                        }
                        """));
        InternalModule internal = InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "probe",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(new LinkedHashMap<>(Map.of("option", "-Dprobe")));

        buildExecutor.addModule("prepared", internal.preparation());
        buildExecutor.addModule("observed", (executor, inherited) -> {
            SequencedMap<String, Path> prepared = new LinkedHashMap<>();
            inherited.forEach((identity, folder) -> prepared.put(
                    identity.replace("prepared/", ""), folder));
            Observation observation = internal.observation(prepared);
            String engine = observation.engine().name() + " "
                    + observation.engine().commands(Collections.emptyNavigableMap(), Path.of("output"));
            executor.addStep("engine", (_, context, _) -> {
                Files.writeString(context.next().resolve("engine.txt"), engine);
                return CompletableFuture.completedStage(new BuildStepResult(true));
            });
            executor.addModule("report", observation.report());
        }, "prepared");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("observed/engine").resolve("engine.txt"))
                .as("the engine the plugin attaches to the tests is named after its group and takes its values")
                .content()
                .isEqualTo("probe [-Dprobe=output]");
        assertThat(steps.get("observed/report/report").resolve("report.txt")).content().isEqualTo("probe");
    }

    @Test
    public void creates_the_language_of_a_plugin_that_compiles_one() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.LanguageFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import build.jenesis.Language;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.LinkedHashSet;
                        import java.util.List;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements build.jenesis.LanguageFactory {
                            public Language create(build.jenesis.Environment environment,
                                                   SequencedMap<String, String> properties,
                                                   build.jenesis.Permits permits,
                                                   String group,
                                                   java.util.Map<String, build.jenesis.Repository> repositories,
                                                   java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                String target = properties.get("target");
                                BuildExecutorModule compiler = (BuildExecutor executor, SequencedMap<String, Path> inherited) ->
                                        executor.addStep("compile", (_, context, _) -> {
                                            Files.writeString(context.next().resolve("compiled.txt"), target);
                                            return CompletableFuture.completedStage(new BuildStepResult(true));
                                        });
                                return new Language(new LinkedHashSet<>(List.of(".greet")), Language.Order.AFTER_JAVAC, compiler, null);
                            }
                        }
                        """));
        InternalModule internal = InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(new LinkedHashMap<>(Map.of("target", "jvm")));

        buildExecutor.addModule("prepared", internal.preparation());
        buildExecutor.addModule("language", (executor, inherited) -> {
            SequencedMap<String, Path> prepared = new LinkedHashMap<>();
            inherited.forEach((identity, folder) -> prepared.put(identity.replace("prepared/", ""), folder));
            Language language = internal.language(prepared);
            assertThat(language.extensions()).containsExactly(".greet");
            assertThat(language.order()).isEqualTo(Language.Order.AFTER_JAVAC);
            assertThat(language.documentation()).isNull();
            executor.addModule("compiler", language.compiler());
        }, "prepared");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("language/compiler/"))
                .map(entry -> entry.getValue().resolve("compiled.txt"))
                .filter(Files::isRegularFile)
                .findFirst())
                .as("the compiler the plugin's language names runs through the bridge with the values it was handed")
                .hasValueSatisfying(file -> assertThat(file).hasContent("jvm"));
    }

    @Test
    public void creates_the_test_framework_of_a_plugin_that_runs_the_tests() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.TestFrameworkFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.TestFramework;
                        import java.lang.module.ModuleDescriptor;
                        import java.nio.file.Path;
                        import java.util.List;
                        import java.util.Set;
                        import java.util.SequencedMap;
                        import java.util.SequencedSet;
                        public class Factory implements build.jenesis.TestFrameworkFactory {
                            public TestFramework create(build.jenesis.Environment environment,
                                                        SequencedMap<String, String> properties,
                                                        build.jenesis.Permits permits,
                                                        String group,
                                                        java.util.Map<String, build.jenesis.Repository> repositories,
                                                        java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return new Runner(properties.get("runner"));
                            }
                            public record Runner(String runnerClass) implements TestFramework {
                                public String runnerModule() {
                                    return null;
                                }
                                public boolean isMarkedBy(ModuleDescriptor module) {
                                    return module.name().equals("example.tests");
                                }
                                public Set<String> reflectingModules() {
                                    return Set.of();
                                }
                                public List<String> arguments(Path supplement,
                                                              Path output,
                                                              SequencedSet<String> classes,
                                                              SequencedMap<String, SequencedSet<String>> methods,
                                                              boolean parallel,
                                                              boolean reporting) {
                                    return List.copyOf(classes);
                                }
                            }
                        }
                        """));
        InternalModule internal = InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(new LinkedHashMap<>(Map.of("runner", "example.Runner")));

        buildExecutor.addModule("prepared", internal.preparation());
        buildExecutor.addModule("framework", (executor, inherited) -> {
            SequencedMap<String, Path> prepared = new LinkedHashMap<>();
            inherited.forEach((identity, folder) -> prepared.put(identity.replace("prepared/", ""), folder));
            TestFramework framework = internal.framework(prepared);
            String described = framework.runnerClass() + " "
                    + framework.isMarkedBy(ModuleDescriptor.newModule("example.tests").build()) + " "
                    + framework.isMarkedBy(ModuleDescriptor.newModule("example.other").build()) + " "
                    + framework.arguments(Path.of("supplement"),
                            Path.of("output"),
                            new LinkedHashSet<>(List.of("example.SampleTest")),
                            Collections.emptyNavigableMap(),
                            false,
                            false);
            executor.addStep("described", (_, context, _) -> {
                Files.writeString(context.next().resolve("framework.txt"), described);
                return CompletableFuture.completedStage(new BuildStepResult(true));
            });
        }, "prepared");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("framework/described").resolve("framework.txt"))
                .as("the framework the plugin creates answers through the bridge with the values it was handed")
                .content()
                .isEqualTo("example.Runner true false [example.SampleTest]");
    }

    @Test
    public void hands_the_plugin_the_repositories_to_resolve_its_tool_with_apart_from_those_it_is_loaded_from()
            throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.util.TreeSet;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements build.jenesis.BuildModuleFactory {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                String handed = new TreeSet<>(repositories.keySet()) + " " + new TreeSet<>(resolvers.keySet());
                                return (executor, _) -> executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), handed);
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "greeting",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .repositories(Map.of("project", versionInsensitive(Map.of())))
                .resolvers(Map.of("project", Resolver.identity())));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt"))
                .as("the plugin resolves its tool with the repositories of the build that loads it")
                .content()
                .isEqualTo("[project] [project]");
    }

    @Test
    public void hands_the_plugin_the_group_to_resolve_its_tool_in() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements build.jenesis.BuildModuleFactory {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return (executor, _) -> executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), group);
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "greeting",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt"))
                .as("the plugin is handed the group its own closure is resolved in, under which it names its tool's")
                .content()
                .isEqualTo("greeting");
    }

    @Test
    public void fails_a_plugin_step_that_resolves_in_a_group_not_named_after_the_plugin() throws IOException {
        buildExecutor.addModule("internal", resolvingIn("greeting:tool/runtime/module/tool", "other/runtime/module/tool"));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The plugin greeting resolved other/runtime/module/tool in the group other")
                .hasMessageContaining("greeting:tool");
    }

    @Test
    public void accepts_a_plugin_step_that_resolves_in_groups_named_after_the_plugin() throws IOException {
        buildExecutor.addModule("internal", resolvingIn("greeting:tool/runtime/module/tool",
                "greeting:processor/runtime/module/processor"));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/resolved").resolve(BuildStep.DEPENDENCIES))
                .as("a plugin resolves what it runs in a group of its own name and any purpose")
                .exists();
    }

    private InternalModule resolvingIn(String... keys) throws IOException {
        StringBuilder index = new StringBuilder();
        for (String key : keys) {
            index.append(key.replace(":", "\\\\:")).append("=tool.jar\\n");
        }
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Factory; }",
                Map.of("test/plugin/Factory.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStep;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.util.concurrent.CompletableFuture;
                        public class Factory implements build.jenesis.BuildModuleFactory {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return (executor, _) -> executor.addStep("resolved", (_, context, _) -> {
                                    Files.writeString(context.next().resolve(BuildStep.DEPENDENCIES), "%s");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """.formatted(index)));
        return InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "greeting",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)));
    }

    @Test
    public void runs_a_plugin_step_again_when_only_the_code_of_the_plugin_changed() throws Exception {
        String plugin = """
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
                            Files.writeString(context.next().resolve("out.txt"), "%s");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        });
                    }
                }
                """;
        String moduleInfo = "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }";
        Path source = writeModuleSource(work.resolve("plugin"),
                moduleInfo,
                Map.of("test/plugin/Plugin.java", plugin.formatted("before")));
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));
        assertThat(buildExecutor.execute().get("internal/marker").resolve("out.txt")).content().isEqualTo("before");

        writeModuleSource(work.resolve("plugin"), moduleInfo, Map.of("test/plugin/Plugin.java", plugin.formatted("after")));
        Path copy = work.resolve("copy");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.toList()) {
                Files.copy(file, copy.resolve(root.relativize(file).toString()), LinkOption.NOFOLLOW_LINKS);
            }
        }
        buildExecutor = BuildExecutor.of(copy,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));
        assertThat(buildExecutor.execute().get("internal/marker").resolve("out.txt")).content()
                .as("a copy of the first build's target holds the old output, and the step's serialised form is unchanged,"
                        + " so only the code of the plugin can tell it apart")
                .isEqualTo("after");
    }

    @Test
    public void hands_its_properties_to_the_constructor_of_a_plugin_that_takes_them() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.Collections;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        public class Plugin implements build.jenesis.BuildModuleFactory, BuildExecutorModule {
                            public BuildExecutorModule create(build.jenesis.Environment environment,
                                                              java.util.SequencedMap<String, String> properties,
                                                              build.jenesis.Permits permits,
                                                              String group,
                                                              java.util.Map<String, build.jenesis.Repository> repositories,
                                                              java.util.Map<String, build.jenesis.Resolver> resolvers) {
                                return new Plugin(properties);
                            }
                            private final String greeting;
                            public Plugin() {
                                this(Collections.emptyNavigableMap());
                            }
                            public Plugin(SequencedMap<String, String> properties) {
                                greeting = properties.get("greeting");
                            }
                            public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
                                String text = greeting;
                                executor.addStep("marker", (_, context, _) -> {
                                    Files.writeString(context.next().resolve("out.txt"), text);
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));
        SequencedMap<String, String> properties = new LinkedHashMap<>();
        properties.put("greeting", "configured");

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(properties));

        assertThat(buildExecutor.execute().get("internal/marker").resolve("out.txt")).content().isEqualTo("configured");
    }

    @Test
    public void creates_a_plugin_without_a_properties_constructor_as_without_properties() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(Collections.emptyNavigableMap()));

        assertThat(buildExecutor.execute().get("internal/marker").resolve("out.txt")).content().isEqualTo("hello");
    }

    @Test
    public void plugin_step_can_read_predecessor_argument_folder() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/second").resolve("out.txt")).content().isEqualTo("seen:produced");
    }

    @Test
    public void plugin_can_add_nested_module() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/inner/marker").resolve("out.txt")).content().isEqualTo("nested");
    }

    @Test
    public void hides_what_the_modules_of_a_plugin_resolve_to_nothing() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                "module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }",
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Path;
                        import java.util.Optional;
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
                                executor.addStep("hidden", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
                                executor.addStep("shown", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
                                executor.addModule("inner", new BuildExecutorModule() {
                                    public void accept(BuildExecutor sub, SequencedMap<String, Path> ignored) {
                                        sub.addStep("hidden", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
                                        sub.addStep("shown", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));
                                    }
                                    public Optional<String> resolve(String path) {
                                        return path.equals("hidden") ? Optional.empty() : Optional.of(path);
                                    }
                                });
                            }
                            public Optional<String> resolve(String path) {
                                return path.equals("hidden") ? Optional.empty() : Optional.of(path);
                            }
                        }
                        """));

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps).containsKeys("internal/shown", "internal/inner/shown");
        assertThat(steps)
                .as("a plugin's modules decide what they expose, as they would in the build itself")
                .doesNotContainKeys("internal/hidden", "internal/inner/hidden");
    }

    @Test
    public void fails_when_source_lacks_module_info() throws IOException {
        Path source = Files.createDirectory(work.resolve("non-modular"));
        Files.writeString(source.resolve("Foo.java"), "public class Foo {}");

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not modular");
    }

    @Test
    public void resolves_named_provider_when_load_build_module_set() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .buildModuleName("foo"));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt")).content().isEqualTo("named");
    }

    @Test
    public void fails_when_no_provider_with_requested_name() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .buildModuleName("bar"));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("named bar");
    }

    @Test
    public void fails_when_only_annotated_provider_exists_without_load_build_module() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No unnamed");
    }

    @Test
    public void fails_when_multiple_unnamed_providers() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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

        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple");
    }

    @Test
    public void honors_pinned_version_and_checksum() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))), "manifests");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt")).content().isEqualTo("hello");
    }

    @Test
    public void fails_when_pinned_checksum_mismatches() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))), "manifests");

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mismatched digest");
    }

    @Test
    public void fails_when_group_pinned_checksum_mismatches_constrains_runtime_scope() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))), "manifests");

        assertThatThrownBy(() -> buildExecutor.execute())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mismatched digest");
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void honours_the_modules_own_pin() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
                """
                        /** @jenesis.pin build.jenesis 9.9.9 */
                        module test.plugin { requires build.jenesis; provides build.jenesis.BuildModuleFactory with test.plugin.Plugin; }
                        """,
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
                                    Files.writeString(context.next().resolve("out.txt"), "pinned");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));

        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", recording(asked, Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt")).content().isEqualTo("pinned");
        assertThat(asked).isNotEmpty().allSatisfy(coordinate ->
                assertThat(coordinate).isEqualTo("build.jenesis/9.9.9"));
    }

    @Test
    public void takes_no_alias_of_the_build_it_is_handed() throws IOException {
        Path source = writeModuleSource(work.resolve("plugin"),
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
                                    Files.writeString(context.next().resolve("out.txt"), "isolated");
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));
        Path outer = Files.createDirectories(work.resolve("outer"));
        SequencedProperties aliases = new SequencedProperties();
        aliases.setProperty("main/module/build.jenesis", "sample/elsewhere");
        aliases.store(outer.resolve(BuildStep.ALIASES));
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        buildExecutor.addSource("outer", outer);
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", recording(asked, Map.of("build.jenesis", jenesisJar))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true))), "outer");

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt")).content().isEqualTo("isolated");
        assertThat(asked)
                .as("an alias of the build the plugin runs in renames nothing the plugin requires")
                .isNotEmpty()
                .allSatisfy(coordinate -> assertThat(coordinate).startsWith("build.jenesis"));
    }

    @Test
    public void resolves_a_required_module_through_its_alias() throws IOException {
        Path toolSources = writeModuleSource(work.resolve("tool"), "module sample.tool { exports sample.tool; }", Map.of("sample/tool/Tool.java", """
                package sample.tool;
                public class Tool {
                    public static String greeting() {
                        return "aliased";
                    }
                }
                """));
        Path toolClasses = Files.createDirectories(work.resolve("tool-classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", toolClasses.toString(),
                toolSources.resolve("module-info.java").toString(),
                toolSources.resolve("sample/tool/Tool.java").toString())).isZero();
        Path tool = work.resolve("tool.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(tool))) {
            for (String entry : List.of("module-info.class", "sample/tool/Tool.class")) {
                jar.putNextEntry(new JarEntry(entry));
                Files.copy(toolClasses.resolve(entry), jar);
                jar.closeEntry();
            }
        }
        Path source = writeModuleSource(work.resolve("plugin"), """
                        /**
                         * @jenesis.alias sample.tool sample/tool
                         * @jenesis.pin sample/tool 1.0
                         */
                        module test.plugin {
                            requires build.jenesis;
                            requires sample.tool;
                            provides build.jenesis.BuildModuleFactory with test.plugin.Plugin;
                        }
                        """,
                Map.of("test/plugin/Plugin.java", """
                        package test.plugin;
                        import build.jenesis.BuildExecutor;
                        import build.jenesis.BuildExecutorModule;
                        import build.jenesis.BuildStepResult;
                        import java.nio.file.Files;
                        import java.nio.file.Path;
                        import java.util.SequencedMap;
                        import java.util.concurrent.CompletableFuture;
                        import sample.tool.Tool;
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
                                    Files.writeString(context.next().resolve("out.txt"), Tool.greeting());
                                    return CompletableFuture.completedStage(new BuildStepResult(true));
                                });
                            }
                        }
                        """));
        List<String> asked = new ArrayList<>();
        buildExecutor.addModule("internal", InternalModule.ofEnvironment(Environment.NONE,
                "module",
                "main",
                source,
                Map.of("module", versionInsensitive(Map.of("build.jenesis", jenesisJar)),
                        "maven", recording(asked, Map.of("sample", tool))),
                Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true),
                        "maven", Resolver.identity())));

        SequencedMap<String, Path> steps = buildExecutor.execute();
        assertThat(steps.get("internal/marker").resolve("out.txt"))
                .as("a module the module index does not serve is reached through the alias, as in a project")
                .content()
                .isEqualTo("aliased");
        assertThat(asked).containsExactly("sample/tool/1.0");
    }

    private static Path writeModuleSource(Path target, String moduleInfo, Map<String, String> sources) throws IOException {
        Files.createDirectories(target);
        Files.writeString(target.resolve("module-info.java"), moduleInfo);
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = target.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
        }
        return target;
    }

    private static Repository recording(List<String> asked, Map<String, Path> files) {
        Repository delegate = versionInsensitive(files);
        return (executor, coordinate, _) -> {
            asked.add(coordinate);
            return delegate.fetch(executor, coordinate);
        };
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
