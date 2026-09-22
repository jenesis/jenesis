package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;

import static org.assertj.core.api.Assertions.assertThat;

public class MakeToolTest {

    @TempDir
    private Path root;

    @Test
    public void is_discovered_by_the_name_the_command_line_uses() {
        assertThat(ToolProvider.findFirst("jenesis-make"))
                .as("a tool the service loader finds is what lets a build run inside another program's JVM,"
                        + " and it answers to the name the command line already uses")
                .isPresent();
        assertThat(ToolProvider.findFirst("jenesis-exec")).isPresent();
        assertThat(ToolProvider.findFirst("jpx")).isPresent();
    }

    @Test
    public void takes_its_settings_out_of_the_command_line_it_is_given() throws IOException {
        Files.writeString(root.resolve("module-info.java"), "module foo {}");
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jenesis-make").orElseThrow().run(new PrintWriter(out), new PrintWriter(err),
                "-Djenesis.make.root=" + root,
                "-Djenesis.project.version=1.2.3",
                "-Djenesis.print.progress=false",
                "configuration");
        assertThat(code).isEqualTo(0);
        assertThat(out.toString())
                .as("the settings a tool is handed are the settings the build it runs is configured by")
                .contains("jenesis.project.version=1.2.3");
    }

    @Test
    public void refuses_a_setting_that_is_not_the_build_tool_s() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jenesis-make").orElseThrow().run(new PrintWriter(out), new PrintWriter(err),
                "-Duser.home=/elsewhere", "configuration");
        assertThat(code).isEqualTo(1);
        assertThat(err.toString())
                .as("a tool configures the build it runs, so a setting of the JVM it runs in is refused")
                .contains("Not a Jenesis setting: -Duser.home=/elsewhere");
    }

    @Test
    public void the_jpx_tool_answers_the_help_of_the_command() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jpx").orElseThrow().run(new PrintWriter(out), new PrintWriter(err), "--help");
        assertThat(code).isEqualTo(0);
        assertThat(out.toString()).startsWith("Usage: jpx");
    }

    @Test
    public void writes_what_the_build_prints_to_the_writers_it_is_handed() throws IOException {
        Files.writeString(root.resolve("module-info.java"), "module foo {}");
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jenesis-make").orElseThrow().run(new PrintWriter(out), new PrintWriter(err),
                "-Djenesis.make.root=" + root, "-Djenesis.print.progress=false", "configuration");
        assertThat(code).isEqualTo(0);
        assertThat(out.toString())
                .as("the tool hands the build its own writers rather than the streams of the JVM it runs in")
                .contains("jenesis.project.target=target");
        assertThat(System.getProperty("jenesis.make.root"))
                .as("a setting named on a tool's command line configures that one build, and nothing else")
                .isNull();
    }

    @Test
    public void the_exec_tool_refuses_a_container_it_cannot_relaunch_into() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jenesis-exec").orElseThrow().run(new PrintWriter(out), new PrintWriter(err),
                "-Djenesis.make.root=" + root, "-Djenesis.execute.docker", "build");
        assertThat(code).isEqualTo(1);
        assertThat(out.toString() + err)
                .as("running a program in a container replaces the process it runs in, which a tool cannot do")
                .contains("A dockerized program cannot be run by the jenesis-exec tool");
    }

    @Test
    public void refuses_a_toolchain_it_cannot_relaunch_onto() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jenesis-make").orElseThrow().run(new PrintWriter(out), new PrintWriter(err),
                "-Djenesis.make.root=" + root, "-Djenesis.toolchain.version=25", "configuration");
        assertThat(code).isEqualTo(1);
        assertThat(out.toString() + err)
                .contains("jenesis.toolchain.version cannot be honored by the jenesis-make tool");
    }
}
