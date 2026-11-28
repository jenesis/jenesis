package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Palette;
import build.jenesis.step.Warnings;

import static org.assertj.core.api.Assertions.assertThat;

public class WarningsTest {

    @TempDir
    private Path root;

    @Test
    public void prints_a_warning_in_the_first_build_and_in_no_build_after_it() throws IOException {
        List<String> printed = new ArrayList<>();
        build(warnings("the pom.xml names no release"), printed::add);
        build(warnings("the pom.xml names no release"), printed::add);
        assertThat(printed).containsExactly("[RELEASE]   the pom.xml names no release");
    }

    @Test
    public void prints_a_warning_that_changed() throws IOException {
        List<String> printed = new ArrayList<>();
        build(warnings("the pom.xml names no release"), printed::add);
        build(warnings("the pom.xml names a release that does not exist"), printed::add);
        build(warnings("the pom.xml names a release that does not exist"), printed::add);
        assertThat(printed).containsExactly("[RELEASE]   the pom.xml names no release",
                "[RELEASE]   the pom.xml names a release that does not exist");
    }

    @Test
    public void keeps_a_warning_for_the_first_build_that_prints() throws IOException {
        List<String> printed = new ArrayList<>();
        build(warnings("the pom.xml names no release"), null);
        assertThat(printed).as("a build without a consumer prints nothing").isEmpty();
        build(warnings("the pom.xml names no release"), printed::add);
        build(warnings("the pom.xml names no release"), null);
        build(warnings("the pom.xml names no release"), printed::add);
        assertThat(printed)
                .as("the build that did not print leaves the warning to the next one that does")
                .containsExactly("[RELEASE]   the pom.xml names no release");
    }

    private static Warnings warnings(String warning) {
        SequencedMap<String, String> warnings = new LinkedHashMap<>();
        warnings.put(warning, "[RELEASE]");
        return new Warnings(_ -> warnings);
    }

    private void build(Warnings warnings, Consumer<String> printing) throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addStep("warnings", warnings.printing(printing, Palette.NONE));
        executor.execute(Runnable::run).toCompletableFuture().join();
    }
}
