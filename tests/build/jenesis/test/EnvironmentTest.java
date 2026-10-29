package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Make.Setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EnvironmentTest {

    @Test
    public void a_flag_is_the_default_when_it_is_not_set() {
        assertThat(Environment.NONE.flag("sample.flag", Setting.PROJECT)).isFalse();
        assertThat(Environment.NONE.flag("sample.flag", true, Setting.PROJECT)).isTrue();
    }

    @Test
    public void a_flag_named_with_no_value_is_true() {
        assertThat(new Environment(Map.of("sample.flag", "")).flag("sample.flag", Setting.PROJECT))
                .as("naming a flag on the command line and nothing else is how it is switched on")
                .isTrue();
    }

    @Test
    public void a_flag_set_to_false_is_false() {
        Environment environment = new Environment(Map.of("sample.flag", "false"));
        assertThat(environment.flag("sample.flag", Setting.PROJECT))
                .as("=false once switched a presence-read flag on, which is the whole reason"
                        + " every boolean is read the same way now")
                .isFalse();
        assertThat(environment.flag("sample.flag", true, Setting.PROJECT)).isFalse();
    }

    @Test
    public void a_flag_set_to_anything_else_is_refused() {
        assertThatThrownBy(() -> new Environment(Map.of("sample.flag", "yes")).flag("sample.flag", Setting.PROJECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.flag: 'yes'"
                        + " (expected true, false, or the setting named with no value at all)");
    }

    @Test
    public void a_relative_path_is_resolved_against_the_root_of_the_environment(@TempDir Path root) {
        Environment environment = new Environment(Map.of("sample.folder", " folder/../repository ")).root(root);
        assertThat(environment.path("sample.folder", Setting.PROJECT))
                .as("a setting no file and no command line supplied names a folder of the project")
                .isEqualTo(root.resolve("repository"));
        assertThat(environment.path("sample.missing", "fallback", Setting.PROJECT)).isEqualTo(root.resolve("fallback"));
        assertThat(environment.path("sample.missing", Setting.PROJECT)).isNull();
    }

    @Test
    public void an_absolute_path_is_used_as_it_is(@TempDir Path root, @TempDir Path elsewhere) {
        Environment environment = new Environment(Map.of("sample.folder", elsewhere.toString())).root(root);
        assertThat(environment.path("sample.folder", Setting.PROJECT)).isEqualTo(elsewhere);
    }

    @Test
    public void a_blank_path_reads_as_absent(@TempDir Path root) {
        assertThat(new Environment(Map.of("sample.folder", " ")).root(root).path("sample.folder",
                "default",
                Setting.PROJECT))
                .isEqualTo(root.resolve("default"));
    }

    @Test
    public void a_machine_setting_is_refused_where_a_project_s_file_supplied_it() {
        Environment environment = new Environment(Map.of("sample.uri", "https://example.com",
                "make.provided", "sample.uri"));
        assertThatThrownBy(() -> environment.value("sample.uri", Setting.MACHINE))
                .as("the reader names what kind of setting it reads, and a machine's setting is no project's to make")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("jenesis.sample.uri cannot be set in a file the project provides");
        assertThat(environment.value("sample.uri", Setting.PROJECT)).isEqualTo("https://example.com");
    }

    @Test
    public void a_machine_setting_is_read_where_the_user_s_own_file_supplied_it() {
        Environment environment = new Environment(Map.of("sample.uri", "https://example.com",
                "make.personal", "sample.uri"));
        assertThat(environment.value("sample.uri", Setting.MACHINE)).isEqualTo("https://example.com");
    }

    @Test
    public void a_command_setting_is_refused_where_any_file_supplied_it() {
        for (String list : List.of("make.provided", "make.personal")) {
            Environment environment = new Environment(Map.of("sample.publish", "true", list, "sample.publish"));
            assertThatThrownBy(() -> environment.flag("sample.publish", Setting.COMMAND))
                    .as("what one run is asked to do is named on its command line, never in a file")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("jenesis.sample.publish cannot be set in a file");
        }
        assertThat(new Environment(Map.of("sample.publish", "true")).flag("sample.publish", Setting.COMMAND)).isTrue();
    }

    @Test
    public void a_value_is_never_read_as_confined() {
        assertThatThrownBy(() -> Environment.NONE.value("sample.folder", Setting.CONFINED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only a location can be confined to the project");
    }

    @Test
    public void a_confined_path_a_project_s_file_supplied_stays_inside_the_project(@TempDir Path root) {
        Environment inside = new Environment(Map.of("sample.folder", "out", "make.provided", "sample.folder")).root(root);
        assertThat(inside.path("sample.folder", Setting.CONFINED)).isEqualTo(root.resolve("out"));
        Environment outside = new Environment(Map.of("sample.folder", "../out", "make.provided", "sample.folder"))
                .root(root);
        assertThatThrownBy(() -> outside.path("sample.folder", Setting.CONFINED))
                .as("a project names only folders of its own for the build to write to and delete")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("jenesis.sample.folder cannot name '../out' in a file the project provides");
        assertThat(outside.path("sample.folder", Setting.PROJECT))
                .as("a folder the build only reads may lie beside the project")
                .isEqualTo(root.resolveSibling("out"));
    }

    @Test
    public void a_path_is_resolved_against_where_it_was_written(@TempDir Path root,
                                                               @TempDir Path home,
                                                               @TempDir Path directory) {
        Environment environment = new Environment(Map.of("sample.cache", "cache",
                "sample.folder", "folder",
                "sample.typed", "typed",
                "sample.given", "given",
                "make.personal", "sample.cache,sample.folder",
                "make.home", home.toString(),
                "make.ambient", "sample.typed",
                "make.directory", directory.toString())).root(root);
        assertThat(environment.path("sample.cache", Setting.MACHINE))
                .as("what a machine shares between projects is named relative to your own folder of settings")
                .isEqualTo(home.resolve("cache"));
        assertThat(environment.path("sample.folder", Setting.CONFINED))
                .as("a folder of the project written in your own file names that folder of every project")
                .isEqualTo(root.resolve("folder"));
        assertThat(environment.path("sample.typed", Setting.CONFINED))
                .as("a path typed on the command line names a folder below the one the command runs in")
                .isEqualTo(directory.resolve("typed"));
        assertThat(environment.path("sample.given", Setting.CONFINED))
                .as("a path in a map a program hands the build names a folder of the project")
                .isEqualTo(root.resolve("given"));
    }

    @Test
    public void a_part_of_a_value_is_resolved_as_its_setting_is(@TempDir Path root, @TempDir Path directory) {
        Environment environment = new Environment(Map.of("sample.files", "a.txt:b.txt",
                "make.ambient", "sample.files",
                "make.directory", directory.toString())).root(root);
        assertThat(environment.located("sample.files", "a.txt", Setting.PROJECT)).isEqualTo(directory.resolve("a.txt"));
    }

    @Test
    public void a_path_is_not_a_uri() {
        assertThatThrownBy(() -> new Environment(Map.of("sample.folder", "file://cache")).path("sample.folder",
                Setting.PROJECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Malformed value for jenesis.sample.folder: 'file://cache' (expected a path, not a URI)");
    }

    @Test
    public void tells_a_setting_a_project_s_file_supplied() {
        Environment environment = new Environment(Map.of("sample.uri", "x", "make.provided", "sample.uri"));
        assertThat(environment.provided("sample.uri")).isTrue();
        assertThat(environment.provided("sample.other")).isFalse();
    }

    @Test
    public void a_flag_ignores_case_and_surrounding_space() {
        assertThat(new Environment(Map.of("sample.flag", " TRUE ")).flag("sample.flag", Setting.PROJECT)).isTrue();
    }

    @Test
    public void a_flag_that_is_not_set_can_be_told_from_one_set_to_false() {
        assertThat(Environment.NONE.flagOrNull("sample.flag", Setting.PROJECT)).isNull();
        assertThat(new Environment(Map.of("sample.flag", "false")).flagOrNull("sample.flag",
                Setting.PROJECT)).isFalse();
    }

    @Test
    public void a_flag_read_for_its_absence_reads_a_value_like_every_other() {
        assertThat(new Environment(Map.of("sample.flag", "")).flagOrNull("sample.flag", Setting.PROJECT)).isTrue();
        assertThatThrownBy(() -> new Environment(Map.of("sample.flag", "yes")).flagOrNull("sample.flag",
                Setting.PROJECT))
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
        assertThat(environment.value("sample.value", Setting.PROJECT)).isEqualTo("text");
        assertThat(environment.value("sample.absent", "fallback", Setting.PROJECT)).isEqualTo("fallback");
        assertThat(environment.flag("sample.flag", Setting.PROJECT)).isTrue();
        assertThat(environment.flag("sample.absent", true, Setting.PROJECT)).isTrue();
        assertThat(environment.number("sample.count", 0, Setting.PROJECT)).isEqualTo(4);
        assertThat(environment.number("sample.absent", 7, Setting.PROJECT)).isEqualTo(7);
        assertThat(environment.entries("sample.entries", Setting.PROJECT)).containsExactly("a", "b");
        assertThat(environment.words("sample.words", Setting.PROJECT)).containsExactly("-a", "-b");
    }

    @Test
    public void tells_a_raw_value_from_a_trimmed_one() {
        Map<String, String> settings = Map.of("sample.empty", "");
        Environment environment = new Environment(settings);
        assertThat(environment.getProperty("sample.empty", Setting.PROJECT))
                .as("a setting named with no value is empty rather than absent,"
                        + " which is what tells a tag deliberately cleared from one never given")
                .isEmpty();
        assertThat(environment.value("sample.empty", Setting.PROJECT)).isNull();
        assertThat(environment.getProperty("sample.absent", "fallback", Setting.PROJECT)).isEqualTo("fallback");
    }

    @Test
    public void refuses_a_number_that_is_not_one() {
        Environment environment = new Environment(Map.of("sample.count", "many"));
        assertThatThrownBy(() -> environment.number("sample.count", 0, Setting.PROJECT))
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

    @Test
    public void reads_a_value_naming_a_variable_as_the_setting_it_names() {
        Environment environment = new Environment(Map.of("variable.level", "strict"));
        assertThat(environment.variable("The file sets level", "@level")).isEqualTo("strict");
        assertThat(environment.variable("The file sets level", "@other/lenient"))
                .as("a variable that is not set takes the default it names")
                .isEqualTo("lenient");
        assertThat(environment.variable("The file sets level", "@@level"))
                .as("@@ is a literal @")
                .isEqualTo("@level");
        assertThat(environment.variable("The file sets level", "plain")).isEqualTo("plain");
    }

    @Test
    public void refuses_a_value_naming_a_variable_that_is_not_set() {
        assertThatThrownBy(() -> Environment.NONE.variable("The file sets level", "@level"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The file sets level to @level, but jenesis.variable.level is not set - set it, or give a"
                        + " default as @level/<default>");
    }
}
