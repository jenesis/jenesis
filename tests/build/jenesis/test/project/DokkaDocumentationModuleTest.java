package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Json;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.DokkaDocumentationModule;
import build.jenesis.project.InferredDocumentationChainModule;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DokkaDocumentationModuleTest {

    private static final List<String> CONFIGURATIONS = new CopyOnWriteArrayList<>();

    @TempDir
    private Path root, project, module;

    @BeforeEach
    public void clear() {
        CONFIGURATIONS.clear();
    }

    @Test
    public void a_failing_dokka_run_fails_the_step_naming_what_dokka_printed() throws IOException {
        sources();
        ToolProvider failing = new ToolProvider() {
            @Override
            public String name() {
                return "dokka";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                err.println("Dokka could not analyse sample/Sample.kt");
                return 1;
            }
        };
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dokka", dokka().factory(ProcessHandler.OfTool.of(failing)), "project");
        assertThatThrownBy(executor::execute)
                .as("documentation that Dokka could not render is not shipped as if it had been")
                .rootCause()
                .hasMessageContaining("Unexpected exit code: 1")
                .hasMessageContaining("Dokka could not analyse sample/Sample.kt");
    }

    @Test
    public void fails_to_document_kotlin_sources_when_no_dokka_jars_were_resolved() throws IOException {
        sources();
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dokka",
                new DokkaDocumentationModule(Map.of(), Map.of("module", Resolver.identity()))
                        .factory(ProcessHandler.OfTool.of(new Recording())),
                "project");
        assertThatThrownBy(executor::execute)
                .as("Kotlin sources left undocumented are not shipped as an empty documentation jar")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("No Dokka CLI and plugin jars were resolved in the dokka group");
        assertThat(CONFIGURATIONS).isEmpty();
    }

    @Test
    public void hands_dokka_a_json_configuration_with_the_defaults_of_the_dokka_plugins() throws IOException {
        Map<String, Object> configuration = document(new SequencedProperties());
        assertThat(configuration)
                .containsEntry("moduleName", "documentation")
                .containsEntry("failOnWarning", false)
                .containsEntry("suppressObviousFunctions", true)
                .containsEntry("suppressInheritedMembers", false)
                .doesNotContainKey("pluginsConfiguration");
        assertThat(list(configuration.get("pluginsClasspath")))
                .as("the plugins are every resolved jar but the command line itself")
                .isNotEmpty()
                .noneMatch(jar -> jar.toString().contains("dokka-cli"));
        Map<String, Object> sourceSet = sourceSet(configuration);
        assertThat(sourceSet)
                .containsEntry("analysisPlatform", "jvm")
                .containsEntry("skipEmptyPackages", true)
                .containsEntry("skipDeprecated", false)
                .containsEntry("reportUndocumented", false)
                .containsEntry("includes", List.of())
                .doesNotContainKeys("documentedVisibilities", "jdkVersion", "sourceLinks",
                        "externalDocumentationLinks", "perPackageOptions");
        assertThat(list(sourceSet.get("sourceRoots"))).singleElement()
                .satisfies(folder -> assertThat(Path.of(folder.toString()).resolve("sample/Sample.kt")).exists());
    }

    @Test
    public void hands_dokka_the_module_name_visibilities_and_flags_dokka_properties_sets() throws IOException {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("module-name", "Sample \"API\"");
        config.setProperty("documented-visibilities", "public, Protected,INTERNAL");
        config.setProperty("suppress-obvious-functions", "false");
        config.setProperty("suppress-inherited-members", "true");
        config.setProperty("skip-empty-packages", "false");
        config.setProperty("skip-deprecated", "true");
        config.setProperty("report-undocumented", "true");
        config.setProperty("fail-on-warning", "true");
        config.setProperty("jdk-version", "17");
        Map<String, Object> configuration = document(config);
        assertThat(configuration)
                .containsEntry("moduleName", "Sample \"API\"")
                .containsEntry("failOnWarning", true)
                .containsEntry("suppressObviousFunctions", false)
                .containsEntry("suppressInheritedMembers", true);
        assertThat(sourceSet(configuration))
                .containsEntry("documentedVisibilities", List.of("PUBLIC", "PROTECTED", "INTERNAL"))
                .containsEntry("skipEmptyPackages", false)
                .containsEntry("skipDeprecated", true)
                .containsEntry("reportUndocumented", true)
                .containsEntry("jdkVersion", 17.0)
                .containsEntry("sourceSetID", Map.of("scopeId", "Sample \"API\"", "sourceSetName", "main"));
    }

    @Test
    public void links_every_source_folder_to_the_repository_with_the_line_suffix_of_the_dokka_plugins()
            throws IOException {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("source-link", "https://github.com/example/sample/tree/main/src/main/kotlin");
        Map<String, Object> sourceSet = sourceSet(document(config));
        assertThat(list(sourceSet.get("sourceLinks"))).singleElement().isEqualTo(Map.of(
                "localDirectory", list(sourceSet.get("sourceRoots")).getFirst(),
                "remoteUrl", "https://github.com/example/sample/tree/main/src/main/kotlin",
                "remoteLineSuffix", "#L"));
        config.setProperty("source-link-suffix", "#lines-");
        Map<String, Object> suffixed = sourceSet(document(config));
        assertThat(list(suffixed.get("sourceLinks"))).singleElement().isEqualTo(Map.of(
                "localDirectory", list(suffixed.get("sourceRoots")).getFirst(),
                "remoteUrl", "https://github.com/example/sample/tree/main/src/main/kotlin",
                "remoteLineSuffix", "#lines-"));
    }

    @Test
    public void links_external_documentation_with_its_package_list_or_the_one_beside_it() throws IOException {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("external-link.okio", "https://square.github.io/okio/3.x/okio/");
        config.setProperty("external-link.okio.package-list", "https://square.github.io/okio/3.x/okio/okio/package-list");
        config.setProperty("external-link.coroutines", "https://kotlinlang.org/api/kotlinx.coroutines");
        assertThat(list(sourceSet(document(config)).get("externalDocumentationLinks"))).containsExactly(
                Map.of("url", "https://kotlinlang.org/api/kotlinx.coroutines",
                        "packageListUrl", "https://kotlinlang.org/api/kotlinx.coroutines/package-list"),
                Map.of("url", "https://square.github.io/okio/3.x/okio/",
                        "packageListUrl", "https://square.github.io/okio/3.x/okio/okio/package-list"));
    }

    @Test
    public void suppresses_every_package_a_pattern_of_suppressed_packages_matches() throws IOException {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("suppressed-packages", "sample\\.internal.*,sample\\.impl");
        assertThat(list(sourceSet(document(config)).get("perPackageOptions"))).containsExactly(
                suppressed("sample\\.internal.*"),
                suppressed("sample\\.impl"));
    }

    @Test
    public void binds_includes_assets_and_styles_from_the_module_folder_for_the_base_plugin() throws IOException {
        Files.writeString(Files.createDirectories(module.resolve("docs")).resolve("Module.md"), "# Module sample\n");
        Files.writeString(module.resolve("docs/logo.svg"), "<svg/>");
        Files.writeString(module.resolve("docs/custom.css"), ".custom {}");
        SequencedProperties config = new SequencedProperties();
        config.setProperty("includes", "docs/Module.md");
        config.setProperty("custom-assets", "docs/logo.svg");
        config.setProperty("custom-styles", "docs/custom.css");
        config.setProperty("footer-message", "(c) \"Sample\"");
        Map<String, Object> configuration = document(config);
        assertThat(list(sourceSet(configuration).get("includes"))).singleElement().satisfies(file -> {
            assertThat(Path.of(file.toString()).startsWith(module))
                    .as("Dokka reads a copy bound into the build, so its content is an input of the step")
                    .isFalse();
            assertThat(Path.of(file.toString())).hasContent("# Module sample");
        });
        Map<String, Object> plugin = map(list(configuration.get("pluginsConfiguration")).getFirst());
        assertThat(plugin)
                .containsEntry("fqPluginName", "org.jetbrains.dokka.base.DokkaBase")
                .containsEntry("serializationFormat", "JSON");
        Map<String, Object> base = map(Json.parse((String) plugin.get("values")));
        assertThat(base).containsEntry("footerMessage", "(c) \"Sample\"");
        assertThat(list(base.get("customAssets"))).singleElement()
                .satisfies(file -> assertThat(Path.of(file.toString())).hasFileName("logo.svg").hasContent("<svg/>"));
        assertThat(list(base.get("customStyleSheets"))).singleElement()
                .satisfies(file -> assertThat(Path.of(file.toString())).hasFileName("custom.css").hasContent(".custom {}"));
    }

    @Test
    public void runs_dokka_again_when_an_included_file_or_an_option_changes() throws IOException {
        sources();
        Path include = Files.writeString(module.resolve("Module.md"), "# Module sample\n\nFirst.\n");
        SequencedProperties config = new SequencedProperties();
        config.setProperty("includes", "Module.md");
        run(config);
        run(config);
        assertThat(CONFIGURATIONS).as("nothing changed, so Dokka does not run again").hasSize(1);
        Files.writeString(include, "# Module sample\n\nSecond.\n");
        run(config);
        assertThat(CONFIGURATIONS).as("an included file is an input of the step").hasSize(2);
        config.setProperty("footer-message", "Changed");
        run(config);
        assertThat(CONFIGURATIONS).as("an option is part of the step's cache key").hasSize(3);
    }

    @Test
    public void requires_the_javadoc_plugin_only_for_the_javadoc_format() throws IOException {
        assertThat(required(new SequencedProperties())).doesNotContain(
                "dokka/runtime/maven/org.jetbrains.dokka/javadoc-plugin/RELEASE");
        SequencedProperties config = new SequencedProperties();
        config.setProperty("format", "javadoc");
        assertThat(required(config)).contains(
                "dokka/runtime/maven/org.jetbrains.dokka/dokka-cli/RELEASE",
                "dokka/runtime/maven/org.jetbrains.dokka/dokka-base/RELEASE",
                "dokka/runtime/maven/org.jetbrains.dokka/javadoc-plugin/RELEASE");
    }

    @Test
    public void reads_dokka_properties_of_the_configuration_folder_and_resolves_its_files_against_the_module()
            throws IOException {
        sources();
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("dokka.properties"), "module-name=Configured\nincludes=Module.md\n");
        Files.writeString(module.resolve("Module.md"), "# Module Configured\n");
        Path jar = jar();
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("documentation",
                new InferredDocumentationChainModule(Map.of("maven", (_, _, _) -> Optional.of(RepositoryItem.ofFile(jar))),
                        Map.of("maven", Resolver.identity()))
                        .configuration(new LinkedHashSet<>(List.of(configuration)))
                        .location(module)
                        .dokka(dokka -> dokka.factory(ProcessHandler.OfTool.of(new Recording()))),
                "project");
        executor.execute();
        Map<String, Object> recorded = recorded();
        assertThat(recorded).containsEntry("moduleName", "Configured");
        assertThat(list(sourceSet(recorded).get("includes"))).singleElement()
                .satisfies(file -> assertThat(Path.of(file.toString())).hasContent("# Module Configured"));
    }

    @Test
    public void refuses_a_key_dokka_properties_does_not_know() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("moduleName", "sample");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown dokka property: moduleName")
                .hasMessageContaining("module-name")
                .hasMessageContaining("external-link.<name>.package-list");
    }

    @Test
    public void refuses_a_format_the_dokka_plugins_do_not_render() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("format", "gfm");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown dokka format: gfm (expected one of [html, javadoc])");
    }

    @Test
    public void refuses_a_visibility_dokka_does_not_know() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("documented-visibilities", "public,friends");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown dokka visibility in documented-visibilities: friends")
                .hasMessageContaining("[internal, package, private, protected, public]");
    }

    @Test
    public void refuses_a_jdk_version_that_is_no_feature_release() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("jdk-version", "1.8");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("The dokka property jdk-version is 1.8");
    }

    @Test
    public void refuses_a_line_suffix_without_a_source_link() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("source-link-suffix", "#L");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("declare source-link=");
    }

    @Test
    public void refuses_a_package_list_without_its_external_link() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("external-link.okio.package-list", "https://square.github.io/okio/3.x/okio/okio/package-list");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("declare external-link.okio=<url>");
    }

    @Test
    public void refuses_an_include_the_module_folder_does_not_hold() {
        SequencedProperties config = new SequencedProperties();
        config.setProperty("includes", "docs/Missing.md");
        assertThatThrownBy(() -> document(config))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("The dokka property includes names docs/Missing.md")
                .hasMessageContaining("name a file relative to the module's folder");
    }

    private Map<String, Object> document(SequencedProperties config) throws IOException {
        sources();
        CONFIGURATIONS.clear();
        run(config);
        return recorded();
    }

    private void run(SequencedProperties config) throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dokka",
                dokka().config(config).location(module).factory(ProcessHandler.OfTool.of(new Recording())),
                "project");
        executor.execute();
    }

    private static Map<String, Object> recorded() {
        assertThat(CONFIGURATIONS).as("Dokka ran once").hasSize(1);
        return map(Json.parse(CONFIGURATIONS.getFirst()));
    }

    private static Map<String, Object> sourceSet(Map<String, Object> configuration) {
        return map(list(configuration.get("sourceSets")).getFirst());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> suppressed(String pattern) {
        return Map.of("matchingRegex", pattern,
                "suppress", true,
                "includeNonPublic", false,
                "skipDeprecated", false,
                "documentedVisibilities", List.of());
    }

    private Set<String> required(SequencedProperties config) throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("dokka", dokka().config(config), "project");
        executor.execute("dokka/required");
        return SequencedProperties.ofFiles(root.resolve("dokka").resolve("required").resolve("output")
                .resolve(BuildStep.REQUIRES)).stringPropertyNames();
    }

    private void sources() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Sample.kt"), "package sample\nclass Sample");
    }

    private Path jar() throws IOException {
        Path jar = project.resolve("dokka.jar");
        return Files.exists(jar) ? jar : Files.createFile(jar);
    }

    private DokkaDocumentationModule dokka() throws IOException {
        Path jar = jar();
        return new DokkaDocumentationModule(
                Map.of("maven", (_, _, _) -> Optional.of(RepositoryItem.ofFile(jar))),
                Map.of("maven", Resolver.identity()));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }

    private record Recording() implements ToolProvider {

        @Override
        public String name() {
            return "dokka";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            try {
                CONFIGURATIONS.add(Files.readString(Path.of(args[args.length - 1])));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return 0;
        }
    }
}
