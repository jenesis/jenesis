package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Make;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MakeAotTest {

    @TempDir
    private Path root;

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
    }

    @Test
    public void trains_a_cache_on_the_first_build_and_reuses_it_on_the_next() throws Exception {
        assertThat(make("build")).isEqualTo(0);
        Path trained = cache();
        assertThat(trained.getFileName().toString())
                .as("the engine and the JVM it was trained for are the name")
                .matches("engine-[0-9a-f]{12}\\.aot");
        FileTime written = Files.getLastModifiedTime(trained);

        assertThat(make("build")).isEqualTo(0);

        assertThat(cache())
                .as("the second build reuses what the first one trained")
                .isEqualTo(trained);
        assertThat(Files.getLastModifiedTime(trained)).isEqualTo(written);
    }

    @Test
    public void trains_no_cache_for_a_selector_that_builds_nothing() throws Exception {
        assertThat(make("help")).isEqualTo(0);

        assertThat(caches())
                .as("a selector that only prints loads none of the machinery worth caching")
                .isEmpty();
    }

    @Test
    public void retrains_a_cache_that_outlived_its_lifetime() throws Exception {
        assertThat(make("build")).isEqualTo(0);
        Path trained = cache();
        FileTime written = Files.getLastModifiedTime(trained);
        Files.setLastModifiedTime(trained, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        assertThat(make(List.of("-Djenesis.aot.lifetime=PT1H"), "build")).isEqualTo(0);

        assertThat(Files.getLastModifiedTime(cache()))
                .as("a cache older than the lifetime is trained again")
                .isNotEqualTo(written);
    }

    @Test
    public void sweeps_a_cache_trained_for_another_engine() throws Exception {
        Path stale = Files.createDirectories(root.resolve(".jenesis")).resolve("engine-0123456789ab.aot");
        Files.writeString(stale, "trained for something else");

        assertThat(make("build")).isEqualTo(0);

        assertThat(stale)
                .as("a cache no build can use is removed where the one that fits is trained")
                .doesNotExist();
        assertThat(caches()).hasSize(1);
    }

    @Test
    public void refuses_a_cache_beside_a_daemon() {
        System.setProperty("jenesis.aot.enabled", "true");
        System.setProperty("jenesis.make.daemon", "true");
        try {
            assertThatThrownBy(() -> new Make("build.jenesis.Project"))
                    .as("a daemon holds the engine a cache would hand to a starting JVM")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jenesis.aot.enabled")
                    .hasMessageContaining("jenesis.make.daemon");
        } finally {
            System.clearProperty("jenesis.aot.enabled");
            System.clearProperty("jenesis.make.daemon");
        }
    }

    @Test
    public void refuses_a_cache_without_the_compiled_engine() {
        System.setProperty("jenesis.aot.enabled", "true");
        System.setProperty("jenesis.make.compile", "false");
        try {
            assertThatThrownBy(() -> new Make("build.jenesis.Project"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jenesis.aot.enabled")
                    .hasMessageContaining("jenesis.make.compile");
        } finally {
            System.clearProperty("jenesis.aot.enabled");
            System.clearProperty("jenesis.make.compile");
        }
    }

    @Test
    public void refuses_a_daemon_switched_on_beside_a_cache() {
        assertThatThrownBy(() -> new Make("build.jenesis.Project").aot(true).daemon(true))
                .as("the combination is refused wherever it is made, not only where a property names it")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.make.daemon");
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

    private Path cache() throws IOException {
        List<Path> caches = caches();
        if (caches.size() != 1) {
            throw new IllegalStateException("Expected one cache under " + root.resolve(".jenesis") + ": " + caches);
        }
        return caches.getFirst();
    }

    private List<Path> caches() throws IOException {
        Path folder = root.resolve(".jenesis");
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".aot")).sorted().toList();
        }
    }

    private int make(String... selectors) throws Exception {
        return make(List.of(), selectors);
    }

    private int make(List<String> options, String... selectors) throws Exception {
        Path engine = Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djenesis.aot.enabled=true"));
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
