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