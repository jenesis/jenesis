package build.jenesis.test;

import module java.base;
import build.jenesis.Jdk;
import module org.junit.jupiter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdkTest {

    @TempDir
    private Path home;

    @Test
    public void reads_the_version_of_a_jdk_from_its_release_file() throws IOException {
        Files.writeString(home.resolve("release"), """
                IMPLEMENTOR="Eclipse Adoptium"
                JAVA_RUNTIME_VERSION="25.0.5+11-LTS"
                JAVA_VERSION="25.0.5"
                """);
        assertThat(Jdk.version(home)).isEqualTo(Runtime.Version.parse("25.0.5+11-LTS"));
    }

    @Test
    public void reads_the_java_version_where_the_release_file_names_no_runtime_version() throws IOException {
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"26\"\n");
        assertThat(Jdk.version(home).feature()).isEqualTo(26);
    }

    @Test
    public void refuses_a_jdk_without_a_release_file_naming_java_home() {
        assertThatThrownBy(() -> Jdk.version(home))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(home.toString())
                .hasMessageContaining("JAVA_HOME");
    }

    @Test
    public void refuses_a_malformed_version() throws IOException {
        Files.writeString(home.resolve("release"), "JAVA_RUNTIME_VERSION=\"not a version\"\n");
        assertThatThrownBy(() -> Jdk.version(home))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a version");
    }

    @Test
    public void reads_the_release_file_without_its_quotes() throws IOException {
        Files.writeString(home.resolve("release"), """
                JAVA_VERSION="25.0.5"
                MODULES="java.base java.sql jdk.compiler"
                """);
        assertThat(Jdk.release(home))
                .containsEntry("JAVA_VERSION", "25.0.5")
                .containsEntry("MODULES", "java.base java.sql jdk.compiler");
    }

    @Test
    public void names_the_version_of_the_running_jvm_outside_a_native_image() {
        assertThat(Jdk.image()).isFalse();
        assertThat(Jdk.version())
                .as("a build on a JVM keys its steps on the JVM it runs on, as it always did")
                .isEqualTo(Runtime.version());
        assertThat(Jdk.javaVersion()).isEqualTo(System.getProperty("java.version"));
    }

    @Test
    public void names_the_modules_of_the_running_jvm_outside_a_native_image() {
        assertThat(Jdk.modules())
                .contains("java.base", "java.sql", "jdk.compiler")
                .isEqualTo(ModuleFinder.ofSystem().findAll().stream()
                        .map(reference -> reference.descriptor().name())
                        .collect(Collectors.toSet()));
        assertThat(Jdk.system().find("java.sql")).isPresent();
    }

    @Test
    public void names_the_same_modules_as_the_release_file_of_the_running_jdk() {
        assertThat(Set.of(Jdk.release(Path.of(System.getProperty("java.home"))).get("MODULES").split("\\s+")))
                .as("a native image reads the modules of the JDK from its release file, so the two agree")
                .isEqualTo(Jdk.modules());
    }
}
