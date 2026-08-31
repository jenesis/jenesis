package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;

import static org.assertj.core.api.Assertions.assertThat;

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
}