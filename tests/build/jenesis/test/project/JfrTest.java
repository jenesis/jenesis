package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.project.Jfr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JfrTest {

    @TempDir
    private Path root;

    @Test
    public void records_into_the_reports_of_the_test_step_with_the_options_it_is_given() throws IOException {
        Path output = Files.createDirectory(root.resolve("output"));
        assertThat(new Jfr(new LinkedHashMap<>(Map.of("settings", "profile"))).commands(new LinkedHashMap<>(), output))
                .containsExactly("-XX:StartFlightRecording=filename="
                        + output.resolve(BuildStep.REPORTS + "jfr").resolve("tests.jfr").toAbsolutePath()
                        + ",dumponexit=true,settings=profile");
        assertThat(output.resolve(BuildStep.REPORTS + "jfr")).isDirectory();
    }

    @Test
    public void refuses_a_filename_which_the_build_sets() {
        assertThatThrownBy(() -> new Jfr(new LinkedHashMap<>(Map.of("filename", "elsewhere.jfr"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leave filename out of the options");
    }

    @Test
    public void refuses_an_option_that_would_split_the_recording_options() {
        assertThatThrownBy(() -> new Jfr(new LinkedHashMap<>(Map.of("settings", "default,profile"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holds no comma");
    }

    @Test
    public void refuses_an_option_without_a_value() {
        assertThatThrownBy(() -> new Jfr(new LinkedHashMap<>(Map.of("settings", ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'settings='");
    }
}
