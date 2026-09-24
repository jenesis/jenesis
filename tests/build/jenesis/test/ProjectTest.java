package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorFileCache;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Make;
import build.jenesis.Environment;
import build.jenesis.Project;
import build.jenesis.SequencedProperties;
import build.jenesis.module.JenesisModuleRepositoryExport;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.ProjectModuleDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProjectTest {

    private final Map<String, String> settings = new HashMap<>();

    @TempDir
    private Path root;

    @Test
    public void reads_the_tag_revision_and_tree_from_their_properties_and_keeps_empty_ones() {
        assertThat(Project.ofEnvironment(new Environment(settings), root).tag()).isNull();
        assertThat(Project.ofEnvironment(new Environment(settings), root).revision()).isNull();
        assertThat(Project.ofEnvironment(new Environment(settings), root).tree()).isNull();
        settings.put("project.tag", "v1.2.3");
        settings.put("project.revision", "0123abcd");
        settings.put("project.tree", "4b825dc642cb6eb9a060e54bf8d69288fbee4904");
        assertThat(Project.ofEnvironment(new Environment(settings), root).tag()).isEqualTo("v1.2.3");
        assertThat(Project.ofEnvironment(new Environment(settings), root).revision()).isEqualTo("0123abcd");
        assertThat(Project.ofEnvironment(new Environment(settings), root).tree()).isEqualTo("4b825dc642cb6eb9a060e54bf8d69288fbee4904");
        settings.put("project.tag", "");
        settings.put("project.revision", "");
        settings.put("project.tree", "");
        assertThat(Project.ofEnvironment(new Environment(settings), root).tag()).isEmpty();
        assertThat(Project.ofEnvironment(new Environment(settings), root).revision()).isEmpty();
        assertThat(Project.ofEnvironment(new Environment(settings), root).tree()).isEmpty();
    }

    @Test
    public void reads_every_setting_from_the_provider_it_is_given() {
        Project project = Project.ofEnvironment(new Environment(Map.of("project.version", "1.2.3",
                "project.target", "out",
                "project.sources", "",
                "project.digest", "SHA-512")), root);
        assertThat(project.version()).isEqualTo("1.2.3");
        assertThat(project.target()).isEqualTo(Path.of("out"));
        assertThat(project.sources()).isTrue();
        assertThat(System.getProperty("jenesis.project.version"))
                .as("a caller configures a build by handing it a provider, never by setting a property"
                        + " the whole JVM shares")
                .isNull();
    }

    @Test
    public void takes_its_defaults_when_it_is_given_no_provider() {
        Project project = new Project(root);
        assertThat(project.version())
                .as("the environment configures the entry point's project, not every project a caller builds")
                .isNull();
        assertThat(project.target()).isEqualTo(Path.of("target"));
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.version", "9.9.9", "project.target", "elsewhere")), root).version()).isEqualTo("9.9.9");
    }

    @Test
    public void records_no_scm_tag_for_a_version_alone() throws IOException {
        assertThat(metadataValues(Project.ofEnvironment(new Environment(settings), Path.of(".")).version("1.2.3")))
                .as("how a project names its release tags is not derived from its version")
                .containsEntry("version", "1.2.3")
                .doesNotContainKey("scm.tag");
    }

    @Test
    public void records_a_set_tag_and_revision() throws IOException {
        assertThat(metadataValues(Project.ofEnvironment(new Environment(settings), Path.of(".")).tag("v1.2.3").revision("0123abcd")
                .tree("4b825dc642cb6eb9a060e54bf8d69288fbee4904")))
                .containsEntry("scm.tag", "v1.2.3")
                .containsEntry("scm.revision", "0123abcd")
                .containsEntry("scm.tree", "4b825dc642cb6eb9a060e54bf8d69288fbee4904");
    }

    @Test
    public void records_an_empty_tag_and_revision_to_replace_declared_ones() throws IOException {
        assertThat(metadataValues(Project.ofEnvironment(new Environment(settings), Path.of(".")).tag("").revision("")))
                .as("an empty value overrides what a metadata file or a pom.xml declares")
                .containsEntry("scm.tag", "")
                .containsEntry("scm.revision", "");
    }

    @Test
    public void records_no_scm_tag_or_revision_unless_set() throws IOException {
        assertThat(metadataValues(Project.ofEnvironment(new Environment(settings), Path.of("."))))
                .as("what a metadata file or a pom.xml declares stays in force")
                .doesNotContainKeys("scm.tag", "scm.revision", "scm.tree");
    }

    private SequencedProperties metadataValues(Project project) throws IOException {
        Files.writeString(Files.createDirectories(root.resolve("sources")).resolve("module-info.java"), "module example {}");
        Path target = root.resolve("target");
        project.root(root).target(target).build(Project.METADATA);
        SequencedProperties values = new SequencedProperties();
        try (Stream<Path> walk = Files.walk(target)) {
            for (Path file : walk.filter(path -> path.getFileName().toString().equals(BuildStep.METADATA)).toList()) {
                values.putAll(SequencedProperties.ofFiles(file));
            }
        }
        return values;
    }

    @Test
    public void auto_detects_maven_from_pom_xml() throws IOException {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        assertThat(Project.Layout.of(root)).isSameAs(Project.Layout.MAVEN);
    }

    @Test
    public void auto_detects_modular_from_module_info() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module example {}");
        assertThat(Project.Layout.of(root)).isSameAs(Project.Layout.MODULAR_TO_MAVEN);
    }

    @Test
    public void auto_prefers_maven_when_both_are_present() throws IOException {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module example {}");
        assertThat(Project.Layout.of(root)).isSameAs(Project.Layout.MAVEN);
    }

    @Test
    public void auto_throws_when_neither_descriptor_is_present() {
        assertThatThrownBy(() -> Project.Layout.of(root))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No build descriptor found");
    }

    @Test
    public void auto_skips_module_info_under_a_nested_build_marker() throws IOException {
        Path nested = Files.createDirectory(root.resolve("nested"));
        Files.createFile(nested.resolve(BuildExecutor.SKIP_MARKER));
        Files.writeString(nested.resolve("module-info.java"), "module hidden {}");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        assertThat(Project.Layout.of(root)).isSameAs(Project.Layout.MAVEN);
    }

    @Test
    public void build_throws_when_no_descriptor_is_detected() {
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(settings), root).target(root.resolve("target")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No build descriptor found");
    }

    @Test
    public void runs_verify_without_a_plugin_in_each_concrete_layout() throws IOException {
        Files.writeString(Files.createDirectories(root.resolve("sources")).resolve("module-info.java"), "module demo.empty { }\n");
        Files.writeString(root.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>demo</groupId>
                    <artifactId>empty</artifactId>
                    <version>1</version>
                </project>
                """);
        for (Project.Layout layout : List.of(Project.Layout.MAVEN, Project.Layout.MODULAR, Project.Layout.MODULAR_TO_MAVEN)) {
            SequencedMap<String, Path> outputs = Project.ofEnvironment(new Environment(settings), root)
                    .target(root.resolve("target-" + layout.hashCode()))
                    .layout(layout)
                    .build(Project.VERIFY);
            assertThat(outputs.keySet())
                    .as("verify is a selector of every layout, and adds nothing without a transform or an inspection")
                    .noneMatch(key -> key.startsWith(Project.VERIFY + "/"));
        }
    }

    @Test
    public void layout_setter_round_trips_each_concrete_layout() {
        for (Project.Layout layout : List.of(Project.Layout.MAVEN, Project.Layout.MODULAR, Project.Layout.MODULAR_TO_MAVEN)) {
            assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).layout(layout).layout()).isSameAs(layout);
        }
    }

    @Test
    public void system_property_picks_each_concrete_layout() {
        Map<String, Project.Layout> cases = Map.of(
                "maven", Project.Layout.MAVEN,
                "modular", Project.Layout.MODULAR,
                "modular_to_maven", Project.Layout.MODULAR_TO_MAVEN);
        cases.forEach((name, layout) -> {
            assertThat(Project.ofEnvironment(new Environment(Map.of("project.layout", name)), Path.of(".")).layout())
                    .as("layout=%s", name)
                    .isSameAs(layout);
        });
    }

    @Test
    public void explicit_layout_overrides_system_property() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.layout", "maven")), Path.of(".")).layout(Project.Layout.MODULAR).layout())
                .isSameAs(Project.Layout.MODULAR);
    }

    @Test
    public void system_property_rejects_unknown_layout() {
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(Map.of("project.layout", "nonsense")), Path.of(".")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown layout");
    }

    @Test
    public void skip_tests_setter_skips_tests() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).tests(false).tests()).isFalse();
    }

    @Test
    public void defaults_keep_tests_enabled() {
        Project project = Project.ofEnvironment(new Environment(settings), Path.of("."));
        assertThat(project.tests()).isTrue();
    }

    @Test
    public void default_target_is_build() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).defaultTarget()).containsExactly("build");
    }

    @Test
    public void lists_every_setting_in_force_as_it_forwards_them_into_a_container() {
        List<String> printed = new ArrayList<>();
        Project.perform(new Environment(Map.of("platform.fips", "true", "project.version", "1")).out(printed::add),
                root,
                new LinkedHashSet<>(),
                Project.PROPERTIES);
        assertThat(printed)
                .as("a setting named by a pattern is in force as much as one the catalogue lists")
                .contains("jenesis.platform.fips=true", "jenesis.project.version=1");
    }

    @Test
    public void configuration_defaults_to_build_jenesis_under_the_root() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).configuration())
                .containsExactly(Path.of(".").resolve("build.jenesis"));
    }

    @Test
    public void empty_configuration_property_skips_the_global_configuration() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.configuration", "")), Path.of(".")).configuration()).isEmpty();
    }

    @Test
    public void boms_default_to_the_configuration() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).boms())
                .containsExactly(Path.of(".").resolve("build.jenesis"));
        settings.put("project.configuration", "config");
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).boms()).containsExactly(Path.of(".").resolve("config"));
    }

    @Test
    public void boms_property_overrides_the_configuration() {
        settings.put("project.boms", "platform");
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).boms()).containsExactly(Path.of(".").resolve("platform"));
        settings.put("project.boms", "");
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).boms()).isEmpty();
    }

    @Test
    public void boms_wither_replaces_the_locations() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).boms(Path.of("platform")).boms()).containsExactly(Path.of("platform"));
    }

    @Test
    public void configuration_reference_splices_the_default() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.configuration", "shared,@")), Path.of(".")).configuration())
                .containsExactly(Path.of(".").resolve("shared"), Path.of(".").resolve("build.jenesis"));
    }

    @Test
    public void configuration_named_reference_splices_a_property_value() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("test.sample.key", "shared,extra", "project.configuration", "@test.sample.key,@")), Path.of(".")).configuration())
                .containsExactly(Path.of(".").resolve("shared"),
                        Path.of(".").resolve("extra"),
                        Path.of(".").resolve("build.jenesis"));
    }

    @Test
    public void configuration_fails_on_unresolved_reference() {
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(Map.of("project.configuration", "@test.sample.unset")), Path.of(".")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unresolved location reference: @test.sample.unset");
    }

    @Test
    public void configuration_fails_on_circular_reference() {
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(Map.of("test.sample.a", "@test.sample.b", "test.sample.b", "@test.sample.a", "project.configuration", "@test.sample.a")), Path.of(".")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Circular location reference: @test.sample.a");
    }

    @Test
    public void boms_reference_splices_the_configuration() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.configuration", "config", "project.boms", "platform,@")), Path.of(".")).boms())
                .containsExactly(Path.of(".").resolve("platform"), Path.of(".").resolve("config"));
    }

    @Test
    public void scoped_configuration_folders_precede_the_module_and_project_folders() throws IOException {
        Path scoped = Files.createDirectories(root.resolve("module/src/main/build.jenesis"));
        Path module = Files.createDirectories(root.resolve("module/build.jenesis"));
        Path shared = Files.createDirectories(root.resolve("config"));
        SequencedSet<Path> folders = Project.Layout.configurations(
                Arrays.asList(
                        root.resolve("module/src/main/build.jenesis"),
                        root.resolve("module/src/main/missing"),
                        root.resolve("module/build.jenesis"),
                        null),
                new LinkedHashSet<>(List.of(shared)),
                Collections.emptyNavigableSet());
        assertThat(folders).containsExactly(
                scoped.toAbsolutePath().normalize(),
                module.toAbsolutePath().normalize(),
                shared.toAbsolutePath().normalize());
    }

    @Test
    public void configuration_folders_are_absolute_deduplicated_and_must_exist() throws IOException {
        Path existing = Files.createDirectories(root.resolve("module").resolve("config"));
        SequencedSet<Path> folders = Project.Layout.configurations(
                root.resolve("module").resolve("config"),
                new LinkedHashSet<>(Arrays.asList(
                        root.resolve("module/config"),
                        root.resolve("missing"),
                        null)),
                Collections.emptyNavigableSet());
        assertThat(folders).hasSize(1);
        Path only = folders.getFirst();
        assertThat(only.isAbsolute()).isTrue();
        assertThat(only).isEqualTo(existing.toAbsolutePath().normalize());
    }

    @Test
    public void profiles_are_the_selected_profile_names() throws IOException {
        settings.put("make.profiles", "release, supply-chain.properties");
        assertThat(Make.settings(root, settings).profiles())
                .containsExactly(Path.of("release"), Path.of("supply-chain"));
    }

    @Test
    public void profiles_default_to_empty_without_a_selection() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).profiles()).isEmpty();
    }

    @Test
    public void profiles_wither_round_trips() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).profiles(Path.of("release"), Path.of("ci")).profiles())
                .containsExactly(Path.of("release"), Path.of("ci"));
    }

    @Test
    public void profile_folders_precede_plain_folders_module_before_root() throws IOException {
        Path moduleConfig = Files.createDirectories(root.resolve("module").resolve("config"));
        Path rootConfig = Files.createDirectories(root.resolve("root"));
        Path moduleProfile = Files.createDirectories(moduleConfig.resolve("release"));
        Path rootProfile = Files.createDirectories(rootConfig.resolve("release"));
        SequencedSet<Path> folders = Project.Layout.configurations(
                moduleConfig,
                new LinkedHashSet<>(List.of(rootConfig)),
                new LinkedHashSet<>(List.of(Path.of("release"))));
        assertThat(folders).containsExactly(
                moduleProfile.toAbsolutePath().normalize(),
                rootProfile.toAbsolutePath().normalize(),
                moduleConfig.toAbsolutePath().normalize(),
                rootConfig.toAbsolutePath().normalize());
    }

    @Test
    public void default_target_can_be_overridden() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).defaultTarget("foo", "bar").defaultTarget())
                .containsExactly("foo", "bar");
    }

    @Test
    public void a_project_is_built_from_the_folder_it_is_given() {
        assertThat(Project.ofEnvironment(new Environment(settings), root).root()).isEqualTo(root);
    }

    @Test
    public void system_property_overrides_target() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.target", "custom-target")), Path.of(".")).target()).isEqualTo(Path.of("custom-target"));
    }

    @Test
    public void system_property_overrides_artifacts() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.artifacts", "custom-artifacts")), Path.of(".")).artifacts()).isEqualTo(Path.of("custom-artifacts"));
    }

    @Test
    public void local_build_cache_is_disabled_by_default() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).cache()).isNull();
    }

    @Test
    public void local_build_cache_rejects_a_uri_value() {
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(Map.of("project.cache", "file:///tmp/cache")), Path.of("."))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void empty_property_enables_local_build_cache_at_default_location() {
        BuildExecutorCache cache = Project.ofEnvironment(new Environment(Map.of("make.root", root.toString(), "project.cache", "")), Path.of(".")).cache();
        assertThat(cache).isInstanceOf(BuildExecutorFileCache.class);
        assertThat(((BuildExecutorFileCache) cache).root().endsWith(Path.of(".jenesis", "cache"))).isTrue();
    }

    @Test
    public void system_property_overrides_local_build_cache_location() {
        BuildExecutorCache cache = Project.ofEnvironment(new Environment(Map.of("make.root", root.toString(), "project.cache", "custom-cache")), Path.of(".")).cache();
        assertThat(cache).isInstanceOf(BuildExecutorFileCache.class);
        assertThat(((BuildExecutorFileCache) cache).root().endsWith(Path.of("custom-cache"))).isTrue();
    }

    @Test
    public void default_digest_is_sha_256() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).hashFunction()).isEqualTo(new HashDigestFunction("SHA-256"));
    }

    @Test
    public void system_property_overrides_digest() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.digest", "SHA-512")), Path.of(".")).hashFunction())
                .isEqualTo(new HashDigestFunction("SHA-512"));
    }

    @Test
    public void digest_can_be_overridden() {
        HashDigestFunction digest = new HashDigestFunction("SHA-512");
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).hashFunction(digest).hashFunction()).isSameAs(digest);
    }

    @Test
    public void default_assembler_is_set() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).assembler()).isNotNull();
    }

    @Test
    public void assembler_can_be_overridden() {
        MultiProjectAssembler<ProjectModuleDescriptor> custom = (_, _, _) -> new AssemblyDescriptor((_, _) -> {});
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).assembler(custom).assembler()).isSameAs(custom);
    }

    @Test
    public void default_layout_is_auto() {
        assertThat(Project.ofEnvironment(new Environment(settings), Path.of(".")).layout()).isSameAs(Project.Layout.AUTO);
    }

    @Test
    public void maven_layout_resolver_maps_named_and_unnamed_modules() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        Function<String, String> resolver = Project.Layout.MAVEN.apply(
                BuildExecutor.of(target,
                        Duration.ZERO,
                        new HashDigestFunction("MD5"),
                        BuildStepHashFunction.ofSerializationDigest("MD5"),
                        BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0),
                project,
                new InferredMultiProjectAssembler());
        assertThat(resolver.apply("sources")).isEqualTo("build/maven/compose/module/module-sources");
        assertThat(resolver.apply("")).isEqualTo("build/maven/compose/module/module-");
    }

    @Test
    public void modular_layout_resolver_maps_named_and_unnamed_modules() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        Function<String, String> resolver = Project.Layout.MODULAR.apply(
                BuildExecutor.of(target,
                        Duration.ZERO,
                        new HashDigestFunction("MD5"),
                        BuildStepHashFunction.ofSerializationDigest("MD5"),
                        BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0),
                project,
                new InferredMultiProjectAssembler());
        assertThat(resolver.apply("sources")).isEqualTo("build/modules/compose/module/module-sources");
        assertThat(resolver.apply("")).isEqualTo("build/modules/compose/module/module-");
        assertThat(resolver.apply("api+client/compile")).isEqualTo("build/modules/compose/module/module-api+client/compile");
    }

    @Test
    public void maven_layout_resolver_preserves_step_path_after_slash() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        Function<String, String> resolver = Project.Layout.MAVEN.apply(
                BuildExecutor.of(target,
                        Duration.ZERO,
                        new HashDigestFunction("MD5"),
                        BuildStepHashFunction.ofSerializationDigest("MD5"),
                        BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0),
                project,
                new InferredMultiProjectAssembler());
        assertThat(resolver.apply("sources/compile/dependencies/artifacts"))
                .isEqualTo("build/maven/compose/module/module-sources/compile/dependencies/artifacts");
        assertThat(resolver.apply("/compile"))
                .isEqualTo("build/maven/compose/module/module-/compile");
    }

    @Test
    public void modular_layout_resolver_preserves_step_path_after_slash() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        Function<String, String> resolver = Project.Layout.MODULAR.apply(
                BuildExecutor.of(target,
                        Duration.ZERO,
                        new HashDigestFunction("MD5"),
                        BuildStepHashFunction.ofSerializationDigest("MD5"),
                        BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0),
                project,
                new InferredMultiProjectAssembler());
        assertThat(resolver.apply("sources/compile/dependencies/artifacts"))
                .isEqualTo("build/modules/compose/module/module-sources/compile/dependencies/artifacts");
        assertThat(resolver.apply("/compile"))
                .isEqualTo("build/modules/compose/module/module-/compile");
    }

    @Test
    public void modular_to_maven_layout_resolver_preserves_step_path_after_slash() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        Function<String, String> resolver = Project.Layout.MODULAR_TO_MAVEN.apply(
                BuildExecutor.of(target,
                        Duration.ZERO,
                        new HashDigestFunction("MD5"),
                        BuildStepHashFunction.ofSerializationDigest("MD5"),
                        BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0),
                project,
                new InferredMultiProjectAssembler());
        assertThat(resolver.apply("sources/compile/dependencies/artifacts"))
                .isEqualTo("build/modules/compose/module/module-sources/compile/dependencies/artifacts");
        assertThat(resolver.apply("/compile"))
                .isEqualTo("build/modules/compose/module/module-/compile");
    }

    @Test
    public void modular_layout_registers_export_step() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        BuildExecutor executor = BuildExecutor.of(target,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);

        Project.Layout.MODULAR.apply(executor, project, new InferredMultiProjectAssembler());

        executor.replaceStep(Project.EXPORT, new JenesisModuleRepositoryExport(target.resolve("module-repository")));
    }

    @Test
    public void modular_to_maven_layout_resolver_maps_named_and_unnamed_modules() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Project project = Project.ofEnvironment(new Environment(settings), root).target(target);
        Function<String, String> resolver = Project.Layout.MODULAR_TO_MAVEN.apply(
                BuildExecutor.of(target,
                        Duration.ZERO,
                        new HashDigestFunction("MD5"),
                        BuildStepHashFunction.ofSerializationDigest("MD5"),
                        BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0),
                project,
                new InferredMultiProjectAssembler());
        assertThat(resolver.apply("sources")).isEqualTo("build/modules/compose/module/module-sources");
        assertThat(resolver.apply("")).isEqualTo("build/modules/compose/module/module-");
    }

    @Test
    public void build_returns_paths_for_the_default_target() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Path source = Files.createDirectory(root.resolve("source"));
        Project.Layout layout = (executor, _, _) -> {
            executor.addSource(Project.BUILD, source);
            return name -> name;
        };
        SequencedMap<String, Path> result = Project.ofEnvironment(new Environment(settings), Path.of("."))
                .root(root)
                .target(target)
                .layout(layout)
                .build();
        assertThat(result).containsExactly(Map.entry(Project.BUILD, source));
    }

    @Test
    public void build_returns_paths_for_explicit_selectors() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Path alpha = Files.createDirectory(root.resolve("alpha"));
        Path beta = Files.createDirectory(root.resolve("beta"));
        Project.Layout layout = (executor, _, _) -> {
            executor.addSource("alpha", alpha);
            executor.addSource("beta", beta);
            return name -> name;
        };
        SequencedMap<String, Path> result = Project.ofEnvironment(new Environment(settings), Path.of("."))
                .root(root)
                .target(target)
                .layout(layout)
                .build("beta");
        assertThat(result).containsExactly(Map.entry("beta", beta));
    }

    @Test
    public void build_resolves_plus_prefixed_selectors_via_the_layout() throws IOException {
        Path target = Files.createDirectory(root.resolve("target"));
        Path source = Files.createDirectory(root.resolve("source"));
        Project.Layout layout = (executor, _, _) -> {
            executor.addSource("resolved", source);
            return name -> "resolved";
        };
        SequencedMap<String, Path> result = Project.ofEnvironment(new Environment(settings), Path.of("."))
                .root(root)
                .target(target)
                .layout(layout)
                .build("+anything");
        assertThat(result).containsExactly(Map.entry("resolved", source));
    }

    @Test
    public void reads_the_plugins_named_beside_jenesis_properties() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), """
                lint+check=./lint@lint
                greeting+binary/generated=demo.greeting
                """);
        Project project = Project.ofEnvironment(new Environment(settings), root);
        assertThat(project.assembler()).isInstanceOfSatisfying(InferredMultiProjectAssembler.class,
                assembler -> assertThat(assembler.plugins()).containsOnlyKeys("lint+check", "greeting+binary/generated"));
    }

    @Test
    public void leaves_out_a_plugin_its_setting_switches_off() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), """
                lint+check=./lint
                greeting+binary/generated=demo.greeting
                """);
        Project project = Project.ofEnvironment(new Environment(Map.of("plugin.lint", "false")), root);
        assertThat(project.assembler()).isInstanceOfSatisfying(InferredMultiProjectAssembler.class,
                assembler -> assertThat(assembler.plugins()).containsOnlyKeys("greeting+binary/generated"));
    }

    @Test
    public void hands_the_plugins_of_transform_and_inspect_to_verify() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), """
                greeting+binary/generated=demo.greeting
                licenses+transform=./licenses
                audit+inspect=demo.audit@audit
                """);
        Project project = Project.ofEnvironment(new Environment(settings), root);
        assertThat(project.assembler()).isInstanceOfSatisfying(InferredMultiProjectAssembler.class,
                assembler -> assertThat(assembler.plugins()).containsOnlyKeys("greeting+binary/generated"));
        assertThat(project.verify().transforms()).containsOnlyKeys("licenses");
        assertThat(project.verify().inspections()).containsOnlyKeys("audit");
        assertThat(project.verify().resolutions()).containsOnlyKeys("licenses", "audit");
        assertThat(project.verify().pins()).isEqualTo(root.resolve("jenesis-plugins-pin.properties"));
    }

    @Test
    public void still_resolves_a_plugin_of_verify_its_setting_switches_off() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), """
                licenses+transform=./licenses
                audit+inspect=demo.audit
                """);
        Project project = Project.ofEnvironment(new Environment(Map.of("plugin.audit", "false")), root);
        assertThat(project.verify().transforms()).containsOnlyKeys("licenses");
        assertThat(project.verify().inspections()).isEmpty();
        assertThat(project.verify().resolutions())
                .as("pin must capture a plugin that only a profile switches on")
                .containsOnlyKeys("licenses", "audit");
    }

    @Test
    public void refuses_a_plugin_of_verify_that_shares_its_name_with_a_module_plugin() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), """
                audit+check=./audit
                audit+inspect=./audit
                """);
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(settings), root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The plugin audit")
                .hasMessageContaining("takes a name of its own");
    }

    @Test
    public void refuses_a_plugin_named_for_both_transform_and_inspect() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), """
                audit+transform=./audit
                audit+inspect=./audit
                """);
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(settings), root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add the plugin audit+inspect")
                .hasMessageContaining("takes a name of its own");
    }

    @Test
    public void refuses_a_plugin_that_selects_a_provider_without_a_name() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), "lint+check=./lint@\n");
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(settings), root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The plugin lint+check")
                .hasMessageContaining("followed by @<name>");
    }

    @Test
    public void refuses_a_plugin_that_names_nothing() throws IOException {
        Files.writeString(root.resolve("jenesis-plugins.properties"), "lint+check=\n");
        assertThatThrownBy(() -> Project.ofEnvironment(new Environment(settings), root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The plugin lint+check")
                .hasMessageContaining("names nothing");
    }

    @Test
    public void the_layered_settings_reads_a_file_from_root() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.test.sample.key=fromFile\n");
        assertThat(Make.settings(root, settings).keys().get("test.sample.key")).isEqualTo("fromFile");
    }

    @Test
    public void the_layered_settings_does_not_override_an_explicit_system_property() throws IOException {
        settings.put("test.sample.key", "fromCommandLine");
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.test.sample.key=fromFile\n");
        assertThat(Make.settings(root, settings).keys().get("test.sample.key")).isEqualTo("fromCommandLine");
    }

    @Test
    public void the_layered_settings_is_a_no_op_when_absent() throws IOException {
        assertThat(Make.settings(root, settings).keys().get("test.sample.key")).isNull();
    }

    @Test
    public void the_layered_settings_names_every_key_a_file_supplied() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"),
                "jenesis.test.sample.key=fromFile\njenesis.test.sample.other=also\n");
        assertThat(Make.settings(root, settings).declared())
                .as("a process the build hands a command line to - a container, the daemon - is given what"
                        + " the files supplied, which is why the keys they name can be enumerated")
                .contains("test.sample.key", "test.sample.other");
    }

    @Test
    public void the_layered_settings_chains_profile_files() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"),
                "jenesis.make.profiles=profile-a, profile-b\njenesis.test.sample.a=fromBase\n");
        Files.writeString(root.resolve("jenesis-profile-a.properties"),
                "jenesis.make.profiles=profile-c\njenesis.test.sample.b=fromA\n");
        Files.writeString(root.resolve("jenesis-profile-b.properties"), "jenesis.test.sample.c=fromB\n");
        Files.writeString(root.resolve("jenesis-profile-c.properties"), "jenesis.test.sample.d=fromC\n");
        assertThat(Make.settings(root, settings).keys().get("test.sample.a")).isEqualTo("fromBase");
        assertThat(Make.settings(root, settings).keys().get("test.sample.b")).isEqualTo("fromA");
        assertThat(Make.settings(root, settings).keys().get("test.sample.c")).isEqualTo("fromB");
        assertThat(Make.settings(root, settings).keys().get("test.sample.d")).isEqualTo("fromC");
    }

    @Test
    public void the_layered_settings_rejects_root_in_the_project_file() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.root=elsewhere\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.make.root cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_root_in_a_profile() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.profiles=ci\n");
        Files.writeString(root.resolve("jenesis-ci.properties"), "jenesis.make.root=elsewhere\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.make.root cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_global_in_a_profile() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.profiles=ci\n");
        Files.writeString(root.resolve("jenesis-ci.properties"), "jenesis.make.global=elsewhere\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.make.global cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_global_in_the_user_global_file() throws IOException {
        Path home = Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(home.resolve("jenesis.properties"), "jenesis.make.global=elsewhere\n");
        settings.put("make.global", root.resolve("home").toString());
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.make.global cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_global_in_the_project_file() throws IOException {
        Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(root.resolve("jenesis.properties"),
                "jenesis.make.global=" + root.resolve("home").toString().replace('\\', '/') + "\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .as("a project that could move the user-global folder could supply that file itself")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.make.global cannot be set in");
    }

    @Test
    public void the_layered_settings_accepts_global_from_the_command_line() throws IOException {
        Path home = Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(home.resolve("jenesis.properties"), "jenesis.test.sample.key=fromGlobal\n");
        settings.put("make.global", root.resolve("home").toString());
        assertThat(Make.settings(root, settings).keys().get("test.sample.key")).isEqualTo("fromGlobal");
    }

    @Test
    public void the_layered_settings_rejects_toolchain_searchpath_in_the_project_file() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.toolchain.searchpath=/opt/jdks/*\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.toolchain.searchpath cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_toolchain_searchpath_in_a_profile() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.profiles=ci\n");
        Files.writeString(root.resolve("jenesis-ci.properties"), "jenesis.toolchain.searchpath=\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .as("even an empty search path is the user's to set, so a project file never names one")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.toolchain.searchpath cannot be set in");
    }

    @Test
    public void the_layered_settings_accepts_toolchain_searchpath_in_the_user_global_file() throws IOException {
        Path home = Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(home.resolve("jenesis.properties"), "jenesis.toolchain.searchpath=/opt/jdks/*\n");
        settings.put("make.global", root.resolve("home").toString());
        assertThat(Make.settings(root, settings).keys().get("toolchain.searchpath")).isEqualTo("/opt/jdks/*");
    }

    @Test
    public void the_layered_settings_rejects_docker_settings_in_the_project_file() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.project.docker=false\n");
        assertThatThrownBy(() -> Make.settings(root, Map.of()))
                .as("a project cannot switch off the isolation its user asked for")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.project.docker cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_docker_settings_in_a_profile() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.profiles=ci\n");
        Files.writeString(root.resolve("jenesis-ci.properties"), "jenesis.execute.docker.mountWritable=/\n");
        assertThatThrownBy(() -> Make.settings(root, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.execute.docker.mountWritable cannot be set in");
    }

    @Test
    public void the_layered_settings_accepts_docker_settings_in_the_user_global_file() throws IOException {
        Path home = Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(home.resolve("jenesis.properties"), "jenesis.project.docker=true\n");
        assertThat(Make.settings(root, Map.of("make.global", root.resolve("home").toString()))
                .keys()
                .get("project.docker")).isEqualTo("true");
    }

    @Test
    public void the_layered_settings_leave_the_running_jvm_untouched() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.test.sample.key=fromFile\n");
        assertThat(Make.settings(root, settings).keys().get("test.sample.key")).isEqualTo("fromFile");
        assertThat(System.getProperty("jenesis.test.sample.key"))
                .as("a build reads its settings off what it was handed, so reading a project's files"
                        + " no longer changes the properties of the JVM the build happens to run in")
                .isNull();
    }

    @Test
    public void the_layered_settings_records_the_settings_a_project_file_supplied() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.test.sample.a=fromProject\n");
        assertThat(Make.settings(root, settings).keys().get("make.provided"))
                .as("a repository url a project supplies is never handed a credential, so its keys are recorded")
                .isEqualTo("test.sample.a");
    }

    @Test
    public void the_layered_settings_records_nothing_a_project_file_only_repeated() throws IOException {
        settings.put("test.sample.a", "fromCommandLine");
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.test.sample.a=fromProject\n");
        Map<String, String> keys = Make.settings(root, settings).keys();
        assertThat(keys.get("test.sample.a")).isEqualTo("fromCommandLine");
        assertThat(keys.get("make.provided"))
                .as("the value in force is the one the command line set, so nothing was supplied")
                .isNull();
    }

    @Test
    public void the_layered_settings_rejects_a_credential_in_the_project_file() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.maven.token=Bearer secret\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.maven.token cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_a_credential_in_a_profile() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.profiles=ci\n");
        Files.writeString(root.resolve("jenesis-ci.properties"), "jenesis.module.token=Bearer secret\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.module.token cannot be set in");
    }

    @Test
    public void the_layered_settings_accepts_a_credential_in_the_user_global_file() throws IOException {
        Path home = Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(home.resolve("jenesis.properties"), "jenesis.maven.token=Bearer secret\n");
        settings.put("make.global", root.resolve("home").toString());
        assertThat(Make.settings(root, settings).keys().get("maven.token")).isEqualTo("Bearer secret");
    }

    @Test
    public void the_layered_settings_rejects_a_plaintext_permission_in_the_project_file() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.repository.insecure=true\n");
        assertThatThrownBy(() -> Make.settings(root, settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.repository.insecure cannot be set in");
    }

    @Test
    public void the_layered_settings_rejects_a_supplied_declaration_in_any_file() throws IOException {
        Path home = Files.createDirectories(root.resolve("home/.jenesis"));
        Files.writeString(home.resolve("jenesis.properties"), "jenesis.make.provided=jenesis.maven.uri\n");
        settings.put("make.global", root.resolve("home").toString());
        assertThatThrownBy(() -> Make.settings(root, settings))
                .as("what a project supplied is derived, never declared, least of all by a project")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.make.provided cannot be set in");
    }

    @Test
    public void the_layered_settings_tolerates_a_profile_without_a_properties_file() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.profiles=folder-only\n");
        assertThat(Make.settings(root, settings).profiles())
                .as("a profile may contribute only a configuration folder, so a missing properties file is not an error")
                .containsExactly(Path.of("folder-only"));
    }

    @Test
    public void keeps_every_default_where_no_setting_names_one() {
        Project project = Project.ofEnvironment(new Environment(Map.of()), Path.of("."));
        assertThat(project.target()).isEqualTo(Path.of("target"));
        assertThat(project.layout())
                .as("a wither is applied only where a setting is named, so an absent one keeps the default")
                .isEqualTo(Project.Layout.AUTO);
        assertThat(project.version()).isNull();
        assertThat(project.cache()).isNull();
    }
}
