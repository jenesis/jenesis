package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Jdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JdkTest {

    @TempDir
    private Path home;

    @Test
    public void names_the_modules_of_the_running_jvm_outside_a_native_image() {
        assertThat(Jdk.image()).isFalse();
        assertThat(Jdk.modules())
                .contains("java.base", "java.sql", "jdk.compiler")
                .isEqualTo(ModuleFinder.ofSystem().findAll().stream()
                        .map(reference -> reference.descriptor().name())
                        .collect(Collectors.toSet()));
        assertThat(Jdk.system().find("java.sql")).isPresent();
        assertThat(Jdk.boot()).isSameAs(ModuleLayer.boot());
    }

    @Test
    public void reads_the_modules_of_the_running_jvm_from_its_release_file() {
        assertThat(Jdk.released(Path.of(System.getProperty("java.home"))))
                .as("a native image names the modules of the JDK by its release file, so the two agree")
                .isEqualTo(Jdk.modules());
    }

    @Test
    public void reads_a_release_file_without_its_quotes() throws IOException {
        Files.writeString(home.resolve("release"), """
                JAVA_VERSION="25.0.4"
                MODULES="java.base java.sql jdk.compiler"
                """);

        assertThat(Jdk.released(home)).containsExactlyInAnyOrder("java.base", "java.sql", "jdk.compiler");
    }

    @Test
    public void refuses_a_release_file_that_names_no_modules() throws IOException {
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"25.0.4\"\n");

        assertThatThrownBy(() -> Jdk.released(home))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(home.toString())
                .hasMessageContaining("MODULES");
        assertThatThrownBy(() -> Jdk.released(home.resolve("missing")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("release file");
    }

    @Test
    public void finds_what_a_native_image_cannot_run_among_the_causes() {
        Jdk.Unavailable unavailable = new Jdk.Unavailable("a plugin");

        assertThat(Jdk.unsupported(new IllegalStateException(new RuntimeException(unavailable))))
                .isSameAs(unavailable);
        assertThat(Jdk.unsupported(new NoClassDefFoundError("sample.Missing")))
                .as("on a JVM a missing class is the build's failure, not one of a native image")
                .isNull();
        assertThat(Jdk.unsupported(new IllegalStateException("failed"))).isNull();
    }
}
