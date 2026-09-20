package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Make;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MakeAotTest {

    @TempDir
    private Path root;

    private Path cache;

    @BeforeEach
    public void setUp() throws IOException {
        Path sources = Files.createDirectories(root.resolve("sources").resolve("sample").resolve("app"));
        Files.writeString(root.resolve("sources").resolve("module-info.java"), """
                /// @jenesis.main sample.app.App
                module sample.app {
                    exports sample.app;
                }
                """);
        Files.writeString(sources.resolve("App.java"), """
                package sample.app;

                public class App {
                    public static void main(String[] args) {
                        System.out.println("sample");
                    }
                }
                """);
        cache = root.resolve(".jenesis").resolve("engine.aot");
    }

    @Test
    public void trains_a_cache_on_the_first_build_and_reuses_it_on_the_next() throws Exception {
        assertThat(make("build")).isEqualTo(0);
        assertThat(cache).isRegularFile();
        Path digest = cache.resolveSibling("engine.aot.digest");
        assertThat(digest).isRegularFile();
        String trained = Files.readString(digest);
        FileTime written = Files.getLastModifiedTime(cache);

        assertThat(make("build")).isEqualTo(0);

        assertThat(Files.readString(digest))
                .as("the second build reuses what the first one trained")
                .isEqualTo(trained);
        assertThat(Files.getLastModifiedTime(cache)).isEqualTo(written);
    }

    @Test
    public void trains_no_cache_for_a_selector_that_builds_nothing() throws Exception {
        assertThat(make("help")).isEqualTo(0);

        assertThat(cache)
                .as("a selector that only prints loads none of the machinery worth caching")
                .doesNotExist();
    }

    @Test
    public void retrains_a_cache_that_outlived_its_lifetime() throws Exception {
        assertThat(make("build")).isEqualTo(0);
        FileTime written = Files.getLastModifiedTime(cache);
        Files.setLastModifiedTime(cache, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        assertThat(make(List.of("-Djenesis.aot.lifetime=PT1H"), "build")).isEqualTo(0);

        assertThat(Files.getLastModifiedTime(cache))
                .as("a cache older than the lifetime is trained again")
                .isNotEqualTo(written);
    }

    @Test
    public void rejects_a_lifetime_that_is_not_a_duration() {
        System.setProperty("jenesis.aot.lifetime", "12h");
        try {
            assertThatThrownBy(() -> new Make("build.jenesis.Project"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jenesis.aot.lifetime")
                    .hasMessageContaining("PT12H");
        } finally {
            System.clearProperty("jenesis.aot.lifetime");
        }
    }

    private int make(String... selectors) throws Exception {
        return make(List.of(), selectors);
    }

    private int make(List<String> options, String... selectors) throws Exception {
        Path engine = Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djenesis.aot.enabled=true",
                "-Djenesis.aot.file=" + cache));
        command.addAll(options);
        command.addAll(List.of("-cp", engine.toString(), "build.jenesis.Make"));
        command.addAll(List.of(selectors));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            throw new IllegalStateException(String.join(" ", command) + " exited with " + code + ":\n" + output);
        }
        return code;
    }
}
