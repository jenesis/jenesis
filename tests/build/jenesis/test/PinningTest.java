package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PinningTest {

    @AfterEach
    public void clear() {
        System.clearProperty("jenesis.dependency.pin");
    }

    @Test
    public void from_property_is_null_when_unset() {
        System.clearProperty("jenesis.dependency.pin");
        assertThat(Pinning.fromProperty()).isNull();
    }

    @Test
    public void from_property_parses_case_insensitively() {
        System.setProperty("jenesis.dependency.pin", "strict");
        assertThat(Pinning.fromProperty()).isEqualTo(Pinning.STRICT);
        System.setProperty("jenesis.dependency.pin", "VERSIONS");
        assertThat(Pinning.fromProperty()).isEqualTo(Pinning.VERSIONS);
    }

    @Test
    public void from_property_rejects_an_unknown_value() {
        System.setProperty("jenesis.dependency.pin", "bogus");
        assertThatThrownBy(Pinning::fromProperty)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.dependency.pin 'bogus'");
    }
}
