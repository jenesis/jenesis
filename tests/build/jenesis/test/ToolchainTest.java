package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.Toolchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ToolchainTest {

    @TempDir
    private Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"025", "25.", "25.01", "temurin", "25_temurin", "25-", "25-tem urin", "25-tem1",
            "25.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0"})
    public void rejects_a_malformed_version_naming_the_grammar(String version) {
        assertThatThrownBy(() -> new Toolchain().version(version))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected <feature>[.<interim>[.<update>...]][-<word>...]");
    }

    @Test
    public void rejects_a_version_jenesis_cannot_run_on() {
        assertThatThrownBy(() -> new Toolchain().version("21-temurin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs 25 or newer")
                .hasMessageContaining("@jenesis.release");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdks/*", "./jdks", "~user/jdks", "*", "/opt/jdk-*"})
    public void rejects_a_search_path_entry_that_is_relative_or_matches_part_of_a_name(String entry) {
        assertThatThrownBy(() -> new Toolchain().searchpath(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed entry");
    }

    @Test
    public void answers_the_running_jvm_when_no_version_is_required() throws IOException, InterruptedException {
        assertThat(new Toolchain().version("").searchpath("").home())
                .isEqualTo(Path.of(System.getProperty("java.home")));
    }

    @Test
    public void answers_the_running_jvm_when_it_matches_even_if_a_newer_jdk_does_too() throws IOException, InterruptedException {
        Assumptions.assumeTrue(Runtime.version().pre().isEmpty(), "the running JVM is not a pre-release");
        jdk(folder.resolve("jdks/newer"), Runtime.version().feature() + ".99.99", "Acme Labs", null);

        assertThat(new Toolchain()
                .version(Integer.toString(Runtime.version().feature()))
                .searchpath(folder.resolve("jdks") + "/*")
                .home())
                .as("the running JVM is checked first, so a build that already runs on a match never relaunches")
                .isEqualTo(Path.of(System.getProperty("java.home")));
    }

    @Test
    public void fails_naming_the_running_jvm_when_it_does_not_match_and_nothing_is_searched() {
        assertThatThrownBy(() -> new Toolchain().version("25-acme").searchpath("").home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.toolchain.version=25-acme")
                .hasMessageContaining(System.getProperty("java.home"))
                .hasMessageContaining("jenesis.toolchain.searchpath is empty");
    }

    @Test
    public void selects_the_highest_matching_version_across_the_search_path() throws IOException, InterruptedException {
        jdk(folder.resolve("first/older"), "25.0.1+3", "Acme Labs", null);
        Path newer = jdk(folder.resolve("second/newer"), "25.0.2+7", "Acme Labs", null);
        jdk(folder.resolve("second/other"), "25.0.9+1", "Other Labs", null);

        assertThat(new Toolchain()
                .version("25-acme")
                .searchpath(folder.resolve("first") + "/*," + folder.resolve("second") + "/*")
                .home()).isEqualTo(newer);
    }

    @Test
    public void prefers_the_earlier_search_path_entry_between_equal_versions() throws IOException, InterruptedException {
        Path first = jdk(folder.resolve("first/jdk"), "25.0.2+7", "Acme Labs", null);
        Path second = jdk(folder.resolve("second/jdk"), "25.0.2+7", "Acme Labs", null);

        assertThat(new Toolchain().version("25.0.2-acme").searchpath(second + "," + first).home())
                .isEqualTo(second);
    }

    @Test
    public void matches_the_words_of_the_vendor_its_version_and_the_runtime_version_ignoring_case()
            throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), "25.0.1+3-LTS", "Acme Labs, Inc.", "Roadrunner-25.0.1+3");

        assertThat(new Toolchain().version("25.0-ACME-roadrunner-lts").searchpath(home.toString()).home())
                .isEqualTo(home);
        assertThatThrownBy(() -> new Toolchain().version("25-acme-coyote").searchpath(home.toString()).home())
                .as("every word of the version has to be one of the JDK's")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No JDK matches jenesis.toolchain.version=25-acme-coyote");
    }

    @Test
    public void selects_a_pre_release_only_when_the_version_names_it() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), "26-ea+17-1764", "Acme Labs", null);

        assertThatThrownBy(() -> new Toolchain().version("26-acme").searchpath(home.toString()).home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("26-ea+17-1764 (acme labs ea)");
        assertThat(new Toolchain().version("26-acme-ea").searchpath(home.toString()).home()).isEqualTo(home);
    }

    @Test
    public void lists_what_it_found_and_skipped_when_nothing_matches() throws IOException, InterruptedException {
        Path found = jdk(folder.resolve("jdks/found"), "25.0.4+2", "Acme Labs", null);
        Path malformed = jdk(folder.resolve("jdks/malformed"), "banana", "Acme Labs", null);
        Path executable = jdk(folder.resolve("jdks/executable"), "25.0.4+2", "Acme Labs", null);
        Files.delete(executable.resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java"));
        Files.createDirectories(folder.resolve("jdks/unrelated"));

        assertThatThrownBy(() -> new Toolchain().version("25-coyote").searchpath(folder.resolve("jdks") + "/*").home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(found + "  25.0.4+2 (acme labs)")
                .hasMessageContaining(malformed + "  skipped, as its release file names a malformed JAVA_RUNTIME_VERSION")
                .hasMessageContaining(executable + "  skipped, as it has no java executable in bin")
                .hasMessageNotContaining("unrelated")
                .hasMessageContaining("~/.jenesis/jenesis.properties");
    }

    @Test
    public void counts_a_jdk_reached_through_a_link_once() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdks/25.0.7"), "25.0.7+1", "Acme Labs", null);
        try {
            Files.createSymbolicLink(folder.resolve("jdks/current"), home);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.abort("symbolic links are unavailable: " + exception);
        }

        assertThatThrownBy(() -> new Toolchain().version("25-coyote").searchpath(folder.resolve("jdks") + "/*").home())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(exception -> assertThat(exception.getMessage().split("25\\.0\\.7\\+1", -1)).hasSize(2));
    }

    @Test
    public void replaces_control_characters_in_the_paths_it_reports() throws IOException, InterruptedException {
        Path home;
        try {
            home = jdk(folder.resolve("jdks/escape[31m"), "banana", "Acme Labs", null);
        } catch (InvalidPathException exception) {
            Assumptions.abort("the file system rejects control characters in names: " + exception);
            return;
        }

        assertThatThrownBy(() -> new Toolchain().version("25-coyote").searchpath(folder.resolve("jdks") + "/*").home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(home.getParent().resolve("escape?[31m").toString())
                .hasMessageNotContaining("");
    }

    @Test
    public void refuses_a_matching_jdk_with_a_file_every_user_can_write() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), "25.0.1+3", "Acme Labs", null);
        Assumptions.assumeTrue(posix(home), "POSIX permissions");
        Files.setPosixFilePermissions(home.resolve("release"), PosixFilePermissions.fromString("rw-r--rw-"));

        assertThatThrownBy(() -> new Toolchain().version("25-acme").searchpath(home.toString()).home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(home.resolve("release") + " is writable by every user")
                .hasMessageContaining("chmod -R go-w " + home);
    }

    @Test
    public void accepts_a_jdk_writable_by_the_private_group_of_its_owner() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), "25.0.1+3", "Acme Labs", null);
        Assumptions.assumeTrue(posix(home), "POSIX permissions");
        PosixFileAttributes attributes = Files.readAttributes(home, PosixFileAttributes.class);
        Assumptions.assumeTrue(attributes.group().getName().equals(attributes.owner().getName()),
                "new files belong to the user's private group");
        Files.setPosixFilePermissions(home.resolve("release"), PosixFilePermissions.fromString("rw-rw-r--"));

        assertThat(new Toolchain().version("25-acme").searchpath(home.toString()).home()).isEqualTo(home);
    }

    @Test
    public void refuses_a_jdk_writable_by_a_group_shared_with_other_users() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), "25.0.1+3", "Acme Labs", null);
        Assumptions.assumeTrue(posix(home), "POSIX permissions");
        PosixFileAttributes attributes = Files.readAttributes(home, PosixFileAttributes.class);
        Assumptions.assumeFalse(attributes.group().getName().equals(attributes.owner().getName()),
                "new files belong to a group other than the user's private one");
        Files.setPosixFilePermissions(home.resolve("release"), PosixFilePermissions.fromString("rw-rw-r--"));

        assertThatThrownBy(() -> new Toolchain().version("25-acme").searchpath(home.toString()).home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(home.resolve("release") + " is writable by the group "
                        + attributes.group().getName());
    }

    @Test
    public void pins_the_toolchain_in_the_command_that_repeats_the_program() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), "25.0.1+3", "Acme Labs", null);

        List<String> command = new Toolchain()
                .version("25-acme")
                .searchpath(home.toString())
                .command(Child.class, List.of("-Djenesis.test.forwarded=yes"), List.of("first", "second"));

        assertThat(command.getFirst())
                .isEqualTo(home.resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java").toString());
        assertThat(command.subList(1, 4))
                .as("the relaunched JVM only asserts the version, so it can never relaunch again")
                .containsExactly("-Djenesis.test.forwarded=yes",
                        "-Djenesis.toolchain.version=25-acme",
                        "-Djenesis.toolchain.searchpath=");
        assertThat(command).anyMatch(element -> element.endsWith(Child.class.getName()));
        assertThat(command.subList(command.size() - 2, command.size())).containsExactly("first", "second");
    }

    @Test
    public void relaunches_the_program_on_the_selected_jdk() throws IOException, InterruptedException {
        Path home = jdk(folder.resolve("jdk"), Runtime.version().toString(), "Acme Labs", null);
        Assumptions.assumeTrue(posix(home), "a JDK home whose java hands over to the running one");
        Path java = home.resolve("bin/java");
        Files.writeString(java, "#!/bin/sh\nexec '" + Path.of(System.getProperty("java.home"), "bin", "java") + "' \"$@\"\n");
        Files.setPosixFilePermissions(java, PosixFilePermissions.fromString("rwxr-xr-x"));

        int code = new Toolchain()
                .version(Runtime.version().feature() + "-acme" + Runtime.version().pre().map(pre -> "-" + pre).orElse(""))
                .searchpath(home.toString())
                .launch(Child.class, List.of("-Djenesis.test.forwarded=yes"), List.of("first", "second"));

        assertThat(code)
                .as("the child sees the forwarded option, the arguments and an empty search path")
                .isZero();
    }

    @Test
    public void runs_the_installer_when_no_jdk_matches_and_selects_the_jdk_it_installed() throws Exception {
        Path jdks = Files.createDirectories(folder.resolve("jdks"));
        Path installer = installer(folder.resolve("install"), "printf '%s\\n' \"$@\" > '" + folder.resolve("arguments") + "'\n"
                + "mkdir -p '" + jdks.resolve("installed/bin") + "'\n"
                + "printf 'JAVA_RUNTIME_VERSION=\"25.0.9+1\"\\nIMPLEMENTOR=\"Acme Labs\"\\n' > '" + jdks.resolve("installed/release") + "'\n"
                + ": > '" + jdks.resolve("installed/bin/java") + "'\n"
                + "chmod -R go-w '" + jdks.resolve("installed") + "'\n");

        Path home = new Toolchain()
                .version("25-acme")
                .searchpath(jdks + "/*")
                .installer(installer + " --quiet")
                .home();

        assertThat(home).isEqualTo(jdks.resolve("installed"));
        assertThat(Files.readAllLines(folder.resolve("arguments")))
                .as("the installer gets its own words, then the version as its last argument")
                .containsExactly("--quiet", "25-acme");
    }

    @Test
    public void runs_no_installer_when_a_jdk_matches() throws Exception {
        Path home = jdk(folder.resolve("jdks/found"), "25.0.1+3", "Acme Labs", null);
        Path installer = installer(folder.resolve("install"), ": > '" + folder.resolve("ran") + "'\n");

        assertThat(new Toolchain().version("25-acme").searchpath(folder.resolve("jdks") + "/*").installer(installer.toString()).home())
                .isEqualTo(home);
        assertThat(folder.resolve("ran")).doesNotExist();
    }

    @Test
    public void runs_no_installer_when_nothing_is_searched() throws Exception {
        Path installer = installer(folder.resolve("install"), ": > '" + folder.resolve("ran") + "'\n");

        assertThatThrownBy(() -> new Toolchain().version("25-acme").searchpath("").installer(installer.toString()).home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.toolchain.searchpath is empty");
        assertThat(folder.resolve("ran"))
                .as("an empty search path only checks the running JVM, so nothing could find what is installed")
                .doesNotExist();
    }

    @Test
    public void fails_naming_the_installer_and_its_exit_code() throws Exception {
        Path installer = installer(folder.resolve("install"), "exit 3\n");

        assertThatThrownBy(() -> new Toolchain()
                .version("25-acme")
                .searchpath(Files.createDirectories(folder.resolve("jdks")) + "/*")
                .installer(installer.toString())
                .home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The installer " + installer + " failed with exit code 3");
    }

    @Test
    public void fails_when_the_installer_puts_no_matching_jdk_into_the_search_path() throws Exception {
        Path installer = installer(folder.resolve("install"), "exit 0\n");

        assertThatThrownBy(() -> new Toolchain()
                .version("25-acme")
                .searchpath(Files.createDirectories(folder.resolve("jdks")) + "/*")
                .installer(installer.toString())
                .home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ran for jenesis.toolchain.version=25-acme")
                .hasMessageContaining("and still no JDK matches it");
    }

    @Test
    public void runs_the_installer_in_the_home_folder_rather_than_the_project() throws Exception {
        Path installer = installer(folder.resolve("install"), "pwd -P > '" + folder.resolve("directory") + "'\n");

        assertThatThrownBy(() -> new Toolchain()
                .version("25-acme")
                .searchpath(Files.createDirectories(folder.resolve("jdks")) + "/*")
                .installer(installer.toString())
                .home())
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(folder.resolve("directory")).strip())
                .as("a tool that reads configuration from its working directory never sees the project's")
                .isEqualTo(Path.of(System.getProperty("user.home")).toRealPath().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tools/install-jdk", "./install-jdk", "C:install-jdk", "~user/install-jdk"})
    public void rejects_an_installer_named_by_a_relative_path(String installer) {
        assertThatThrownBy(() -> new Toolchain().installer(installer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed jenesis.toolchain.installer");
    }

    @Test
    public void fails_when_the_installer_name_is_on_no_folder_of_the_path() throws IOException, InterruptedException {
        assertThatThrownBy(() -> new Toolchain()
                .version("25-acme")
                .searchpath(Files.createDirectories(folder.resolve("jdks")) + "/*")
                .installer("jenesis-no-such-installer")
                .home())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis-no-such-installer")
                .hasMessageContaining("on no absolute folder of the PATH");
    }

    private static Path installer(Path script, String body) throws IOException {
        Assumptions.assumeTrue(posix(script.getParent()), "an installer written as a shell script");
        Files.writeString(script, "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }

    private static Path jdk(Path home, String runtime, String implementor, String implementorVersion)
            throws IOException {
        Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java"), "");
        Files.writeString(home.resolve("release"), "JAVA_RUNTIME_VERSION=\"" + runtime + "\"\n"
                + "IMPLEMENTOR=\"" + implementor + "\"\n"
                + (implementorVersion == null ? "" : "IMPLEMENTOR_VERSION=\"" + implementorVersion + "\"\n"));
        if (posix(home)) {
            try (Stream<Path> paths = Files.walk(home)) {
                for (Path path : paths.toList()) {
                    Files.setPosixFilePermissions(path,
                            PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwxr-xr-x" : "rw-r--r--"));
                }
            }
        }
        return home;
    }

    private static boolean posix(Path path) throws IOException {
        return Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class);
    }

    public static class Child {

        public static void main(String... arguments) {
            System.exit("".equals(System.getProperty("jenesis.toolchain.searchpath"))
                    && "yes".equals(System.getProperty("jenesis.test.forwarded"))
                    && List.of(arguments).equals(List.of("first", "second")) ? 0 : 3);
        }
    }
}
