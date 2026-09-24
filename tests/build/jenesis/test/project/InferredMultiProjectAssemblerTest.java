package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.BuildExecutorModule;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.InferredComplianceModule;
import build.jenesis.project.InferredDocumentationModule;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.InferredTestObservationModule;
import build.jenesis.project.ProjectModule;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.step.JPackage;
import build.jenesis.step.ProcessBuildStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredMultiProjectAssemblerTest {

    @TempDir
    private Path root;

    @Test
    public void main_in_module_properties_yields_main_class_argument_for_jar() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jarArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jar.properties"));
        assertThat(jarArguments.getProperty("--main-class")).isEqualTo("com.example.Entry");
    }

    @Test
    public void absent_main_in_module_properties_yields_no_jar_arguments() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jar.properties")).doesNotExist();
    }

    @Test
    public void empty_main_in_module_properties_yields_no_jar_arguments() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jar.properties")).doesNotExist();
    }

    @Test
    public void main_in_module_properties_yields_jpackage_arguments() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "artifact=demo\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--name")).isEqualTo("demo");
        assertThat(jpackageArguments.getProperty("--main-jar")).isEqualTo("classes.jar");
        assertThat(jpackageArguments.getProperty("--main-class")).isEqualTo("com.example.Entry");
    }

    @Test
    public void snapshot_version_reaches_jpackage_app_version_verbatim() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "artifact=demo\nversion=0-SNAPSHOT\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--app-version"))
                .as("the version is the project's to get right, so a qualifier reaches jpackage and fails there")
                .isEqualTo("0-SNAPSHOT");
    }

    @Test
    public void non_numeric_version_reaches_jpackage_app_version_verbatim() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "artifact=demo\nversion=RELEASE\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--app-version"))
                .as("nothing is invented for a version jpackage cannot parse; it is reported by jpackage itself")
                .isEqualTo("RELEASE");
    }

    @Test
    public void absent_main_in_module_properties_yields_no_jpackage_arguments() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties")).doesNotExist();
    }

    @Test
    public void package_type_enabled_adds_jpackage_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false, "app-image");
        Path packageOutput = fixture.execute("package/jpackage").get("package/jpackage");
        assertThat(packageOutput.resolve(JPackage.PACKAGES))
                .as("a module without a main class produces no application image")
                .doesNotExist();
    }

    @Test
    public void package_type_disabled_omits_jpackage_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/jpackage"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jpackage - ");
    }

    @Test
    public void modular_main_yields_module_jpackage_argument() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\nmodule=com.example.foo\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--module")).isEqualTo("com.example.foo/com.example.Entry");
        assertThat(jpackageArguments.stringPropertyNames())
                .as("modular launch uses --module, not the classpath --main-jar/--main-class")
                .doesNotContain("--main-jar", "--main-class");
    }

    @Test
    public void module_in_module_properties_yields_add_modules_for_jlink() throws IOException {
        Fixture fixture = setUp("module=foo\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jlinkArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jlink.properties"));
        assertThat(jlinkArguments.getProperty("--add-modules")).isEqualTo("foo");
    }

    @Test
    public void absent_module_in_module_properties_yields_no_jlink_arguments() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jlink.properties")).doesNotExist();
    }

    @Test
    public void a_process_command_file_in_configuration_yields_tool_arguments() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n-parameters=\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties javacArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"));
        assertThat(javacArguments.getProperty("-g")).isEqualTo("");
        assertThat(javacArguments.getProperty("-parameters")).isEqualTo("");
    }

    @Test
    public void a_process_command_file_adds_to_and_overrides_generated_arguments() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "version=1.0\n");
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "--module-version=9.9\n-g=\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties javacArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"));
        assertThat(javacArguments.getProperty("--module-version"))
                .as("a configuration key overrides the build-generated one")
                .isEqualTo("9.9");
        assertThat(javacArguments.getProperty("-g"))
                .as("a configuration key without a build-generated counterpart is added")
                .isEqualTo("");
    }

    @Test
    public void an_empty_higher_precedence_process_command_file_shadows_a_lower_one() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "version=1.0\n");
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n");
        Files.writeString(fixture.profile().resolve("process-javac.properties"), "");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties javacArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"));
        assertThat(javacArguments.getProperty("--module-version"))
                .as("the build-generated arguments remain")
                .isEqualTo("1.0");
        assertThat(javacArguments.getProperty("-g"))
                .as("the first-discovered (empty) file wins, shadowing the lower location's arguments")
                .isNull();
    }

    @Test
    public void jmod_flag_enabled_packages_a_module_archive() throws IOException {
        Fixture fixture = setUp("module=foo\n", false, false, false, null, true, false);
        Files.writeString(
                Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES)).resolve("module-info.java"),
                "module foo { }\n");
        Path jmodOutput = fixture.execute("sub/jmod").get("sub/jmod");
        assertThat(jmodOutput.resolve("jmods").resolve("foo.jmod")).isNotEmptyFile();
    }

    @Test
    public void jmod_flag_disabled_omits_jmod_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/jmod"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jmod - ");
    }

    @Test
    public void jlink_flag_disabled_omits_jlink_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/jlink"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jlink - ");
    }

    @Test
    public void native_image_flag_disabled_omits_native_image_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/native-image"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: native-image - ");
    }

    @Test
    public void native_image_enabled_wires_package_inventory_so_the_binary_is_stageable() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false, null, false, false, true);
        assertThat(fixture.execute("package/inventory"))
                .as("a native-image-only package phase still feeds the binary through an inventory step")
                .containsKey("package/inventory");
    }

    @Test
    public void source_flag_enabled_adds_sources_jar_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, true, false);
        Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES));
        Files.writeString(fixture.sources.resolve(BuildStep.SOURCES).resolve("foo.java"), "// dummy");
        Path sourcesOutput = fixture.execute("sub/sources/archive").get("sub/sources/archive");
        assertThat(sourcesOutput.resolve("sources").resolve("sources.jar")).exists();
    }

    @Test
    public void source_flag_disabled_omits_sources_jar_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/sources/archive"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: sources/archive - ");
    }

    @Test
    public void javadoc_flag_enabled_adds_javadoc_sub_module() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, true);
        Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES));
        Files.writeString(fixture.sources.resolve(BuildStep.SOURCES).resolve("module-info.java"), """
                module foo {
                }
                """);
        Path javadocOutput = fixture.execute("sub/documentation/archive").get("sub/documentation/archive");
        assertThat(javadocOutput.resolve("documentation").resolve("javadoc.jar")).exists();
    }

    @Test
    public void javadoc_flag_disabled_omits_javadoc_sub_module() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/documentation/archive"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: documentation/archive - ");
    }

    @Test
    public void tests_flag_enabled_for_test_variant_without_framework_in_dependencies_fails_resolution()
            throws IOException {
        Fixture fixture = setUp("path=\ntest=main_artifact\n", true, false, false);
        Files.writeString(
                Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES)).resolve("Sample.java"),
                "public class Sample {}");
        assertThatThrownBy(() -> fixture.execute("sub/observed/test/resolved"))
                .rootCause()
                .hasMessageContaining("No test framework could be resolved");
    }

    @Test
    public void tests_flag_disabled_omits_test_sub_module() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/test/resolved"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: test/resolved - ");
    }

    @Test
    public void abstract_test_module_omits_test_sub_module() throws IOException {
        Fixture fixture = setUp("path=\ntest=\nabstract=true\n", true, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/observed/test/resolved"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: observed/test/resolved - ");
    }

    @Test
    public void editing_a_process_override_reruns_prepare_on_the_next_build() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n");
        Path first = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(readProperties(first.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties")).stringPropertyNames())
                .contains("-g");

        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-verbose=\n");
        Path second = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(readProperties(second.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties")).stringPropertyNames())
                .as("editing only a process override must re-run prepare, not serve the stale one")
                .contains("-verbose")
                .doesNotContain("-g");
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation) throws IOException {
        return setUp(moduleProperties, tests, source, documentation, null, false, false);
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation,
                          String packageType) throws IOException {
        return setUp(moduleProperties, tests, source, documentation, packageType, false, false);
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation,
                          String packageType,
                          boolean jmod,
                          boolean jlink) throws IOException {
        return setUp(moduleProperties, tests, source, documentation, packageType, jmod, jlink, false);
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation,
                          String packageType,
                          boolean jmod,
                          boolean jlink,
                          boolean nativeImage) throws IOException {
        Path manifests = Files.createDirectory(root.resolve("manifests"));
        Files.writeString(manifests.resolve(BuildStep.MODULE), moduleProperties);
        Path sources = Files.createDirectory(root.resolve("sources"));
        Path artifacts = Files.createDirectory(root.resolve("artifacts"));
        Path configuration = Files.createDirectory(root.resolve("configuration"));
        Path profile = Files.createDirectory(root.resolve("profile"));
        StringBuilder packaging = new StringBuilder();
        if (jmod) {
            packaging.append("jmod=true\n");
        }
        if (jlink) {
            packaging.append("jlink=true\n");
        }
        if (nativeImage) {
            packaging.append("native=true\n");
        }
        if (packageType != null) {
            packaging.append("jpackage=").append(packageType).append("\n");
        }
        if (!packaging.isEmpty()) {
            Files.writeString(configuration.resolve("packaging.properties"), packaging.toString());
        }
        Path build = Files.createDirectory(root.resolve("build"));
        ProjectModule base = new ProjectModule() {
            @Override
            public String name() {
                return "module";
            }

            @Override
            public SequencedSet<String> dependencies() {
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> sources() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "sources"));
            }

            @Override
            public SequencedSet<String> resources() {
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> manifests() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "manifests"));
            }

            @Override
            public SequencedSet<String> coordinates() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "coordinates"));
            }

            @Override
            public SequencedSet<String> artifacts() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "artifacts"));
            }

            @Override
            public SequencedSet<String> spdx() {
                return Collections.emptyNavigableSet();
            }
        };
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base)
                .configuration(profile, configuration)
                .test(tests)
                .source(source)
                .documentation(documentation);
        return new Fixture(descriptor, build, manifests, sources, artifacts, configuration, profile);
    }

    private record Fixture(ProjectModuleDescriptor descriptor,
                           Path build,
                           Path manifests,
                           Path sources,
                           Path artifacts,
                           Path configuration,
                           Path profile) {

        SequencedMap<String, Path> execute(String selector) throws IOException {
            return execute(new InferredMultiProjectAssembler(), selector);
        }

        SequencedMap<String, Path> execute(InferredMultiProjectAssembler assembler, String... selectors) throws IOException {
            AssemblyDescriptor assembled = assembler.apply(descriptor, Map.of(), Map.of());
            BuildExecutor executor = BuildExecutor.of(build,
                    Duration.ZERO,
                    new HashDigestFunction("MD5"),
                    BuildStepHashFunction.ofSerializationDigest("MD5"),
                    BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
            executor.addSource("manifests", manifests);
            executor.addSource("sources", sources);
            executor.addSource("artifacts", artifacts);
            executor.addModule("sub", assembled.build(),
                    "manifests", "sources", "artifacts");
            for (Map.Entry<String, BuildExecutorModule> phase : assembled.tail().entrySet()) {
                executor.addModule(phase.getKey(), phase.getValue(), "sub");
            }
            return executor.execute(Runnable::run, selectors).toCompletableFuture().join();
        }
    }

    @Test
    public void a_custom_module_runs_in_the_module_build_beside_a_stock_step_of_the_same_name() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        SequencedMap<String, BuildExecutorModule> custom = new LinkedHashMap<>();
        custom.put("prepare", (executor, inherited) -> executor.addStep("inputs", (_, context, arguments) -> {
            Files.writeString(context.next().resolve("inputs.txt"), String.join("\n", arguments.sequencedKeySet()));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, inherited.sequencedKeySet()));
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().custom(custom),
                "sub/prepare",
                "sub/custom/prepare/inputs");
        assertThat(outputs).containsKeys("sub/prepare", "sub/custom/prepare/inputs");
        assertThat(Files.readAllLines(outputs.get("sub/custom/prepare/inputs").resolve("inputs.txt")))
                .as("a custom module reads what the module build reads")
                .hasSize(3)
                .anySatisfy(input -> assertThat(input).endsWith("manifests"))
                .anySatisfy(input -> assertThat(input).endsWith("sources"))
                .anySatisfy(input -> assertThat(input).endsWith("artifacts"));
    }

    @Test
    public void binds_the_project_resources_into_every_module() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Path notice = Files.writeString(root.resolve("NOTICE"), "notice");
        Path legal = Files.createDirectories(root.resolve("legal"));
        Files.writeString(legal.resolve("THIRD-PARTY.txt"), "third party");
        SequencedMap<Path, Path> resources = new LinkedHashMap<>();
        resources.put(Path.of("META-INF/NOTICE"), notice);
        resources.put(Path.of("META-INF/legal"), legal);
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().resources(resources),
                "sub/include");
        Path included = outputs.get("sub/include/resources").resolve(BuildStep.RESOURCES);
        assertThat(included.resolve("META-INF/NOTICE")).hasContent("notice");
        assertThat(included.resolve("META-INF/legal/THIRD-PARTY.txt"))
                .as("a folder is placed as a folder at its target")
                .hasContent("third party");
    }

    @Test
    public void wires_a_plugin_whose_properties_file_is_found_and_hands_it_the_values() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("plugin-lint.properties"), "level=strict\n");
        List<SequencedMap<String, String>> received = new ArrayList<>();
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+check", (_, properties) -> {
            received.add(properties);
            return new MarkerStep().asModule("lint");
        });
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().plugins(plugins),
                "sub/check/custom/lint");
        assertThat(received).containsExactly(new LinkedHashMap<>(Map.of("level", "strict")));
        assertThat(outputs.get("sub/check/custom/lint").resolve("marker.txt")).exists();
    }

    @Test
    public void hands_a_plugin_the_folder_of_its_module_to_resolve_inputs_against() throws IOException {
        Fixture base = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(base.configuration().resolve("plugin-lint.properties"), "@rules=rules\n");
        Path location = base.sources().resolveSibling("module");
        Fixture fixture = new Fixture(base.descriptor().location(location),
                base.build(),
                base.manifests(),
                base.sources(),
                base.artifacts(),
                base.configuration(),
                base.profile());
        List<Path> received = new ArrayList<>();
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+check", (folder, _) -> {
            received.add(folder);
            return new MarkerStep().asModule("lint");
        });
        fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "sub/check/custom/lint");
        assertThat(received).containsExactly(location);
    }

    @Test
    public void does_not_wire_a_plugin_whose_properties_file_is_missing() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+check", (_, _) -> new MarkerStep().asModule("lint"));
        fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "sub/check");
        assertThat(fixture.build().resolve("sub").resolve("check").resolve("custom"))
                .as("a plugin runs only where plugin-lint.properties is found")
                .doesNotExist();
    }

    @Test
    public void wires_a_plugin_into_a_nested_slot() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("plugin-greeting.properties"), "");
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("greeting+binary/generated", (_, _) -> new MarkerStep().asModule("greeting"));
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().plugins(plugins),
                "sub/binary/generated/custom/greeting");
        assertThat(outputs.get("sub/binary/generated/custom/greeting").resolve("marker.txt")).exists();
    }

    @Test
    public void refuses_a_plugin_in_an_unknown_slot() {
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+binary/unknown", (_, _) -> (_, _) -> {});
        assertThatThrownBy(() -> new InferredMultiProjectAssembler().plugins(plugins))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add the plugin lint+binary/unknown")
                .hasMessageContaining("binary/generated");
    }

    private record MarkerStep() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Files.writeString(context.next().resolve("marker.txt"), "");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    @Test
    public void sub_module_configurators_default_to_identity_and_round_trip() {
        InferredMultiProjectAssembler assembler = new InferredMultiProjectAssembler();
        assertThat(assembler.check().apply(null)).as("check configurator defaults to identity").isNull();
        assertThat(assembler.format().apply(null)).as("format configurator defaults to identity").isNull();
        assertThat(assembler.compliance().apply(null)).as("compliance configurator defaults to identity").isNull();
        assertThat(assembler.toolchain().apply(null)).as("toolchain configurator defaults to identity").isNull();
        assertThat(assembler.observe().apply(null)).as("observe configurator defaults to identity").isNull();
        assertThat(assembler.documentation().apply(null)).as("documentation configurator defaults to identity").isNull();

        Function<InferredTestObservationModule, BuildExecutorModule> custom = observe -> observe.test(null);
        assertThat(assembler.observe(custom).observe()).as("the observe wither stores the configurator").isSameAs(custom);

        Function<InferredComplianceModule, BuildExecutorModule> customCompliance = compliance -> compliance;
        assertThat(assembler.compliance(customCompliance).compliance())
                .as("the compliance wither stores the configurator").isSameAs(customCompliance);

        Function<InferredDocumentationModule, BuildExecutorModule> customDocumentation = documentation -> documentation;
        assertThat(assembler.documentation(customDocumentation).documentation())
                .as("the documentation wither stores the configurator").isSameAs(customDocumentation);
    }

    private static SequencedProperties readProperties(Path path) throws IOException {
        assertThat(path).exists();
        return SequencedProperties.ofFiles(path);
    }
}
