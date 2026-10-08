package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.step.FormatBuildStep;

import static org.assertj.core.api.Assertions.assertThat;

public class FormatBuildStepTest {

    @TempDir
    private Path root;

    @Test
    public void a_verifying_formatter_is_skipped_when_none_of_its_inputs_changed() {
        assertThat(new Formatter(true).shouldRun(arguments(ChecksumStatus.RETAINED)))
                .as("a check of unchanged sources has nothing new to report")
                .isFalse();
    }

    @Test
    public void a_verifying_formatter_runs_when_a_source_changed() {
        assertThat(new Formatter(true).shouldRun(arguments(ChecksumStatus.ALTERED))).isTrue();
    }

    @Test
    public void a_rewriting_formatter_runs_on_every_build() {
        assertThat(new Formatter(false).shouldRun(arguments(ChecksumStatus.RETAINED))).isTrue();
    }

    private SequencedMap<String, BuildStepArgument> arguments(ChecksumStatus status) {
        return new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(root,
                Map.of(Path.of("sources/Sample.java"), Checksum.of(status)))));
    }

    private static class Formatter extends FormatBuildStep {

        private Formatter(boolean verify) {
            super("formatter", "formatter", verify);
        }

        @Override
        protected boolean isFormattable(Path file) {
            return true;
        }

        @Override
        protected List<String> command(List<String> jars, Path config, List<String> files, boolean verify) {
            return files;
        }
    }
}
