package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class VerificationTest {

    @Test
    public void reads_the_signature_setting_from_the_provider_it_is_given() {
        System.setProperty("jenesis.dependency.signature", "strict");
        try {
            assertThat(Verification.ofEnvironment(new Environment(Map.of("dependency.signature", "declared"))))
                    .as("what a build verifies is decided by the provider it was handed, not by the JVM"
                            + " the build happens to run in")
                    .isEqualTo(Verification.DECLARED);
            assertThat(Verification.ofEnvironment(Environment.NONE)).isEqualTo(Verification.NONE);
        } finally {
            System.clearProperty("jenesis.dependency.signature");
        }
    }

    @Test
    public void reading_the_signature_setting_is_none_when_unset() {
        assertThat(Verification.ofEnvironment(Environment.NONE))
                .as("verification is opt-in, so a setting nobody names checks nothing")
                .isEqualTo(Verification.NONE);
    }

    @Test
    public void reading_the_signature_setting_parses_case_insensitively() {
        assertThat(Verification.ofEnvironment(new Environment(Map.of("dependency.signature", "declared"))))
                .isEqualTo(Verification.DECLARED);
        assertThat(Verification.ofEnvironment(new Environment(Map.of("dependency.signature", "STRICT"))))
                .isEqualTo(Verification.STRICT);
    }

    @Test
    public void reading_the_signature_setting_rejects_an_unknown_value() {
        assertThatThrownBy(() -> Verification.ofEnvironment(new Environment(Map.of("dependency.signature", "bogus"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.dependency.signature 'bogus'");
    }
}
