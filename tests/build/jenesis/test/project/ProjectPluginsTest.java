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

    @TempDir
    private Path root;

    private BuildExecutor buildExecutor;

    @BeforeEach
    public void setUp() throws Exception {
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

        SequencedMap<String, Path> result = buildExecutor.execute("inspect");

        Path additions = result.get("transform/additions/module-app");
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

        SequencedMap<String, Path> result = buildExecutor.execute("inspect");

        assertThat(result.keySet().stream().filter(key -> key.startsWith("transform/") || key.startsWith("inspect/")))
                .containsExactly("transform/additions/module-app");
    }

    @Test
    public void runs_each_transform_after_the_ones_declared_before_it() throws IOException {
        wire(new ProjectPlugins()
                .transform("licenses", new Attach("licenses", "module-app"))
                .transform("notice", new AttachAfter("notice", "licenses.txt")));

        SequencedMap<String, Path> result = buildExecutor.execute("inspect");

        assertThat(result.get("transform/additions/module-app").resolve("attachment/notice/notice.txt")).hasContent("notice");
    }

    @Test
    public void refuses_an_addition_for_a_module_the_build_does_not_have() {
        wire(new ProjectPlugins().transform("licenses", new Attach("licenses", "module-other")));

        assertThatThrownBy(() -> buildExecutor.execute("inspect"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no module of this build");
    }

    @Test
    public void refuses_an_addition_that_is_neither_an_attachment_nor_a_report() {
        wire(new ProjectPlugins().transform("runtime", (_, context, _) -> {
            Files.writeString(context.next().resolve("extra.jar"), "jar");
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module-app.runtime.1", "extra.jar");
            inventory.store(context.next().resolve(Inventory.INVENTORY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }));

        assertThatThrownBy(() -> buildExecutor.execute("inspect"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only add <module>.attachment.<classifier> or <module>.report.<name>");
    }

    @Test
    public void fails_the_build_when_an_inspection_fails() {
        wire(new ProjectPlugins().inspect("audit", (_, _, _) -> {
            throw new IllegalStateException("no licence for app");
        }));

        assertThatThrownBy(() -> buildExecutor.execute("inspect"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .hasMessage("no licence for app");
    }

    @Test
    public void inspects_the_additions_of_the_transforms() {
        wire(new ProjectPlugins()
                .transform("licenses", new Attach("licenses", "module-app"))
                .inspect("audit", new RequireFile("licenses.txt")));

        assertThat(buildExecutor.execute("inspect")).containsKey("transform/additions/module-app");
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

        assertThatThrownBy(() -> buildExecutor.execute("inspect"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("An inspection changed what it inspects")
                .hasMessageContaining("app.jar");
    }

    @Test
    public void adds_nothing_without_a_transform_or_an_inspection() {
        wire(new ProjectPlugins());

        assertThat(buildExecutor.execute("inspect").keySet().stream().filter(key -> key.startsWith("transform/") || key.startsWith("inspect/"))).isEmpty();
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

        buildExecutor.execute("inspect");

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

        buildExecutor.execute("inspect");

        assertThat(received).containsExactly(new TreeMap<>(Map.of("holder", "Release")));
    }

    @Test
    public void refuses_an_argument_for_a_plugin_it_does_not_declare() throws IOException {
        Path arguments = Files.writeString(root.resolve("jenesis.plugins.arguments.properties"), "other.holder=Example\n");
        wire(new ProjectPlugins().arguments(arguments).transform("licenses", new Attach("licenses", "module-app")));

        assertThatThrownBy(() -> buildExecutor.execute("inspect"))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The argument other.holder")
                .hasMessageContaining("names no plugin of transform or inspect");
    }

    @Test
    public void refuses_a_transform_named_like_a_step_of_its_own() {
        assertThatThrownBy(() -> new ProjectPlugins().transform("additions", new Attach("licenses", "module-app")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add a transform named additions");
    }

    private void wire(ProjectPlugins plugins, Path... profiles) {
        buildExecutor.addModule("transform", plugins.transformModule(new LinkedHashSet<>(List.of(profiles))), "build");
        buildExecutor.addModule("inspect", plugins.inspectModule(new LinkedHashSet<>(List.of(profiles))), "build", "transform");
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
