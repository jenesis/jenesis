package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;
import build.jenesis.SequencedProperties;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PinningTest {

    @Test
    public void every_pin_writer_shares_one_ceiling() {
        Map<String, String> keys = Map.of("pin.concurrency", "3");
        assertThat(Pinning.permits(new Environment(keys)))
                .as("a pom writer and a module-info writer bound one fan-out between them, not one each")
                .isSameAs(Pinning.permits(new Environment(keys)));
        assertThat(Pinning.permits(new Environment(keys)).availablePermits()).isEqualTo(3);
    }

    @Test
    public void an_unbounded_fan_out_holds_no_permit_at_all() {
        assertThat(Pinning.permits(new Environment(Map.of("pin.concurrency", "0")))).isNull();
    }

    @Test
    public void refuses_a_ceiling_below_nothing() {
        assertThatThrownBy(() -> Pinning.permits(new Environment(Map.of("pin.concurrency", "-1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pin concurrency must not be negative: -1");
    }

    @Test
    public void reading_the_pin_setting_is_null_when_unset() {
        assertThat(Pinning.ofEnvironment(Environment.NONE)).isNull();
    }

    @Test
    public void reading_the_pin_setting_parses_case_insensitively() {
        assertThat(Pinning.ofEnvironment(new Environment(Map.of("dependency.pin", "strict")))).isEqualTo(Pinning.STRICT);
        assertThat(Pinning.ofEnvironment(new Environment(Map.of("dependency.pin", "VERSIONS")))).isEqualTo(Pinning.VERSIONS);
    }

    @Test
    public void reading_the_pin_setting_rejects_an_unknown_value() {
        assertThatThrownBy(() -> Pinning.ofEnvironment(new Environment(Map.of("dependency.pin", "bogus"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.dependency.pin 'bogus'");
    }
}
