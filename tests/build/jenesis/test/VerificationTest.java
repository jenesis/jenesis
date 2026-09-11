package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VerificationTest {

    @AfterEach
    public void clear() {
        System.clearProperty("jenesis.dependency.signature");
    }

    @Test
    public void from_property_is_null_when_unset() {
        System.clearProperty("jenesis.dependency.signature");
        assertThat(Verification.fromProperty()).isNull();
    }

    @Test
    public void from_property_parses_case_insensitively() {
        System.setProperty("jenesis.dependency.signature", "unpinned");
        assertThat(Verification.fromProperty()).isEqualTo(Verification.UNPINNED);
        System.setProperty("jenesis.dependency.signature", "STRICT");
        assertThat(Verification.fromProperty()).isEqualTo(Verification.STRICT);
    }

    @Test
    public void from_property_rejects_an_unknown_value() {
        System.setProperty("jenesis.dependency.signature", "bogus");
        assertThatThrownBy(Verification::fromProperty)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.dependency.signature 'bogus'");
    }
}
