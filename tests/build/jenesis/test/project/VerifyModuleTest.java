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
import build.jenesis.project.VerifyModule;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VerifyModuleTest {

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
        buildExecutor.addModule("verify", new VerifyModule().transform("licenses", new Attach("licenses", "module-app")), "build");

        SequencedMap<String, Path> result = buildExecutor.execute("verify");

        Path additions = result.get("verify/additions/module-app");
        assertThat(additions).isNotNull();
        assertThat(SequencedProperties.ofFiles(additions.resolve(Inventory.INVENTORY)).getProperty("module-app.attachment.licenses"))
                .isEqualTo("attachment/licenses/licenses.txt");
        assertThat(additions.resolve("attachment/licenses/licenses.txt")).hasContent("licenses");
    }

    @Test
    public void exports_nothing_but_the_additions() {
        buildExecutor.addModule("verify", new VerifyModule()
                .transform("licenses", new Attach("licenses", "module-app"))
                .inspect("audit", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true))), "build");

        SequencedMap<String, Path> result = buildExecutor.execute("verify");

        assertThat(result.keySet().stream().filter(key -> key.startsWith("verify/")))
                .containsExactly("verify/additions/module-app");
    }

    @Test
    public void runs_each_transform_after_the_ones_declared_before_it() throws IOException {
        buildExecutor.addModule("verify", new VerifyModule()
                .transform("licenses", new Attach("licenses", "module-app"))
                .transform("notice", new AttachAfter("notice", "licenses.txt")), "build");

        SequencedMap<String, Path> result = buildExecutor.execute("verify");

        assertThat(result.get("verify/additions/module-app").resolve("attachment/notice/notice.txt")).hasContent("notice");
    }

    @Test
    public void refuses_an_addition_for_a_module_the_build_does_not_have() {
        buildExecutor.addModule("verify", new VerifyModule().transform("licenses", new Attach("licenses", "module-other")), "build");

        assertThatThrownBy(() -> buildExecutor.execute("verify"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no module of this build");
    }

    @Test
    public void refuses_an_addition_that_is_neither_an_attachment_nor_a_report() {
        buildExecutor.addModule("verify", new VerifyModule().transform("runtime", (_, context, _) -> {
            Files.writeString(context.next().resolve("extra.jar"), "jar");
            SequencedProperties inventory = new SequencedProperties();
            inventory.setProperty("module-app.runtime.1", "extra.jar");
            inventory.store(context.next().resolve(Inventory.INVENTORY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }), "build");

        assertThatThrownBy(() -> buildExecutor.execute("verify"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only add <module>.attachment.<classifier> or <module>.report.<name>");
    }

    @Test
    public void fails_the_build_when_an_inspection_fails() {
        buildExecutor.addModule("verify", new VerifyModule().inspect("audit", (_, _, _) -> {
            throw new IllegalStateException("no licence for app");
        }), "build");

        assertThatThrownBy(() -> buildExecutor.execute("verify"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .hasMessage("no licence for app");
    }

    @Test
    public void inspects_the_additions_of_the_transforms() {
        buildExecutor.addModule("verify", new VerifyModule()
                .transform("licenses", new Attach("licenses", "module-app"))
                .inspect("audit", new RequireFile("licenses.txt")), "build");

        assertThat(buildExecutor.execute("verify")).containsKey("verify/additions/module-app");
    }

    @Test
    public void fails_the_build_when_an_inspection_changes_what_it_inspects() {
        buildExecutor.addModule("verify", new VerifyModule().inspect("audit", (_, _, arguments) -> {
            for (BuildStepArgument argument : arguments.values()) {
                Path jar = argument.folder().resolve("app.jar");
                if (Files.isRegularFile(jar)) {
                    Files.writeString(jar, "changed", StandardOpenOption.APPEND);
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }), "build");

        assertThatThrownBy(() -> buildExecutor.execute("verify"))
                .isInstanceOf(BuildExecutorException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("An inspection changed what it inspects")
                .hasMessageContaining("app.jar");
    }

    @Test
    public void adds_nothing_without_a_transform_or_an_inspection() {
        buildExecutor.addModule("verify", new VerifyModule(), "build");

        assertThat(buildExecutor.execute("verify").keySet().stream().filter(key -> key.startsWith("verify/"))).isEmpty();
    }

    @Test
    public void refuses_a_second_transform_of_the_same_name() {
        VerifyModule verify = new VerifyModule().transform("licenses", new Attach("licenses", "module-app"));

        assertThatThrownBy(() -> verify.transform("licenses", new Attach("notice", "module-app")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A transform named licenses is added already");
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
