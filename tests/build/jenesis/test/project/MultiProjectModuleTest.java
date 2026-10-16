package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.AssemblyRequirement;
import build.jenesis.project.MultiProjectModule;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MultiProjectModuleTest {

    private static final AtomicReference<CountDownLatch> BUILT = new AtomicReference<>();

    @TempDir
    private Path root,
            module1, module2, module3, module4, module5,
            source1, source2, source3, source4, source5;
    private BuildExecutor buildExecutor;

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

    @Test
    public void can_resolve_project() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("1-module", module1);
            buildExecutor.addSource("1-source", Files.writeString(Files.createDirectory(source1
                    .resolve(BuildStep.SOURCES)).resolve("source"), "foo"));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
            buildExecutor.addSource("2-source", Files.writeString(Files.createDirectory(source2
                    .resolve(BuildStep.SOURCES)).resolve("source"), "bar"));
        }, identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-')).replace('-', '/')), modules -> {
            assertThat(modules).containsExactly(
                    Map.entry("1", Collections.emptyNavigableSet()),
                    Map.entry("2", new LinkedHashSet<>(Set.of("1"))));
            return (name, dependencies, identifiers) -> switch (name) {
                case "1" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identifier/1-module",
                            "../../identifier/1-source");
                    assertThat(dependencies).isEmpty();
                    yield AssemblyDescriptor.of((module1, inherited) -> {
                        assertThat(inherited).containsOnlyKeys(
                                "../../../identifier/1-module",
                                "../../../identifier/1-source");
                        module1.addStep("step", (_, context, _) -> {
                            Files.writeString(context.next().resolve("file"), "foo");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        });
                    }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                }
                case "2" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identifier/2-module",
                            "../../identifier/2-source");
                    assertThat(dependencies).containsExactly(Map.entry("1", Collections.emptyNavigableSet()));
                    yield AssemblyDescriptor.of((module2, inherited) -> {
                        assertThat(inherited).containsKeys(
                                "../../../identifier/2-module",
                                "../../../identifier/2-source",
                                "../build-1/step");
                        module2.addStep("step", (_, context, arguments) -> {
                            Files.writeString(
                                    context.next().resolve("file"),
                                    Files.readString(arguments.get("../build-1/step").folder().resolve("file")) + "bar");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        }, "../build-1/step");
                    }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                }
                default -> throw new AssertionError();
            };
        }));
        SequencedMap<String, Path> paths = buildExecutor.execute();
        assertThat(paths).containsKeys("project/build-2/step");
        assertThat(paths.get("project/build-2/step").resolve("file")).content().contains("foobar");
    }

    @Test
    public void can_resolve_project_with_path_encoded_module_names() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("g%2F1-module", module1);
            buildExecutor.addSource("g%2F1-source", Files.writeString(Files.createDirectory(source1
                    .resolve(BuildStep.SOURCES)).resolve("source"), "foo"));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("g%2F2-module", module2);
            buildExecutor.addSource("g%2F2-source", Files.writeString(Files.createDirectory(source2
                    .resolve(BuildStep.SOURCES)).resolve("source"), "bar"));
        }, identifier -> Optional.of(identifier.substring(0, identifier.lastIndexOf('-'))), modules -> {
            assertThat(modules).containsExactly(
                    Map.entry("g%2F1", Collections.emptyNavigableSet()),
                    Map.entry("g%2F2", new LinkedHashSet<>(Set.of("g%2F1"))));
            return (name, _, _) -> switch (name) {
                case "g%2F1" -> AssemblyDescriptor.of((module1, _) -> module1.addStep("step", (_, context, _) -> {
                    Files.writeString(context.next().resolve("file"), "foo");
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                }), AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                case "g%2F2" -> AssemblyDescriptor.of((module2, _) -> module2.addStep("step", (_, context, arguments) -> {
                    Files.writeString(context.next().resolve("file"),
                            Files.readString(arguments.get("../build-g%2F1/step").folder().resolve("file")) + "bar");
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                }, "../build-g%2F1/step"), AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                default -> throw new AssertionError();
            };
        }));
        SequencedMap<String, Path> paths = buildExecutor.execute();
        assertThat(paths).containsKeys("project/build-g%2F2/step");
        assertThat(paths.get("project/build-g%2F2/step").resolve("file")).content().contains("foobar");
    }

    @Test
    public void can_resolve_project_transitives() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("1-module", module1);
            buildExecutor.addSource("1-source", Files.writeString(Files.createDirectory(source1
                    .resolve(BuildStep.SOURCES)).resolve("source"), "foo"));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
            buildExecutor.addSource("2-source", Files.writeString(Files.createDirectory(source2
                    .resolve(BuildStep.SOURCES)).resolve("source"), "bar"));
            SequencedProperties coordinates3 = new SequencedProperties();
            coordinates3.put("foo/baz", "");
            coordinates3.store(module3.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies3 = new SequencedProperties();
            dependencies3.put("main/compile/foo/qux", "");
            dependencies3.store(module3.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("3-module", module3);
            buildExecutor.addSource("3-source", Files.writeString(Files.createDirectory(source3
                    .resolve(BuildStep.SOURCES)).resolve("source"), "qux"));
        }, identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-')).replace('-', '/')), modules -> {
            assertThat(modules).containsExactly(
                    Map.entry("1", Collections.emptyNavigableSet()),
                    Map.entry("2", new LinkedHashSet<>(Set.of("1"))),
                    Map.entry("3", new LinkedHashSet<>(Set.of("2"))));
            return (name, dependencies, identifiers) -> switch (name) {
                case "1" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identifier/1-module",
                            "../../identifier/1-source");
                    assertThat(dependencies).isEmpty();
                    yield AssemblyDescriptor.of((module1, inherited) -> {
                        assertThat(inherited).containsOnlyKeys(
                                "../../../identifier/1-module",
                                "../../../identifier/1-source");
                        module1.addStep("step", (_, context, _) -> {
                            Files.writeString(context.next().resolve("file"), "foo");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        });
                    }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                }
                case "2" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identifier/2-module",
                            "../../identifier/2-source");
                    assertThat(dependencies).containsExactly(Map.entry("1", Collections.emptyNavigableSet()));
                    yield AssemblyDescriptor.of((module2, inherited) -> {
                        assertThat(inherited).containsKeys(
                                "../../../identifier/2-module",
                                "../../../identifier/2-source",
                                "../build-1/step");
                        module2.addStep("step", (_, context, arguments) -> {
                            Files.writeString(
                                    context.next().resolve("file"),
                                    Files.readString(arguments.get("../build-1/step").folder().resolve("file")) + "bar");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        }, "../build-1/step");
                    }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                }
                case "3" -> {
                    assertThat(identifiers).containsOnlyKeys(
                            "../../identifier/3-module",
                            "../../identifier/3-source");
                    assertThat(dependencies).containsExactly(
                            Map.entry("2", new LinkedHashSet<>(Set.of("1"))),
                            Map.entry("1", Collections.emptyNavigableSet()));
                    yield AssemblyDescriptor.of((module2, inherited) -> {
                        assertThat(inherited).containsKeys(
                                "../../../identifier/3-module",
                                "../../../identifier/3-source",
                                "../build-1/step",
                                "../build-2/step");
                        module2.addStep("step", (_, context, arguments) -> {
                            Files.writeString(
                                    context.next().resolve("file"),
                                    Files.readString(arguments.get("../build-2/step").folder().resolve("file")) + "qux");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        }, "../build-2/step");
                    }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
                }
                default -> throw new AssertionError();
            };
        }));
        SequencedMap<String, Path> paths = buildExecutor.execute();
        assertThat(paths).containsKeys("project/build-3/step");
        assertThat(paths.get("project/build-3/step").resolve("file")).content().contains("foobarqux");
    }

    @Test
    public void direct_transitive_sibling_is_visible_in_inherited() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("1-module", module1);
            buildExecutor.addSource("1-source", Files.writeString(Files.createDirectory(source1
                    .resolve(BuildStep.SOURCES)).resolve("source"), "foo"));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
            buildExecutor.addSource("2-source", Files.writeString(Files.createDirectory(source2
                    .resolve(BuildStep.SOURCES)).resolve("source"), "bar"));
            SequencedProperties coordinates3 = new SequencedProperties();
            coordinates3.put("foo/baz", "");
            coordinates3.store(module3.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies3 = new SequencedProperties();
            dependencies3.put("main/compile/foo/qux", "");
            dependencies3.store(module3.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("3-module", module3);
            buildExecutor.addSource("3-source", Files.writeString(Files.createDirectory(source3
                    .resolve(BuildStep.SOURCES)).resolve("source"), "qux"));
        }, identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-')).replace('-', '/')), _ -> (name, _, _) -> switch (name) {
            case "1" -> AssemblyDescriptor.of((module, _) -> module.addStep("step", (_, context, _) -> {
                Files.writeString(context.next().resolve("file"), "1");
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }), AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
            case "2" -> AssemblyDescriptor.of((module, _) -> module.addStep("step", (_, context, _) -> {
                Files.writeString(context.next().resolve("file"), "2");
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }), AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
            case "3" -> AssemblyDescriptor.of((module, inherited) -> {
                assertThat(inherited).containsKeys("../build-1/step", "../build-2/step");
                module.addStep("step", (_, context, _) -> {
                    Files.writeString(context.next().resolve("file"), "3");
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                });
            }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
            default -> throw new AssertionError("Unexpected module: " + name);
        }));
        buildExecutor.execute();
    }

    @Test
    public void indirect_transitive_sibling_is_visible_in_inherited() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("1-module", module1);
            buildExecutor.addSource("1-source", Files.writeString(Files.createDirectory(source1
                    .resolve(BuildStep.SOURCES)).resolve("source"), "foo"));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
            buildExecutor.addSource("2-source", Files.writeString(Files.createDirectory(source2
                    .resolve(BuildStep.SOURCES)).resolve("source"), "bar"));
            SequencedProperties coordinates3 = new SequencedProperties();
            coordinates3.put("foo/baz", "");
            coordinates3.store(module3.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies3 = new SequencedProperties();
            dependencies3.put("main/compile/foo/qux", "");
            dependencies3.store(module3.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("3-module", module3);
            buildExecutor.addSource("3-source", Files.writeString(Files.createDirectory(source3
                    .resolve(BuildStep.SOURCES)).resolve("source"), "qux"));
        }, identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-')).replace('-', '/')), _ -> (name, _, _) -> switch (name) {
            case "1" -> AssemblyDescriptor.of((module, _) -> module.addStep("step", (_, context, _) -> {
                Files.writeString(context.next().resolve("file"), "1");
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }), AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
            case "2" -> AssemblyDescriptor.of((module, inherited) -> {
                assertThat(inherited).containsKey("../build-1/step");
                module.addStep("step", (_, context, _) -> {
                    Files.writeString(context.next().resolve("file"), "2");
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                });
            }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
            case "3" -> AssemblyDescriptor.of((module, inherited) -> {
                assertThat(inherited).containsKeys("../build-1/step", "../build-2/step");
                module.addStep("step", (_, context, _) -> {
                    Files.writeString(context.next().resolve("file"), "3");
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                });
            }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
            default -> throw new AssertionError("Unexpected module: " + name);
        }));
        buildExecutor.execute();
    }

    @Test
    public void selector_builds_transitive_dependencies_but_not_dependents() {
        Set<String> built = ConcurrentHashMap.newKeySet();
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/a", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies1 = new SequencedProperties();
            dependencies1.put("main/compile/foo/b", "");
            dependencies1.store(module1.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("1-module", module1);
            buildExecutor.addSource("1-source", Files.writeString(Files.createDirectory(source1
                    .resolve(BuildStep.SOURCES)).resolve("source"), "a"));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/b", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/c", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
            buildExecutor.addSource("2-source", Files.writeString(Files.createDirectory(source2
                    .resolve(BuildStep.SOURCES)).resolve("source"), "b"));
            SequencedProperties coordinates3 = new SequencedProperties();
            coordinates3.put("foo/c", "");
            coordinates3.store(module3.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("3-module", module3);
            buildExecutor.addSource("3-source", Files.writeString(Files.createDirectory(source3
                    .resolve(BuildStep.SOURCES)).resolve("source"), "c"));
            SequencedProperties coordinates4 = new SequencedProperties();
            coordinates4.put("foo/d", "");
            coordinates4.store(module4.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies4 = new SequencedProperties();
            dependencies4.put("main/compile/foo/e", "");
            dependencies4.store(module4.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("4-module", module4);
            buildExecutor.addSource("4-source", Files.writeString(Files.createDirectory(source4
                    .resolve(BuildStep.SOURCES)).resolve("source"), "d"));
            SequencedProperties coordinates5 = new SequencedProperties();
            coordinates5.put("foo/e", "");
            coordinates5.store(module5.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies5 = new SequencedProperties();
            dependencies5.put("main/compile/foo/c", "");
            dependencies5.store(module5.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("5-module", module5);
            buildExecutor.addSource("5-source", Files.writeString(Files.createDirectory(source5
                    .resolve(BuildStep.SOURCES)).resolve("source"), "e"));
        },
                identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-')).replace('-', '/')),
                _ -> (name, _, _) -> AssemblyDescriptor.of((module, _) -> {
            built.add(name);
            module.addStep("step", (_, context, _) -> {
                Files.writeString(context.next().resolve("file"), name);
                return CompletableFuture.completedStage(new BuildStepResult(true));
            });
        }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD))));
        buildExecutor.execute("project/compose/module/build-1", "project/compose/module/build-5");
        assertThat(built).contains("1", "2", "3", "5").doesNotContain("4");
    }

    @Test
    public void a_phase_sees_its_own_build_under_the_name_of_the_phase_and_no_other_module() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("1-module", module1);
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
        },
                identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-'))),
                _ -> (name, _, _) -> {
            BuildExecutorModule build = (module, _) -> module.addStep(MultiProjectModule.INVENTORY, (_, context, _) -> {
                Files.writeString(context.next().resolve(Inventory.INVENTORY), name);
                return CompletableFuture.completedStage(new BuildStepResult(true));
            });
            BuildExecutorModule pack = (module, inherited) -> module.addStep("bundle", (_, context, arguments) -> {
                Files.writeString(context.next().resolve("bundle"), String.join(";", arguments.sequencedKeySet()));
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }, inherited.sequencedKeySet());
            return AssemblyDescriptor.of(build, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD))
                    .andThen("package", pack, AssemblyRequirement.of().after(AssemblyDescriptor.BUILD));
        }));
        SequencedMap<String, Path> paths = buildExecutor.execute();
        assertThat(paths).containsKeys("project/build-1/inventory",
                "project/build-2/inventory",
                "project/package-1/bundle",
                "project/package-2/bundle");
        assertThat(paths.get("project/package-2/bundle").resolve("bundle"))
                .as("a phase reads the phases it follows under their names and its identifiers under their own")
                .hasContent("../2-module;../build/inventory");
    }

    @Test
    public void a_build_does_not_wait_for_the_other_phases_of_the_modules_it_depends_on() {
        BUILT.set(new CountDownLatch(1));
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addSource("1-module", module1);
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
        },
                identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-'))),
                _ -> (name, _, _) -> AssemblyDescriptor.of((module, _) -> module.addStep("step", (_, context, _) -> {
                    Files.writeString(context.next().resolve("file"), name);
                    if (name.equals("2")) {
                        BUILT.get().countDown();
                    }
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                }), AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD)).andThen("verify", (module, _) -> module.addStep("check", (_, context, _) -> {
                    try {
                        if (name.equals("1") && !BUILT.get().await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("The build of 2 waited for the verification of 1");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    Files.writeString(context.next().resolve("file"), name);
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                }), AssemblyRequirement.of().after(AssemblyDescriptor.BUILD))));
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            SequencedMap<String, Path> paths = buildExecutor.execute(executor).toCompletableFuture().join();
            assertThat(paths).containsKeys("project/build-1/step",
                    "project/build-2/step",
                    "project/verify-1/check",
                    "project/verify-2/check");
        }
    }

    @Test
    public void a_phase_naming_tested_sees_the_phases_of_the_modules_that_test_its_module() throws IOException {
        SequencedProperties tests = new SequencedProperties();
        tests.setProperty("test", "foo/bar");
        tests.store(module2.resolve(BuildStep.MODULE));
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            buildExecutor.addModule("1", (nested, _) -> nested.addSource(MultiProjectModule.MANIFESTS, module1));
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addModule("2", (nested, _) -> nested.addSource(MultiProjectModule.MANIFESTS, module2));
        },
                identifier -> Optional.of(identifier.substring(0, identifier.indexOf('/'))),
                _ -> (name, _, _) -> {
            BuildExecutorModule build = (module, _) -> module.addStep(MultiProjectModule.INVENTORY, (_, context, _) -> {
                Files.writeString(context.next().resolve(Inventory.INVENTORY), name);
                return CompletableFuture.completedStage(new BuildStepResult(true));
            });
            BuildExecutorModule pack = (module, inherited) -> module.addStep("bundle", (_, context, arguments) -> {
                Files.writeString(context.next().resolve("bundle"), String.join(";", arguments.sequencedKeySet()));
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }, inherited.sequencedKeySet());
            return AssemblyDescriptor.of(build, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD))
                    .andThen("package", pack, AssemblyRequirement.of().after(AssemblyDescriptor.BUILD).tested(AssemblyDescriptor.BUILD));
        }));
        SequencedMap<String, Path> paths = buildExecutor.execute();
        assertThat(paths.get("project/package-1/bundle").resolve("bundle"))
                .as("the module 2 tests the module 1, so the package of 1 waits for the build of 2")
                .hasContent("../manifests;../build/inventory;../tested/build-2/inventory");
        assertThat(paths.get("project/package-2/bundle").resolve("bundle"))
                .as("no module tests the module 2")
                .hasContent("../manifests;../build/inventory");
    }

    @Test
    public void rejects_cyclic_modules() {
        buildExecutor.addModule("project", new MultiProjectModule((buildExecutor, _) -> {
            SequencedProperties coordinates1 = new SequencedProperties();
            coordinates1.put("foo/bar", "");
            coordinates1.store(module1.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies1 = new SequencedProperties();
            dependencies1.put("main/compile/foo/qux", "");
            dependencies1.store(module1.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("1-module", module1);
            SequencedProperties coordinates2 = new SequencedProperties();
            coordinates2.put("foo/qux", "");
            coordinates2.store(module2.resolve(BuildStep.IDENTITY));
            SequencedProperties dependencies2 = new SequencedProperties();
            dependencies2.put("main/compile/foo/bar", "");
            dependencies2.store(module2.resolve(BuildStep.REQUIRES));
            buildExecutor.addSource("2-module", module2);
        },
                identifier -> Optional.of(identifier.substring(0, identifier.indexOf('-')).replace('-', '/')),
                _ -> (name, _, _) -> AssemblyDescriptor.of((module, _) -> {
            throw new AssertionError("Unexpected module: " + name);
        }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD))));
        assertThatThrownBy(buildExecutor::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("Cyclic module dependencies")
                .hasMessageContaining("1")
                .hasMessageContaining("2");
    }

    @Test
    public void resolves_module_location_from_manifests_path() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("path", "nested/module");
        properties.store(module1.resolve(BuildStep.MODULE));
        SequencedMap<String, Path> arguments = new LinkedHashMap<>();
        arguments.put("../foo/sources", source1);
        arguments.put("../foo/manifests", module1);
        assertThat(MultiProjectModule.location(root, arguments)).isEqualTo(root.resolve("nested/module"));
    }

    @Test
    public void resolves_no_location_without_manifests() throws IOException {
        SequencedMap<String, Path> arguments = new LinkedHashMap<>();
        arguments.put("../foo/sources", source1);
        assertThat(MultiProjectModule.location(root, arguments)).isNull();
    }

    @Test
    public void resolves_no_location_without_path_property() throws IOException {
        new SequencedProperties().store(module1.resolve(BuildStep.MODULE));
        SequencedMap<String, Path> arguments = new LinkedHashMap<>();
        arguments.put("../foo/manifests", module1);
        assertThat(MultiProjectModule.location(root, arguments)).isNull();
    }

}
