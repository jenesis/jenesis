package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorException;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.ProjectPlugins;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProjectPluginsTest {

    private static final Queue<String> RAN = new ConcurrentLinkedQueue<>();

    @TempDir
    private Path root;

    private BuildExecutor buildExecutor;

    @BeforeEach
    public void setUp() throws Exception {
        RAN.clear();
        buildExecutor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        buildExecutor.addModule("build", (build, _) -> build.addStep("inventory", (_, context, _) -> {
            Files.writeString(context.next().resolve("app.jar"), "jar");
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module-app.path", "app");
            inventory.setProperty("module-app.artifacts.0", "app.jar");
            inventory.store(context.next().resolve(Inventory.INVENTORY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }));
    }

    @Test
    public void adds_what_a_transform_attaches_to_the_inventory_of_its_module() throws IOException {
        wire(new ProjectPlugins().transform("licenses", new Attach("licenses", "module-app")));

        SequencedMap<String, Path> result = buildExecutor.execute("postprocess");

        Path additions = result.get("postprocess/additions/module-app");
        assertThat(additions).isNotNull();
        assertThat(SequencedProperties.ofFiles(additions.resolve(Inventory.INVENTORY)).getProperty("module-app.attachment.licenses"))
                .isEqualTo("attachment/licenses/licenses.txt");
        assertThat(additions.resolve("attachment/licenses/licenses.txt")).hasContent("licenses");
    }

    @Test
    public void exports_nothing_but_the_additions() {
        wire(new ProjectPlugins()
                .transform("licenses", new Attach("licenses", "module-app"))
                .inspect("audit", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true))));

        SequencedMap<String, Path> result = buildExecutor.execute("postprocess");

        assertThat(result.keySet().stream().filter(key -> key.startsWith("postprocess/")))
                .containsExactly("postprocess/additions/module-app", "postprocess/project");
    }

    @Test
    public void runs_each_transform_after_the_ones_declared_before_it() throws IOException {
        wire(new ProjectPlugins()
                .transform("licenses", new Attach("licenses", "module-app"))
                .transform("notice", new AttachAfter("notice", "licenses.txt")));

        SequencedMap<String, Path> result = buildExecutor.execute("postprocess");

        assertThat(result.get("postprocess/additions/module-app").resolve("attachment/notice/notice.txt")).hasContent("notice");
    }

    @Test
    public void refuses_an_addition_for_a_module_the_build_does_not_have() {
        wire(new ProjectPlugins().transform("licenses", new Attach("licenses", "module-other")));

        assertThatThrownBy(() -> buildExecutor.execute("postprocess"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no module of this build");
    }

    @Test
    public void adds_a_file_under_any_key_a_transform_names() throws IOException {
        wire(new ProjectPlugins().transform("runtime", (_, context, _) -> {
            Files.writeString(context.next().resolve("extra.jar"), "jar");
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module-app.runtime.1", "extra.jar");
            inventory.store(context.next().resolve(Inventory.INVENTORY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }));

        Path additions = buildExecutor.execute("postprocess").get("postprocess/additions/module-app");

        assertThat(SequencedProperties.ofFiles(additions.resolve(Inventory.INVENTORY)).getProperty("module-app.runtime.1"))
                .isEqualTo("runtime/1/extra.jar");
    }

    @Test
    public void refuses_an_addition_whose_key_names_nothing() {
        wire(new ProjectPlugins().transform("runtime", (_, context, _) -> {
            Files.writeString(context.next().resolve("extra.jar"), "jar");
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module-app.", "extra.jar");
            inventory.store(context.next().resolve(Inventory.INVENTORY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }));

        assertThatThrownBy(() -> buildExecutor.execute("postprocess"))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name what it adds as <module>.<key>");
    }

    @Test
    public void gathers_what_the_transforms_place_in_the_project() throws IOException {
        wire(new ProjectPlugins()
                .transform("coverage", new PlaceInProject("reports/coverage/index.html"))
                .transform("site", new PlaceInProject("site/index.html")));

        Path project = buildExecutor.execute("postprocess").get("postprocess/project");

        assertThat(project.resolve("project/reports/coverage/index.html")).hasContent("reports/coverage/index.html");
        assertThat(project.resolve("project/site/index.html")).hasContent("site/index.html");
    }

    @Test
    public void refuses_a_file_two_transforms_place_in_the_project() {
        wire(new ProjectPlugins()
                .transform("coverage", new PlaceInProject("reports/index.html"))
                .transform("site", new PlaceInProject("reports/index.html")));

        assertThatThrownBy(() -> buildExecutor.execute("postprocess"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("places reports/index.html in the project, where another plugin placed it already");
    }

    @Test
    public void fails_the_build_when_an_inspection_fails() {
        wire(new ProjectPlugins().inspect("audit", (_, _, _) -> {
            throw new IllegalStateException("no licence for app");
        }));

        assertThatThrownBy(() -> buildExecutor.execute("postprocess"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .hasMessage("no licence for app");
    }

    @Test
    public void inspects_the_additions_of_the_transforms() {
        wire(new ProjectPlugins()
                .transform("licenses", new Attach("licenses", "module-app"))
                .inspect("audit", new RequireFile("licenses.txt")));

        assertThat(buildExecutor.execute("postprocess")).containsKey("postprocess/additions/module-app");
    }

    @Test
    public void fails_the_build_when_an_inspection_changes_what_it_inspects() {
        wire(new ProjectPlugins().inspect("audit", (_, _, arguments) -> {
            for (BuildStepArgument argument : arguments.values()) {
                Path jar = argument.folder().resolve("app.jar");
                if (Files.isRegularFile(jar)) {
                    Files.writeString(jar, "changed", StandardOpenOption.APPEND);
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }));

        assertThatThrownBy(() -> buildExecutor.execute("postprocess"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("An inspection changed what it inspects")
                .hasMessageContaining("app.jar");
    }

    @Test
    public void adds_nothing_without_a_transform_or_an_inspection() {
        wire(new ProjectPlugins());

        assertThat(buildExecutor.execute("postprocess").keySet().stream().filter(key -> key.startsWith("postprocess/"))).isEmpty();
    }

    @Test
    public void refuses_a_second_transform_of_the_same_name() {
        ProjectPlugins plugins = new ProjectPlugins().transform("licenses", new Attach("licenses", "module-app"));

        assertThatThrownBy(() -> plugins.transform("licenses", new Attach("notice", "module-app")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A transform named licenses is added already");
    }

    @Test
    public void hands_a_transform_the_values_its_arguments_file_names() throws IOException {
        Path arguments = Files.writeString(root.resolve("jenesis.plugins.arguments.properties"), "licenses.holder=Example\n");
        List<SequencedMap<String, String>> received = new ArrayList<>();
        wire(new ProjectPlugins().arguments(arguments).transforms(new LinkedHashMap<>(Map.of("licenses", values -> {
            received.add(values);
            return new Attach("licenses", "module-app").asModule("licenses");
        }))));

        buildExecutor.execute("postprocess");

        assertThat(received).containsExactly(new TreeMap<>(Map.of("holder", "Example")));
    }

    @Test
    public void lets_the_arguments_of_an_active_profile_win() throws IOException {
        Path arguments = Files.writeString(root.resolve("jenesis.plugins.arguments.properties"), "licenses.holder=Example\n");
        Files.writeString(root.resolve("jenesis.plugins.arguments-release.properties"), "licenses.holder=Release\n");
        List<SequencedMap<String, String>> received = new ArrayList<>();
        wire(new ProjectPlugins().arguments(arguments).transforms(new LinkedHashMap<>(Map.of("licenses", values -> {
            received.add(values);
            return new Attach("licenses", "module-app").asModule("licenses");
        }))), Path.of("release"));

        buildExecutor.execute("postprocess");

        assertThat(received).containsExactly(new TreeMap<>(Map.of("holder", "Release")));
    }

    @Test
    public void refuses_an_argument_for_a_plugin_it_does_not_declare() throws IOException {
        Path arguments = Files.writeString(root.resolve("jenesis.plugins.arguments.properties"), "other.holder=Example\n");
        wire(new ProjectPlugins().arguments(arguments).transform("licenses", new Attach("licenses", "module-app")));

        assertThatThrownBy(() -> buildExecutor.execute("postprocess"))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The argument other.holder")
                .hasMessageContaining("names no plugin of the whole project");
    }

    @Test
    public void lets_a_transform_take_a_name_the_build_gives_a_step_of_its_own() {
        wire(new ProjectPlugins().transform("additions", new Attach("licenses", "module-app")));

        assertThat(buildExecutor.execute("postprocess")).containsKey("postprocess/additions/module-app");
    }

    @Test
    public void runs_a_preprocessor_before_what_depends_on_it() {
        buildExecutor.addModule("preprocess", new ProjectPlugins().preprocess("headers", (_, _, _) -> {
            RAN.add("headers");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }).preprocess(new LinkedHashSet<>()));
        buildExecutor.addStep("compile", (_, _, _) -> {
            RAN.add("compile");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, "preprocess");

        buildExecutor.execute("compile");

        assertThat(RAN).containsExactly("headers", "compile");
    }

    @Test
    public void stops_what_depends_on_a_preprocessor_that_fails() {
        buildExecutor.addModule("preprocess", new ProjectPlugins().preprocess("headers", (_, _, _) -> {
            throw new IllegalStateException("a header is missing");
        }).preprocess(new LinkedHashSet<>()));
        buildExecutor.addStep("compile", (_, _, _) -> {
            RAN.add("compile");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, "preprocess");

        assertThatThrownBy(() -> buildExecutor.execute("compile"))
                .rootCause()
                .hasMessage("a header is missing");
        assertThat(RAN).isEmpty();
    }

    @Test
    public void hands_nothing_a_preprocessor_writes_to_what_depends_on_it() {
        buildExecutor.addModule("preprocess", new ProjectPlugins().preprocess("headers", (_, context, _) -> {
            Files.writeString(context.next().resolve("headers.txt"), "checked");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }).preprocess(new LinkedHashSet<>()));

        assertThat(buildExecutor.execute("preprocess").keySet()).noneMatch(key -> key.startsWith("preprocess/"));
    }

    @Test
    public void hands_an_exporter_what_its_module_depends_on() {
        buildExecutor.addModule("export",
                new ProjectPlugins().export("publish", new RequireFile("app.jar")).export(new LinkedHashSet<>()),
                "build");

        assertThat(buildExecutor.execute("export").keySet()).anyMatch(key -> key.startsWith("export/custom/publish"));
    }

    @Test
    public void hands_a_releaser_what_its_module_depends_on() {
        buildExecutor.addModule("release",
                new ProjectPlugins().release("announce", new RequireFile("app.jar")).release(new LinkedHashSet<>()),
                "build");

        assertThat(buildExecutor.execute("release").keySet()).anyMatch(key -> key.startsWith("release/custom/announce"));
    }

    @Test
    public void runs_a_goal_under_its_own_name_even_when_it_takes_the_name_of_the_pins_file() throws IOException {
        Path pins = Files.writeString(root.resolve("jenesis.plugins.pin.properties"), "");
        buildExecutor.addModule("plugin",
                new ProjectPlugins().pins(pins).goal("bench", new RequireFile("app.jar")).goal(new LinkedHashSet<>()),
                "build");

        assertThat(buildExecutor.execute("plugin/bench").keySet()).anyMatch(key -> key.startsWith("plugin/bench"));
    }

    @Test
    public void refuses_a_second_exporter_of_the_same_name() {
        ProjectPlugins plugins = new ProjectPlugins().export("publish", new RequireFile("app.jar"));

        assertThatThrownBy(() -> plugins.export("publish", new RequireFile("app.jar")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("An exporter named publish is added already");
    }

    @Test
    public void lets_a_plugin_take_the_name_of_the_pins_it_resolves_with() throws IOException {
        Path pins = Files.writeString(root.resolve("jenesis.plugins.pin.properties"), "");
        buildExecutor.addModule("resolved", new ProjectPlugins().pins(pins).resolutions(new LinkedHashMap<>(Map.of("pins",
                ((BuildStep) (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true))).asModule("pins"))))
                .resolution());

        assertThat(buildExecutor.execute("resolved").keySet()).anyMatch(key -> key.startsWith("resolved/custom/pins"));
    }

    private void wire(ProjectPlugins plugins, Path... profiles) {
        buildExecutor.addModule("postprocess", plugins.postprocess(new LinkedHashSet<>(List.of(profiles))), "build");
    }

    private record Attach(String classifier, String prefix) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Files.writeString(context.next().resolve(classifier + ".txt"), classifier);
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty(prefix + ".attachment." + classifier, classifier + ".txt");
            inventory.store(context.next().resolve(Inventory.INVENTORY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record PlaceInProject(String path) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path file = context.next().resolve("project").resolve(path);
            Files.createDirectories(file.getParent());
            Files.writeString(file, path);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record AttachAfter(String classifier, String required) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            new RequireFile(required).apply(executor, context, arguments);
            return new Attach(classifier, "module-app").apply(executor, context, arguments);
        }
    }

    private record RequireFile(String name) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            for (BuildStepArgument argument : arguments.values()) {
                try (Stream<Path> files = Files.walk(argument.folder())) {
                    if (files.anyMatch(file -> file.getFileName().toString().equals(name))) {
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }
                }
            }
            throw new IllegalStateException("Did not see " + name + " in " + arguments.keySet());
        }
    }
}
