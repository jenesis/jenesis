package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VerificationTest {

    @Test
    public void reads_the_signature_setting_from_the_provider_it_is_given() {
        System.setProperty("jenesis.dependency.signature", "strict");
        try {
            assertThat(Verification.ofKeys(Map.of("dependency.signature", "declared")::get))
                    .as("what a build verifies is decided by the provider it was handed, not by the JVM"
                            + " the build happens to run in")
                    .isEqualTo(Verification.DECLARED);
            assertThat(Verification.ofKeys(SequencedProperties.NONE)).isEqualTo(Verification.NONE);
        } finally {
            System.clearProperty("jenesis.dependency.signature");
        }
    }

    @Test
    public void reading_the_signature_setting_is_none_when_unset() {
        assertThat(Verification.ofKeys(SequencedProperties.NONE))
                .as("verification is opt-in, so a setting nobody names checks nothing")
                .isEqualTo(Verification.NONE);
    }

    @Test
    public void reading_the_signature_setting_parses_case_insensitively() {
        assertThat(Verification.ofKeys(Map.of("dependency.signature", "declared")::get))
                .isEqualTo(Verification.DECLARED);
        assertThat(Verification.ofKeys(Map.of("dependency.signature", "STRICT")::get))
                .isEqualTo(Verification.STRICT);
    }

    @Test
    public void reading_the_signature_setting_rejects_an_unknown_value() {
        assertThatThrownBy(() -> Verification.ofKeys(Map.of("dependency.signature", "bogus")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.dependency.signature 'bogus'");
    }
}
