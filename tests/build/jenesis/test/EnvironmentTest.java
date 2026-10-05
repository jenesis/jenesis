package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Make;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EnvironmentTest {

    @Test
    public void a_flag_is_the_default_when_it_is_not_set() {
        assertThat(Environment.NONE.flag("sample.flag")).isFalse();
        assertThat(Environment.NONE.flag("sample.flag", true)).isTrue();
    }

    @Test
    public void a_flag_named_with_no_value_is_true() {
        assertThat(new Environment(Map.of("sample.flag", "")).flag("sample.flag"))
                .as("naming a flag on the command line and nothing else is how it is switched on")
                .isTrue();
    }

    @Test
    public void a_flag_set_to_false_is_false() {
        Environment environment = new Environment(Map.of("sample.flag", "false"));
        assertThat(environment.flag("sample.flag"))
                .as("=false once switched a presence-read flag on, which is the whole reason"
                        + " every boolean is read the same way now")
                .isFalse();
        assertThat(environment.flag("sample.flag", true)).isFalse();
    }

    @Test
    public void a_flag_set_to_anything_else_is_refused() {
        assertThatThrownBy(() -> new Environment(Map.of("sample.flag", "yes")).flag("sample.flag"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.flag: 'yes'"
                        + " (expected true, false, or the setting named with no value at all)");
    }

    @Test
    public void a_flag_ignores_case_and_surrounding_space() {
        assertThat(new Environment(Map.of("sample.flag", " TRUE ")).flag("sample.flag")).isTrue();
    }

    @Test
    public void a_flag_that_is_not_set_can_be_told_from_one_set_to_false() {
        assertThat(Environment.NONE.flagOrNull("sample.flag")).isNull();
        assertThat(new Environment(Map.of("sample.flag", "false")).flagOrNull("sample.flag")).isFalse();
    }

    @Test
    public void a_flag_read_for_its_absence_reads_a_value_like_every_other() {
        assertThat(new Environment(Map.of("sample.flag", "")).flagOrNull("sample.flag")).isTrue();
        assertThatThrownBy(() -> new Environment(Map.of("sample.flag", "yes")).flagOrNull("sample.flag"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.flag: 'yes'"
                        + " (expected true, false, or the setting named with no value at all)");
    }

    @Test
    public void reads_every_setting_through_the_provider_it_is_given() {
        Map<String, String> settings = Map.of("sample.value", " text ",
                "sample.flag", "",
                "sample.count", "4",
                "sample.entries", "a, ,b",
                "sample.words", "-a  -b");
        Environment environment = new Environment(settings);
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
        Environment environment = new Environment(settings);
        assertThat(environment.getProperty("sample.empty"))
                .as("a setting named with no value is empty rather than absent,"
                        + " which is what tells a tag deliberately cleared from one never given")
                .isEmpty();
        assertThat(environment.value("sample.empty")).isNull();
        assertThat(environment.getProperty("sample.absent", "fallback")).isEqualTo("fallback");
    }

    @Test
    public void refuses_a_number_that_is_not_one() {
        Environment environment = new Environment(Map.of("sample.count", "many"));
        assertThatThrownBy(() -> environment.number("sample.count", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.count: 'many' (expected a whole number)");
    }

    @Test
    public void reads_the_jvm_properties_once_when_the_settings_are_taken(@TempDir Path root) throws IOException {
        System.setProperty("jenesis.test.sample.value", "before");
        try {
            Map<String, String> keys = Make.settings(root).keys();
            System.setProperty("jenesis.test.sample.value", "after");
            assertThat(keys.get("test.sample.value"))
                    .as("code that runs inside the build cannot change a setting after it was read")
                    .isEqualTo("before");
        } finally {
            System.clearProperty("jenesis.test.sample.value");
        }
    }

    @Test
    public void names_every_setting_without_the_namespace_every_setting_shares() {
        Map<String, String> keys = Make.keys(Map.of("jenesis.sample.value", "text"));
        assertThat(keys.get("sample.value")).isEqualTo("text");
        assertThat(keys.get("jenesis.sample.value"))
                .as("a key is named without the prefix everywhere, and the provider is the only place it is added")
                .isNull();
    }

    @Test
    public void holds_every_setting_it_is_handed_so_the_settings_in_force_can_be_listed() {
        Map<String, String> keys = Make.keys(Map.of("jenesis.platform.fips", "true", "jenesis.test.sample.value", "text",
                "java.home", "/jdk"));
        assertThat(new Environment(keys).keys())
                .as("a setting whose key is a pattern is found by listing the settings rather than by a derived list")
                .containsOnlyKeys("platform.fips", "test.sample.value");
    }
}
