package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Make;

import static org.assertj.core.api.Assertions.assertThat;

public class MakeNativeTest {

    @TempDir
    private Path root;

    @Test
    public void refuses_a_native_image_on_a_jdk_without_native_image() throws Exception {
        Path home = Path.of(System.getProperty("java.home"));
        Assumptions.assumeFalse(Files.exists(home.resolve("bin").resolve("native-image")),
                "the JDK running the tests is a GraalVM, which compiles the image");
        Files.writeString(Files.createDirectories(root.resolve("sources").resolve("sample")).resolve("App.java"), """
                package sample;

                public class App {
                }
                """);
        List<String> command = List.of(home.resolve("bin").resolve("java").toString(),
                "-cp", Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                "build.jenesis.Make",
                "-Djenesis.make.mode=native",
                "-Djenesis.make.global=",
                "build");
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isNotZero();
        assertThat(output)
                .as("the build names what it needs rather than running on a JVM in place of the image")
                .contains("native-image of the GraalVM that runs Make")
                .contains(home.toString())
                .contains("GraalVM 25.5 or newer");
        assertThat(root.resolve(".jenesis").resolve("aot").resolve("native")).doesNotExist();
    }

    @Test
    public void starts_the_tools_of_a_native_build_from_ahead_of_time_caches() throws Exception {
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                "build.jenesis.Make",
                "-Djenesis.make.mode=native",
                "-Djenesis.make.global=",
                "configuration");
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isZero();
        assertThat(output)
                .as("a forked tool starts from a cache of its own, as the image cannot hold it")
                .contains("jenesis.process.mode=aot ");
    }
}
