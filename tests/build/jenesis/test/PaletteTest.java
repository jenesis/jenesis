package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.Environment;
import build.jenesis.Palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PaletteTest {

    @Test
    public void colours_unless_told_otherwise() {
        assertThat(Palette.ofEnvironment(Environment.NONE)).isEqualTo(Palette.ANSI);
        assertThat(Palette.ofEnvironment(new Environment(Map.of("print.color", "true")))).isEqualTo(Palette.ANSI);
        assertThat(Palette.ANSI.green()).isEqualTo("\033[32m");
        assertThat(Palette.ANSI.color(244)).isEqualTo("\033[38;5;244m");
    }

    @Test
    public void prints_plain_text_when_switched_off() {
        Palette palette = Palette.ofEnvironment(new Environment(Map.of("print.color", "false")));
        assertThat(palette).isEqualTo(Palette.PLAIN);
        assertThat(Stream.of(palette.reset(),
                        palette.red(),
                        palette.green(),
                        palette.yellow(),
                        palette.blue(),
                        palette.cyan(),
                        palette.color(244),
                        palette.bold(39)))
                .as("a plain palette writes nothing where a colour would go, so the text around it stays as it is")
                .allMatch(String::isEmpty);
    }

    @Test
    public void refuses_a_value_that_is_no_flag() {
        assertThatThrownBy(() -> Palette.ofEnvironment(new Environment(Map.of("print.color", "auto"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("print.color");
    }

    @Test
    public void the_executor_configuration_takes_the_palette_of_its_environment() {
        assertThat(new BuildExecutor.Configuration().palette()).isEqualTo(Palette.ANSI);
        assertThat(BuildExecutor.Configuration.ofEnvironment(new Environment(Map.of("print.color", "false"))).palette())
                .isEqualTo(Palette.PLAIN);
    }
}
