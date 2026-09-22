package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static build.jenesis.SequencedProperties.SYSTEM;

public class SequencedPropertiesTest {

    @Test
    public void stores_properties_in_the_order_they_were_set() throws IOException {
        SequencedProperties original = new SequencedProperties();
        for (String key : List.of("zebra", "alpha", "middle", "beta")) {
            original.setProperty(key, "1");
        }
        StringWriter writer = new StringWriter();
        original.store(writer, null);
        assertThat(writer.toString().lines().map(line -> line.substring(0, line.indexOf('='))))
                .as("Properties sorts what it stores and iterates its own table by hash,"
                        + " so only the sequenced delegate reaching both keeps a written file as written")
                .containsExactly("zebra", "alpha", "middle", "beta");
        assertThat(original.stringPropertyNames()).containsExactly("zebra", "alpha", "middle", "beta");
    }

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
        properties.setProperty("on", " TRUE ");
        properties.setProperty("off", "false");
        properties.setProperty("blank", "");
        assertThat(properties.flag("on")).isTrue();
        assertThat(properties.flag("off")).isFalse();
        assertThat(properties.flag("absent")).isFalse();
        assertThat(properties.flag("absent", true)).isTrue();
        assertThat(properties.flag("blank", false))
                .as("a file names a flag with no value exactly as a command line does, and means the same by it")
                .isTrue();
        assertThat(properties.flag("off", true)).isFalse();
    }

    @Test
    public void refuses_a_flag_in_a_file_that_is_neither_true_nor_false() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("maybe", "yes");
        assertThatThrownBy(() -> properties.flag("maybe"))
                .as("a file read its flags leniently while a system property refused them,"
                        + " so the same word disabled a tool in one place and failed the build in the other")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for maybe: 'yes'"
                        + " (expected true, false, or the setting named with no value at all)");
    }

    @Test
    public void tells_a_flag_that_is_not_set_in_a_file_from_one_set_to_false() {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("off", "false");
        assertThat(properties.flagOrNull("absent")).isNull();
        assertThat(properties.flagOrNull("off")).isFalse();
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
        assertThat(SequencedProperties.flag(SYSTEM, "test.sample.flag")).isFalse();
        assertThat(SequencedProperties.flag(SYSTEM, "test.sample.flag", true)).isTrue();
    }

    @Test
    public void a_system_flag_named_with_no_value_is_true() {
        System.setProperty("jenesis.test.sample.flag", "");
        try {
            assertThat(SequencedProperties.flag(SYSTEM, "test.sample.flag"))
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
            assertThat(SequencedProperties.flag(SYSTEM, "test.sample.flag"))
                    .as("=false once switched a presence-read flag on, which is the whole reason"
                            + " every boolean is read the same way now")
                    .isFalse();
            assertThat(SequencedProperties.flag(SYSTEM, "test.sample.flag", true)).isFalse();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_set_to_anything_else_is_refused() {
        System.setProperty("jenesis.test.sample.flag", "yes");
        try {
            assertThatThrownBy(() -> SequencedProperties.flag(SYSTEM, "test.sample.flag"))
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
            assertThat(SequencedProperties.flag(SYSTEM, "test.sample.flag")).isTrue();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_that_is_not_set_can_be_told_from_one_set_to_false() {
        System.clearProperty("jenesis.test.sample.flag");
        assertThat(SequencedProperties.flagOrNull(SYSTEM, "test.sample.flag")).isNull();
        System.setProperty("jenesis.test.sample.flag", "false");
        try {
            assertThat(SequencedProperties.flagOrNull(SYSTEM, "test.sample.flag")).isFalse();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
    }

    @Test
    public void a_system_flag_read_for_its_absence_reads_a_value_like_every_other() {
        System.setProperty("jenesis.test.sample.flag", "");
        try {
            assertThat(SequencedProperties.flagOrNull(SYSTEM, "test.sample.flag")).isTrue();
        } finally {
            System.clearProperty("jenesis.test.sample.flag");
        }
        System.setProperty("jenesis.test.sample.flag", "yes");
        try {
            assertThatThrownBy(() -> SequencedProperties.flagOrNull(SYSTEM, "test.sample.flag"))
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
        Function<String, String> keys = settings::get;
        assertThat(SequencedProperties.value(keys, "sample.value")).isEqualTo("text");
        assertThat(SequencedProperties.value(keys, "sample.absent", "fallback")).isEqualTo("fallback");
        assertThat(SequencedProperties.flag(keys, "sample.flag")).isTrue();
        assertThat(SequencedProperties.flag(keys, "sample.absent", true)).isTrue();
        assertThat(SequencedProperties.number(keys, "sample.count", 0)).isEqualTo(4);
        assertThat(SequencedProperties.number(keys, "sample.absent", 7)).isEqualTo(7);
        assertThat(SequencedProperties.entries(keys, "sample.entries")).containsExactly("a", "b");
        assertThat(SequencedProperties.words(keys, "sample.words")).containsExactly("-a", "-b");
    }

    @Test
    public void tells_a_raw_value_from_a_trimmed_one() {
        Map<String, String> settings = Map.of("sample.empty", "");
        Function<String, String> keys = settings::get;
        assertThat(SequencedProperties.getProperty(keys, "sample.empty"))
                .as("a setting named with no value is empty rather than absent,"
                        + " which is what tells a tag deliberately cleared from one never given")
                .isEmpty();
        assertThat(SequencedProperties.value(keys, "sample.empty")).isNull();
        assertThat(SequencedProperties.getProperty(keys, "sample.absent", "fallback")).isEqualTo("fallback");
    }

    @Test
    public void refuses_a_number_that_is_not_one() {
        Function<String, String> keys = Map.of("sample.count", "many")::get;
        assertThatThrownBy(() -> SequencedProperties.number(keys, "sample.count", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.count: 'many' (expected a whole number)");
    }

    @Test
    public void the_system_provider_prepends_the_namespace_every_setting_shares() {
        System.setProperty("jenesis.test.sample.value", "text");
        try {
            assertThat(SYSTEM.apply("test.sample.value")).isEqualTo("text");
            assertThat(SYSTEM.apply("jenesis.test.sample.value"))
                    .as("a key is named without the prefix everywhere, and the provider is the only place it is added")
                    .isNull();
        } finally {
            System.clearProperty("jenesis.test.sample.value");
        }
    }

    @Test
    public void an_argument_file_is_the_arguments_it_holds(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("arguments.txt");
        Files.writeString(file, """
                # what this run is
                -Djenesis.project.version=1.0.0
                build "+a module" 'and another'
                trailing # and what it is not
                """);
        assertThat(SequencedProperties.arguments("@" + file, "last"))
                .as("a file of arguments reads as the command line it stands for")
                .containsExactly("-Djenesis.project.version=1.0.0",
                        "build",
                        "+a module",
                        "and another",
                        "trailing",
                        "last");
    }

    @Test
    public void an_argument_file_escapes_within_a_quote_and_nowhere_else(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("arguments.txt");
        Files.writeString(file, "\"a\\tb\" \"quote\\\"inside\" c\\d");
        assertThat(SequencedProperties.arguments("@" + file))
                .as("a backslash escapes inside a quote, as it does for the JDK's own tools, and is a"
                        + " character of its own outside one")
                .containsExactly("a\tb", "quote\"inside", "c\\d");
    }

    @Test
    public void an_argument_file_is_not_expanded_again(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("arguments.txt");
        Files.writeString(file, "@nested.txt");
        assertThat(SequencedProperties.arguments("@" + file))
                .as("an @ within a file is an argument, not another file, as the JDK reads it")
                .containsExactly("@nested.txt");
    }

    @Test
    public void an_argument_starting_with_an_at_is_written_twice() throws IOException {
        assertThat(SequencedProperties.arguments("@@literal", "plain"))
                .containsExactly("@literal", "plain");
    }

    @Test
    public void an_argument_file_that_is_not_there_names_itself(@TempDir Path directory) {
        Path file = directory.resolve("missing.txt");
        assertThatThrownBy(() -> SequencedProperties.arguments("@" + file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No argument file at " + file);
    }

    @Test
    public void an_argument_file_refuses_a_quote_that_never_closes(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("arguments.txt");
        Files.writeString(file, "\"never closed");
        assertThatThrownBy(() -> SequencedProperties.arguments("@" + file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unterminated \" in the argument file " + file);
    }
}
