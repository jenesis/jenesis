package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EnvironmentTest {

    @Test
    public void a_system_flag_is_the_default_when_it_is_not_set() {
        System.clearProperty("jenesis.test.sample.flag");
        assertThat(Environment.SYSTEM.flag("test.sample.flag")).isFalse();
        assertThat(Environment.SYSTEM.flag("test.sample.flag", true)).isTrue();
    }

    @Test
    public void a_system_flag_named_with_no_value_is_true() {
        System.setProperty("jenesis.test.sample.flag", "");
        try {
            assertThat(Environment.SYSTEM.flag("test.sample.flag"))
                    .as("naming a flag on the command line and nothing else is how it is switched on")
                    .isTrue();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_set_to_false_is_false() {
        System.setProperty("jenesis.test.sample.flag", "false");
        try {
            assertThat(Environment.SYSTEM.flag("test.sample.flag"))
                    .as("=false once switched a presence-read flag on, which is the whole reason"
                            + " every boolean is read the same way now")
                    .isFalse();
            assertThat(Environment.SYSTEM.flag("test.sample.flag", true)).isFalse();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_set_to_anything_else_is_refused() {
        System.setProperty("jenesis.test.sample.flag", "yes");
        try {
            assertThatThrownBy(() -> Environment.SYSTEM.flag("test.sample.flag"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Malformed value for jenesis.test.sample.flag: 'yes'"
                            + " (expected true, false, or the setting named with no value at all)");
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_ignores_case_and_surrounding_space() {
        System.setProperty("jenesis.test.sample.flag", " TRUE ");
        try {
            assertThat(Environment.SYSTEM.flag("test.sample.flag")).isTrue();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_that_is_not_set_can_be_told_from_one_set_to_false() {
        System.clearProperty("jenesis.test.sample.flag");
        assertThat(Environment.SYSTEM.flagOrNull("test.sample.flag")).isNull();
        System.setProperty("jenesis.test.sample.flag", "false");
        try {
            assertThat(Environment.SYSTEM.flagOrNull("test.sample.flag")).isFalse();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_read_for_its_absence_reads_a_value_like_every_other() {
        System.setProperty("jenesis.test.sample.flag", "");
        try {
            assertThat(Environment.SYSTEM.flagOrNull("test.sample.flag")).isTrue();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
        System.setProperty("jenesis.test.sample.flag", "yes");
        try {
            assertThatThrownBy(() -> Environment.SYSTEM.flagOrNull("test.sample.flag"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Malformed value for jenesis.test.sample.flag: 'yes'"
                            + " (expected true, false, or the setting named with no value at all)");
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void reads_every_setting_through_the_provider_it_is_given() {
        Map<String, String> settings = Map.of("sample.value", " text ",
                "sample.flag", "",
                "sample.count", "4",
                "sample.entries", "a, ,b",
                "sample.words", "-a  -b");
        Environment environment = new Environment(settings::get);
        assertThat(environment.value("sample.value")).isEqualTo("text");
        assertThat(environment.value("sample.absent", "fallback")).isEqualTo("fallback");
        assertThat(environment.flag("sample.flag")).isTrue();
        assertThat(environment.flag("sample.absent", true)).isTrue();
        assertThat(environment.number("sample.count", 0)).isEqualTo(4);
        assertThat(environment.number("sample.absent", 7)).isEqualTo(7);
        assertThat(environment.entries("sample.entries")).containsExactly("a", "b");
        assertThat(environment.words("sample.words")).containsExactly("-a", "-b");
    }

    @Test
    public void tells_a_raw_value_from_a_trimmed_one() {
        Map<String, String> settings = Map.of("sample.empty", "");
        Environment environment = new Environment(settings::get);
        assertThat(environment.getProperty("sample.empty"))
                .as("a setting named with no value is empty rather than absent,"
                        + " which is what tells a tag deliberately cleared from one never given")
                .isEmpty();
        assertThat(environment.value("sample.empty")).isNull();
        assertThat(environment.getProperty("sample.absent", "fallback")).isEqualTo("fallback");
    }

    @Test
    public void refuses_a_number_that_is_not_one() {
        Environment environment = new Environment(Map.of("sample.count", "many")::get);
        assertThatThrownBy(() -> environment.number("sample.count", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.count: 'many' (expected a whole number)");
    }

    @Test
    public void the_system_provider_prepends_the_namespace_every_setting_shares() {
        System.setProperty("jenesis.test.sample.value", "text");
        try {
            assertThat(Environment.SYSTEM.getProperty("test.sample.value")).isEqualTo("text");
            assertThat(Environment.SYSTEM.getProperty("jenesis.test.sample.value"))
                    .as("a key is named without the prefix everywhere, and the provider is the only place it is added")
                    .isNull();
        } finally {
            System.clearProperty("jenesis.test.sample.value");
        }
    }
}
