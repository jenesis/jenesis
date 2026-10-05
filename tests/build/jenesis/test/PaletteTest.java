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
    public void colors_with_ansi_unless_told_otherwise() {
        assertThat(Palette.ofEnvironment(Environment.NONE)).isEqualTo(Palette.ANSI);
        assertThat(Palette.ofEnvironment(new Environment(Map.of("palette.colors", "ansi")))).isEqualTo(Palette.ANSI);
        assertThat(Palette.ANSI.status()).isEqualTo("\033[32m");
        assertThat(Palette.ANSI.output()).isEqualTo("\033[38;5;244m");
    }

    @Test
    public void prints_plain_text_without_colors() {
        Palette palette = Palette.ofEnvironment(new Environment(Map.of("palette.colors", "none")));
        assertThat(palette).isEqualTo(Palette.NONE);
        assertThat(Stream.of(palette.reset(),
                        palette.status(),
                        palette.failure(),
                        palette.skipped(),
                        palette.info(),
                        palette.warning(),
                        palette.heading(),
                        palette.detail(),
                        palette.output(),
                        palette.error(),
                        palette.color(244),
                        palette.bold(39)))
                .as("without colors a palette writes nothing where a color would go, so the text around it stays as it is")
                .allMatch(String::isEmpty);
    }

    @Test
    public void refuses_an_unknown_palette_naming_the_known_ones() {
        assertThatThrownBy(() -> Palette.ofEnvironment(new Environment(Map.of("palette.colors", "auto"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.palette.colors 'auto'")
                .hasMessageContaining("'ansi' or 'none'");
    }

    @Test
    public void the_executor_configuration_takes_the_palette_of_its_environment() {
        assertThat(new BuildExecutor.Configuration().out())
                .as("a configuration built without an environment prints nothing, so it needs no colors either")
                .isNull();
        assertThat(new BuildExecutor.Configuration().palette()).isEqualTo(Palette.NONE);
        assertThat(BuildExecutor.Configuration.ofEnvironment(Environment.NONE).palette()).isEqualTo(Palette.ANSI);
        assertThat(BuildExecutor.Configuration.ofEnvironment(new Environment(Map.of("palette.colors", "none"))).palette())
                .isEqualTo(Palette.NONE);
    }
}
