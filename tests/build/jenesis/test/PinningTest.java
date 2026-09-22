package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static build.jenesis.SequencedProperties.SYSTEM;

public class PinningTest {

    @AfterEach
    public void clear() {
        System.clearProperty("jenesis.dependency.pin");
        System.clearProperty("jenesis.pin.concurrency");
    }

    @Test
    public void every_pin_writer_shares_one_ceiling() {
        System.setProperty("jenesis.pin.concurrency", "3");
        assertThat(Pinning.permits(SYSTEM))
                .as("a pom writer and a module-info writer bound one fan-out between them, not one each")
                .isSameAs(Pinning.permits(SYSTEM));
        assertThat(Pinning.permits(SYSTEM).availablePermits()).isEqualTo(3);
    }

    @Test
    public void an_unbounded_fan_out_holds_no_permit_at_all() {
        System.setProperty("jenesis.pin.concurrency", "0");
        assertThat(Pinning.permits(SYSTEM)).isNull();
    }

    @Test
    public void refuses_a_ceiling_below_nothing() {
        System.setProperty("jenesis.pin.concurrency", "-1");
        assertThatThrownBy(() -> Pinning.permits(SYSTEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pin concurrency must not be negative: -1");
    }

    @Test
    public void reading_the_pin_setting_is_null_when_unset() {
        System.clearProperty("jenesis.dependency.pin");
        assertThat(Pinning.ofKeys(SYSTEM)).isNull();
    }

    @Test
    public void reading_the_pin_setting_parses_case_insensitively() {
        System.setProperty("jenesis.dependency.pin", "strict");
        assertThat(Pinning.ofKeys(SYSTEM)).isEqualTo(Pinning.STRICT);
        System.setProperty("jenesis.dependency.pin", "VERSIONS");
        assertThat(Pinning.ofKeys(SYSTEM)).isEqualTo(Pinning.VERSIONS);
    }

    @Test
    public void reading_the_pin_setting_rejects_an_unknown_value() {
        System.setProperty("jenesis.dependency.pin", "bogus");
        assertThatThrownBy(() -> Pinning.ofKeys(SYSTEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.dependency.pin 'bogus'");
    }
}
