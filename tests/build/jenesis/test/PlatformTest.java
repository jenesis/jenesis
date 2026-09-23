package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PlatformTest {

    @Test
    public void normalizes_tokens_to_sorted_lower_case() {
        assertThat(Platform.of(" Windows , AARCH64 ").tokens()).containsExactly("aarch64", "windows");
    }

    @Test
    public void canonical_renders_sorted_comma_form() {
        assertThat(Platform.of("windows, aarch64").canonical()).isEqualTo("aarch64,windows");
    }

    @Test
    public void empty_token_list_is_legal() {
        assertThat(Platform.of(" , ").tokens()).isEmpty();
    }

    @Test
    public void matches_is_subset_based() {
        Platform active = Platform.of("windows,x86_64");
        assertThat(active.matches(Platform.of("windows"))).isTrue();
        assertThat(active.matches(Platform.of("windows,x86_64"))).isTrue();
        assertThat(active.matches(Platform.of("windows,aarch64"))).isFalse();
    }

    @Test
    public void select_returns_fallback_when_no_guard_matches() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>(Map.of("windows", ":win:1.0"));
        assertThat(Platform.of("linux,x86_64").select("key", "1.0", guarded)).isEqualTo("1.0");
    }

    @Test
    public void select_returns_null_without_fallback_or_match() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>(Map.of("windows", ":win:1.0"));
        assertThat(Platform.of("linux,x86_64").select("key", null, guarded)).isNull();
    }

    @Test
    public void select_prefers_matching_guard_over_fallback() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>(Map.of("windows", ":win:1.0"));
        assertThat(Platform.of("windows,x86_64").select("key", "1.0", guarded)).isEqualTo(":win:1.0");
    }

    @Test
    public void select_prefers_more_specific_guard() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>();
        guarded.put("windows", ":win:1.0");
        guarded.put("aarch64,windows", ":win-aarch64:1.0");
        assertThat(Platform.of("windows,aarch64").select("key", "1.0", guarded)).isEqualTo(":win-aarch64:1.0");
    }

    @Test
    public void select_prefers_specific_guard_over_two_less_specific_matches() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>();
        guarded.put("windows", ":win:1.0");
        guarded.put("x86_64", ":x64:1.0");
        guarded.put("windows,x86_64", ":win-x64:1.0");
        assertThat(Platform.of("windows,x86_64").select("key", null, guarded)).isEqualTo(":win-x64:1.0");
    }

    @Test
    public void select_result_is_independent_of_guard_declaration_order() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>();
        guarded.put("windows,x86_64", ":win-x64:1.0");
        guarded.put("windows", ":win:1.0");
        guarded.put("x86_64", ":x64:1.0");
        assertThat(Platform.of("windows,x86_64").select("key", null, guarded)).isEqualTo(":win-x64:1.0");
    }

    @Test
    public void select_rejects_equally_specific_distinct_guards() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>();
        guarded.put("windows", ":win:1.0");
        guarded.put("x86_64", ":x64:1.0");
        assertThatThrownBy(() -> Platform.of("windows,x86_64").select("key", null, guarded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous")
                .hasMessageContaining("key");
    }

    @Test
    public void select_tolerates_equally_specific_guards_with_equal_value() {
        SequencedMap<String, String> guarded = new LinkedHashMap<>();
        guarded.put("windows", ":native:1.0");
        guarded.put("x86_64", ":native:1.0");
        assertThat(Platform.of("windows,x86_64").select("key", null, guarded)).isEqualTo(":native:1.0");
    }

    @Test
    public void equal_token_sets_are_equal_regardless_of_input_order() {
        assertThat(Platform.of("windows,x86_64")).isEqualTo(Platform.of("x86_64, WINDOWS"));
    }

    @Test
    public void detected_platform_has_an_operating_system_and_chipset() {
        assertThat(new Platform().tokens()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void declared_flag_adds_a_token_on_top_of_the_detected_ones() {
        Platform detected = new Platform();
        Platform extended = Platform.ofEnvironment(new Environment(Make.keys(Map.of("jenesis.platform.fips", "true"))));
        assertThat(extended.tokens()).contains("fips");
        assertThat(extended.tokens()).containsAll(detected.tokens());
        assertThat(detected.tokens()).doesNotContain("fips");
    }

    @Test
    public void declared_flag_that_is_neither_true_nor_false_is_refused() {
        Environment environment = new Environment(Make.keys(Map.of("jenesis.platform.fips", "yes")));
        assertThatThrownBy(() -> Platform.ofEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.platform.fips");
    }

    @Test
    public void declared_false_flag_removes_a_detected_token() {
        Platform detected = new Platform();
        String removed = detected.tokens().getFirst();
        SequencedSet<String> expected = new TreeSet<>(detected.tokens());
        expected.remove(removed);
        assertThat(Platform.ofEnvironment(new Environment(Make.keys(Map.of("jenesis.platform." + removed, "false"))))
                .tokens()).containsExactlyElementsOf(expected);
    }

    @Test
    public void declared_flag_in_the_project_file_adds_a_token(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.platform.fips=true\n");
        Environment environment = new Environment(Make.settings(root, Make.keys(Map.of())).keys());
        assertThat(Platform.ofEnvironment(environment).tokens())
                .as("a token set in a file is read like one set on the command line")
                .contains("fips");
    }
}
