package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SequencedPropertiesTest {

    @Test
    public void can_suppress_comments_and_subsequent_newline() throws IOException {
        SequencedProperties original = new SequencedProperties();
        for (char character = 'z'; character >= 'a'; character--) {
            original.setProperty("key-" + character, "value-" + character);
        }
        StringWriter writer = new StringWriter();
        original.store(writer, null);
        assertThat(writer.toString()).isEqualTo(IntStream.iterate('z',
                        character -> character >= 'a',
                        character -> character - 1)
                .mapToObj(character -> "key-" + (char) character + "=value-" + (char) character)
                .collect(Collectors.joining("\n", "", "\n")));
        SequencedProperties copy = new SequencedProperties();
        copy.load(new StringReader(writer.toString()));
        assertThat(copy.stringPropertyNames()).containsExactlyElementsOf(original.stringPropertyNames());
    }

    @Test
    public void reads_a_trimmed_value_and_treats_a_blank_one_as_absent() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("named", "  value  ");
        properties.setProperty("blank", "   ");
        assertThat(properties.value("named")).isEqualTo("value");
        assertThat(properties.value("blank")).as("a blank value reads as an absent one").isNull();
        assertThat(properties.value("absent")).isNull();
        assertThat(properties.value("blank", "fallback")).isEqualTo("fallback");
        assertThat(properties.value("named", "fallback")).isEqualTo("value");
    }

    @Test
    public void reads_a_flag_with_its_default_when_absent() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("on", " true ");
        properties.setProperty("off", "false");
        properties.setProperty("blank", "");
        assertThat(properties.flag("on")).isTrue();
        assertThat(properties.flag("off")).isFalse();
        assertThat(properties.flag("absent")).isFalse();
        assertThat(properties.flag("blank", true)).as("a blank value falls back to the default").isTrue();
        assertThat(properties.flag("off", true)).isFalse();
    }

    @Test
    public void reads_comma_separated_entries_without_blanks() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("listed", " one , two ,, three ");
        properties.setProperty("separators", ",,,");
        properties.setProperty("blank", "  ");
        assertThat(properties.entries("listed")).containsExactly("one", "two", "three");
        assertThat(properties.entries("separators"))
                .as("a value that lists nothing is empty, not absent")
                .isEmpty();
        assertThat(properties.entries("blank")).isNull();
        assertThat(properties.entries("absent")).isNull();
    }

    @Test
    public void reads_a_command_line_as_whitespace_separated_words() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("arguments", "  --library native   --additional-properties useJakartaEe=true ");
        properties.setProperty("blank", "   ");
        assertThat(properties.words("arguments"))
                .containsExactly("--library", "native", "--additional-properties", "useJakartaEe=true");
        assertThat(properties.words("blank"))
                .as("an unconfigured command line adds no arguments rather than an empty one")
                .isEmpty();
        assertThat(properties.words("absent")).isEmpty();
    }

    @Test
    public void can_traverse_string_properties_in_order() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("k2", "v2");
        properties.setProperty("k1", "v1");
        properties.put("k3", new Object());
        properties.put(new Object(), "v4");
        SequencedMap<String, String> traversed = new LinkedHashMap<>();
        properties.forEachProperty(traversed::put);
        assertThat(traversed)
                .as("only string typed entries are traversed, and in insertion order")
                .containsExactly(Map.entry("k2", "v2"), Map.entry("k1", "v1"));
    }

    @Test
    public void can_suppress_header_comment() throws IOException {
        SequencedProperties original = new SequencedProperties();
        original.setProperty("k1", "v1");
        StringWriter writer = new StringWriter();
        original.store(writer, "header");
        assertThat(writer.toString()).isEqualTo("k1=v1\n");
    }

    @Test
    public void a_system_flag_is_the_default_when_it_is_not_set() {
        System.clearProperty("jenesis.test.sample.flag");
        assertThat(SequencedProperties.systemFlag("jenesis.test.sample.flag")).isFalse();
        assertThat(SequencedProperties.systemFlag("jenesis.test.sample.flag", true)).isTrue();
    }

    @Test
    public void a_system_flag_named_with_no_value_is_true() {
        System.setProperty("jenesis.test.sample.flag", "");
        try {
            assertThat(SequencedProperties.systemFlag("jenesis.test.sample.flag"))
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
            assertThat(SequencedProperties.systemFlag("jenesis.test.sample.flag"))
                    .as("=false once switched a presence-read flag on, which is the whole reason"
                            + " every boolean is read the same way now")
                    .isFalse();
            assertThat(SequencedProperties.systemFlag("jenesis.test.sample.flag", true)).isFalse();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_set_to_anything_else_is_refused() {
        System.setProperty("jenesis.test.sample.flag", "yes");
        try {
            assertThatThrownBy(() -> SequencedProperties.systemFlag("jenesis.test.sample.flag"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Malformed value for jenesis.test.sample.flag: 'yes'"
                            + " (expected true, false, or the property named with no value at all)");
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_ignores_case_and_surrounding_space() {
        System.setProperty("jenesis.test.sample.flag", " TRUE ");
        try {
            assertThat(SequencedProperties.systemFlag("jenesis.test.sample.flag")).isTrue();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }
}