package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildExecutorModule;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildExecutorModuleTest {

    static final List<String> NAMES = List.of(
            "plain",
            "with-dash",
            "with.dot",
            "with_underscore",
            "with space",
            "with/slash",
            "with\\backslash",
            "with:colon",
            "with*asterisk",
            "with?question",
            "with<less",
            "with>greater",
            "with\"quote",
            "with|pipe",
            "with+plus",
            "with%percent",
            "with#hash",
            "with&ampersand",
            "with(paren)",
            "with[bracket]",
            "with{brace}",
            "with;semicolon",
            "with'apostrophe",
            "with,comma",
            "with=equals",
            "with@at",
            "with!bang",
            "with$dollar",
            "with~tilde",
            "with`backtick",
            "with^caret",
            "ümläüt",
            "日本語",
            "emoji-rocket");

    @TempDir
    Path temp;

    @ParameterizedTest
    @FieldSource("NAMES")
    public void encoded_name_only_uses_filesystem_safe_characters(String name) {
        String encoded = BuildExecutorModule.encode(name);
        assertThat(encoded).matches("[a-zA-Z0-9._%-]+");
    }

    @ParameterizedTest
    @FieldSource("NAMES")
    public void encoded_name_can_be_used_as_directory(String name) throws IOException {
        String encoded = BuildExecutorModule.encode(name);
        Path directory = Files.createDirectory(temp.resolve(encoded));
        assertThat(directory).isDirectory();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plain",
            "with-dash",
            "with.dot",
            "with_underscore",
            "with space",
            "with/slash",
            "with*asterisk",
            "ümläüt"
    })
    public void encoded_name_round_trips_through_temp_directory_factory(String name) throws IOException {
        String encoded = BuildExecutorModule.encode(name);
        Path directory = Files.createTempDirectory(temp, encoded);
        assertThat(directory).isDirectory();
        assertThat(directory.getFileName().toString()).startsWith(encoded);
    }

    @ParameterizedTest
    @FieldSource("NAMES")
    public void decoded_name_restores_the_encoded_name(String name) {
        assertThat(BuildExecutorModule.decode(BuildExecutorModule.encode(name))).isEqualTo(name);
    }

    @Test
    public void decodes_the_escapes_that_encode_emits_explicitly() {
        assertThat(BuildExecutorModule.decode("%20")).isEqualTo(" ");
        assertThat(BuildExecutorModule.decode("%2A")).isEqualTo("*");
        assertThat(BuildExecutorModule.decode("%25")).isEqualTo("%");
    }

    @Test
    public void encodes_asterisk_explicitly() {
        assertThat(BuildExecutorModule.encode("*")).isEqualTo("%2A");
    }

    @Test
    public void encodes_space_as_percent_twenty_not_plus() {
        assertThat(BuildExecutorModule.encode(" ")).isEqualTo("%20");
    }

    @Test
    public void preserves_unreserved_characters() {
        assertThat(BuildExecutorModule.encode("abcXYZ0123-_.")).isEqualTo("abcXYZ0123-_.");
    }
}
