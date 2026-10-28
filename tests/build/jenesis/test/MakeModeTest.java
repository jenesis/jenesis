package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.Make;

import static org.assertj.core.api.Assertions.assertThat;

public class MakeModeTest {

    @TempDir
    private Path root;

    @Test
    public void hands_a_build_to_the_daemon_where_nothing_names_a_mode() {
        assertThat(new Make("build.jenesis.Project", settings()).mode()).isEqualTo(Make.Mode.DAEMON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fork", "aot"})
    public void builds_without_a_daemon_where_the_tools_are_not_to_run_in_one(String process) {
        assertThat(new Make("build.jenesis.Project", settings("process.mode", process)).mode())
                .as("a process mode other than the daemon's says the build is not to leave JVMs running")
                .isEqualTo(Make.Mode.COMPILED);
    }

    @Test
    public void builds_without_a_daemon_where_the_build_runs_in_a_container() {
        assertThat(new Make("build.jenesis.Project", settings("project.docker", "true")).mode())
                .as("a daemon cannot run a build that replaces its process with a container")
                .isEqualTo(Make.Mode.COMPILED);
    }

    @Test
    public void publishes_a_release_from_a_jvm_no_earlier_build_ran_in() {
        assertThat(new Make("build.jenesis.Project", settings("release.publish", "true")).mode())
                .as("the credentials and keys of a release are never handed to a JVM another build ran in")
                .isEqualTo(Make.Mode.COMPILED);
    }

    @Test
    public void keeps_the_daemon_where_the_tools_run_in_it() {
        assertThat(new Make("build.jenesis.Project", settings("process.mode", "daemon")).mode())
                .isEqualTo(Make.Mode.DAEMON);
    }

    @Test
    public void runs_in_the_mode_named_whatever_else_is_set() {
        assertThat(new Make("build.jenesis.Project", settings("make.mode", "daemon", "release.publish", "true"))
                .mode())
                .isEqualTo(Make.Mode.DAEMON);
        assertThat(new Make("build.jenesis.Project", settings("make.mode", "source")).mode())
                .isEqualTo(Make.Mode.SOURCE);
    }

    @Test
    public void reads_the_mode_a_file_of_the_project_names() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.make.mode=compiled\n");
        assertThat(new Make("build.jenesis.Project", settings()).mode()).isEqualTo(Make.Mode.COMPILED);
    }

    private Map<String, String> settings(String... pairs) {
        Map<String, String> settings = new HashMap<>();
        settings.put("make.root", root.toString());
        settings.put("make.global", "");
        for (int index = 0; index < pairs.length; index += 2) {
            settings.put(pairs[index], pairs[index + 1]);
        }
        return settings;
    }
}
