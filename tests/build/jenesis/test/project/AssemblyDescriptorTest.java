package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.AssemblyRequirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AssemblyDescriptorTest {

    private static final BuildExecutorModule NONE = (_, _) -> {
    };

    @TempDir
    private Path root;

    @Test
    public void a_requirement_names_one_phase_per_call_in_the_order_they_were_named_and_leaves_its_origin_as_it_was() {
        AssemblyRequirement empty = AssemblyRequirement.of();
        AssemblyRequirement requirement = empty.after("build").after("verify").upstream("build").tested("build");
        assertThat(requirement.after()).containsExactly("build", "verify");
        assertThat(requirement.upstream()).containsExactly("build");
        assertThat(requirement.tested()).containsExactly("build");
        assertThat(empty.after()).isEmpty();
    }

    @Test
    public void refuses_a_phase_name_with_a_hyphen_since_it_separates_the_phase_from_the_module() {
        assertThatThrownBy(() -> build().andThen("check-sources", NONE, AssemblyRequirement.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check-sources");
    }

    @Test
    public void refuses_to_follow_a_phase_that_is_not_declared_before() {
        assertThatThrownBy(() -> build().andThen("verify", NONE, AssemblyRequirement.of().after("document")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document")
                .hasMessageContaining(AssemblyDescriptor.BUILD);
    }

    @Test
    public void refuses_to_extend_a_phase_with_a_requirement_that_follows_a_phase_declared_after_it() {
        AssemblyDescriptor assembly = build().andThen("verify", NONE, AssemblyRequirement.of().after(AssemblyDescriptor.BUILD));
        assertThatThrownBy(() -> assembly.andThen(AssemblyDescriptor.BUILD, NONE, AssemblyRequirement.of().after("verify")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verify");
    }

    @Test
    public void extends_a_phase_declared_already_with_the_steps_of_another_module_after_its_own() throws IOException {
        AssemblyDescriptor assembly = AssemblyDescriptor.of(writing("first"), AssemblyRequirement.of())
                .andThen(AssemblyDescriptor.BUILD, writing("second"), AssemblyRequirement.of());

        SequencedMap<String, Path> steps = execute(assembly.toBuildModule());

        assertThat(steps).containsKeys("build/first", "build/second");
    }

    @Test
    public void nests_a_phase_inside_a_module_of_its_own_and_exposes_its_steps_as_before() throws IOException {
        AssemblyDescriptor assembly = AssemblyDescriptor.of(writing("first"), AssemblyRequirement.of())
                .nest(AssemblyDescriptor.BUILD, "assemble", build -> (executor, _) -> {
                    executor.addModule("describe", writing("pom"));
                    executor.addModule("assemble", build, "describe");
                })
                .nest(AssemblyDescriptor.BUILD, "produce", build -> (executor, _) -> executor.addModule("produce", build));

        SequencedMap<String, Path> steps = execute(assembly.toBuildModule());

        assertThat(steps).containsKeys("build/first", "build/describe/pom");
        assertThat(steps.get("build/first")).isEqualTo(root.resolve("build/produce/assemble/first/output"));
    }

    @Test
    public void refuses_to_nest_a_phase_it_does_not_declare() {
        assertThatThrownBy(() -> build().nest("verify", "nested", UnaryOperator.identity()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verify")
                .hasMessageContaining(AssemblyDescriptor.BUILD);
    }

    private SequencedMap<String, Path> execute(BuildExecutorModule module) throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addModule(AssemblyDescriptor.BUILD, module);
        return executor.execute();
    }

    private static BuildExecutorModule writing(String name) {
        return (executor, _) -> executor.addStep(name, (_, context, _) -> {
            Files.writeString(context.next().resolve(name + ".txt"), name);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        });
    }

    private static AssemblyDescriptor build() {
        return AssemblyDescriptor.of(NONE, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
    }
}
