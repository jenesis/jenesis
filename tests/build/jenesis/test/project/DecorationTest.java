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
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.Decoration;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.ProjectModule;
import build.jenesis.project.ProjectModuleDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DecorationTest implements Serializable {

    @TempDir
    private Path root;

    @Test
    public void adds_a_step_after_the_decorated_build_under_a_name_the_build_declares_itself() throws IOException {
        MultiProjectAssembler<ProjectModuleDescriptor> assembler = new Decoration("assemble")
                .after(_ -> (sub, _) -> sub.addStep("sign", text("signed|"), "assemble"))
                .around(stock());
        SequencedMap<String, Path> outputs = execute(assembler, "module/sign");
        assertThat(outputs.get("module/sign").resolve("text"))
                .as("the decoration's sign step sits beside the build, which declares a sign step of its own")
                .hasContent("source|stock|signed|");
    }

    @Test
    public void hands_the_decorated_build_the_steps_its_redirected_descriptor_names() throws IOException {
        MultiProjectAssembler<ProjectModuleDescriptor> assembler = new Decoration("assemble")
                .descriptor(descriptor -> descriptor.sources("preprocess"))
                .before(descriptor -> (sub, _) -> sub.addStep("preprocess", text("preprocessed|"), descriptor.sources().stream()))
                .around(stock());
        SequencedMap<String, Path> outputs = execute(assembler, "module/assemble/sign");
        assertThat(outputs.get("module/assemble/sign").resolve("text")).hasContent("source|preprocessed|stock|");
    }

    @Test
    public void nests_a_decoration_of_a_decorated_build_one_level_deeper() throws IOException {
        MultiProjectAssembler<ProjectModuleDescriptor> assembler = new Decoration("assemble")
                .after(_ -> (sub, _) -> sub.addStep("sign", text("outer|"), "assemble/sign"))
                .around(new Decoration("assemble")
                        .after(_ -> (sub, _) -> sub.addStep("sign", text("inner|"), "assemble"))
                        .around(stock()));
        SequencedMap<String, Path> outputs = execute(assembler, "module/sign");
        assertThat(outputs.get("module/sign").resolve("text")).hasContent("source|stock|inner|outer|");
    }

    @Test
    public void skips_a_hook_that_returns_no_module() throws IOException {
        MultiProjectAssembler<ProjectModuleDescriptor> assembler = new Decoration("assemble")
                .before(_ -> null)
                .after(_ -> null)
                .around(stock());
        SequencedMap<String, Path> outputs = execute(assembler, "module/assemble/sign");
        assertThat(outputs.get("module/assemble/sign").resolve("text")).hasContent("source|stock|");
    }

    @Test
    public void refuses_a_name_of_more_than_one_segment() {
        assertThatThrownBy(() -> new Decoration("assemble/nested"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assemble/nested");
    }

    private static MultiProjectAssembler<ProjectModuleDescriptor> stock() {
        return (descriptor, _, _) -> new AssemblyDescriptor((sub, _) ->
                sub.addStep("sign", text("stock|"), descriptor.sources().stream()));
    }

    private static BuildStep text(String text) {
        return (_, context, arguments) -> {
            StringBuilder content = new StringBuilder();
            for (BuildStepArgument argument : arguments.values()) {
                Path file = argument.folder().resolve("text");
                if (Files.isRegularFile(file)) {
                    content.append(Files.readString(file));
                }
            }
            Files.writeString(context.next().resolve("text"), content + text);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        };
    }

    private SequencedMap<String, Path> execute(MultiProjectAssembler<ProjectModuleDescriptor> assembler,
                                               String selector) throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("text"), "source|");
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(new ProjectModule() {
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
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> coordinates() {
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> artifacts() {
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> spdx() {
                return Collections.emptyNavigableSet();
            }
        });
        BuildExecutor executor = BuildExecutor.of(Files.createDirectory(root.resolve("build")),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("sources", sources);
        executor.addModule("module", assembler.apply(descriptor, Map.of(), Map.of()).build(), "sources");
        return executor.execute(Runnable::run, selector).toCompletableFuture().join();
    }
}
