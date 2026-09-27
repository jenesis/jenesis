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
    public void records_into_the_reports_of_the_test_step_with_the_options_of_the_file() throws IOException {
        Path file = Files.writeString(root.resolve("jfr.properties"), "settings=profile");
        Path output = Files.createDirectory(root.resolve("output"));
        assertThat(Jfr.ofFile(file).commands(new LinkedHashMap<>(), output)).containsExactly("-XX:StartFlightRecording=filename="
                + output.resolve(BuildStep.REPORTS + "jfr").resolve("tests.jfr").toAbsolutePath()
                + ",dumponexit=true,settings=profile");
        assertThat(output.resolve(BuildStep.REPORTS + "jfr")).isDirectory();
    }

    @Test
    public void refuses_a_filename_which_the_build_sets() throws IOException {
        Path file = Files.writeString(root.resolve("jfr.properties"), "filename=elsewhere.jfr");
        assertThatThrownBy(() -> Jfr.ofFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remove the filename line");
    }

    @Test
    public void refuses_an_option_that_would_split_the_recording_options() throws IOException {
        Path file = Files.writeString(root.resolve("jfr.properties"), "settings=default,profile");
        assertThatThrownBy(() -> Jfr.ofFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holds no comma");
    }

    @Test
    public void refuses_an_option_without_a_value() throws IOException {
        Path file = Files.writeString(root.resolve("jfr.properties"), "settings");
        assertThatThrownBy(() -> Jfr.ofFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gives settings no value");
    }
}
