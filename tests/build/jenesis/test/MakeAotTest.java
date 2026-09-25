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
                /**
                 * A module whose build is small enough for startup to dominate it.
                 *
                 * @jenesis.release 25
                 */
                module sample.app {
                    exports sample.app;
                }
                """);
        Files.writeString(sources.resolve("App.java"), """
                package sample.app;

                public class App {
                }
                """);
    }

    @Test
    public void trains_a_cache_on_the_first_build_and_reuses_it_on_the_next() throws Exception {
        make(classPath(), List.of(), "build");
        Path trained = cache();
        assertThat(trained.getFileName().toString())
                .as("the engine and the JVM it was trained for are the name")
                .matches("engine-[0-9a-f]{12}\\.aot");
        FileTime written = Files.getLastModifiedTime(trained);

        make(classPath(), List.of(), "build");

        assertThat(cache())
                .as("the second build reuses what the first one trained")
                .isEqualTo(trained);
        assertThat(Files.getLastModifiedTime(trained)).isEqualTo(written);
    }

    @Test
    public void trains_a_cache_for_an_engine_run_from_the_module_path() throws Exception {
        make(List.of("-p", engine().toString(), "-m", "build.jenesis/build.jenesis.Make"), List.of(), "build");
        Path trained = cache();

        make(List.of("-p", engine().toString(), "-m", "build.jenesis/build.jenesis.Make"), List.of(), "build");

        assertThat(cache())
                .as("an engine that runs as a named module is relaunched as one, and its cache reused")
                .isEqualTo(trained);
        assertThat(root.resolve(".jenesis").resolve("engine.jar"))
                .as("the module path is handed on as it stands rather than packaged")
                .doesNotExist();
    }

    @Test
    public void trains_no_cache_for_a_selector_that_only_prints() throws Exception {
        make(classPath(), List.of(), "help");

        assertThat(caches())
                .as("a selector that only prints loads none of the machinery worth caching")
                .isEmpty();
    }

    @Test
    public void retrains_a_cache_that_outlived_its_lifetime() throws Exception {
        make(classPath(), List.of(), "build");
        Path trained = cache();
        FileTime aged = FileTime.from(Instant.now().minus(Duration.ofHours(2)));
        Files.setLastModifiedTime(trained, aged);

        make(classPath(), List.of("-Djenesis.aot.lifetime=PT1H"), "build");

        assertThat(Files.getLastModifiedTime(cache()))
                .as("a cache older than the lifetime is trained again")
                .isNotEqualTo(aged);
    }

    @Test
    public void sweeps_a_cache_trained_for_another_engine_and_nothing_that_only_resembles_one() throws Exception {
        Path folder = Files.createDirectories(root.resolve(".jenesis"));
        Path stale = folder.resolve("engine-0123456789ab.aot"), unrelated = folder.resolve("engine-notes.aot");
        Files.writeString(stale, "trained for another engine");
        Files.writeString(unrelated, "not a cache");

        make(classPath(), List.of(), "build");

        assertThat(stale)
                .as("a cache no build can use is removed where the one that fits is trained")
                .doesNotExist();
        assertThat(unrelated)
                .as("only a name a build writes is swept")
                .exists();
    }

    @Test
    public void refuses_a_cache_beside_a_daemon() {
        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings("make.aot", "true", "make.daemon", "true")))
                .as("a daemon holds the engine a cache would hand to a starting JVM")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.make.aot")
                .hasMessageContaining("jenesis.make.daemon");
    }

    @Test
    public void refuses_a_cache_without_the_compiled_engine() {
        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings("make.aot", "true", "make.compile", "false")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.make.aot")
                .hasMessageContaining("jenesis.make.compile");
    }

    @Test
    public void refuses_a_daemon_switched_on_beside_a_cache() {
        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings()).aot(true).daemon(true))
                .as("the combination is refused wherever it is made, not only where a setting names it")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.make.daemon");
    }

    @Test
    public void rejects_a_lifetime_that_is_not_a_duration() {
        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings("aot.lifetime", "12h")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.aot.lifetime")
                .hasMessageContaining("PT12H");
    }

    @Test
    public void refuses_a_cache_that_a_file_of_the_project_places_outside_it() throws IOException {
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.aot.file=../elsewhere/engine.aot\n");

        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings()))
                .as("a project would otherwise have a build write and sweep files wherever it names")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.aot.file");
    }

    @Test
    public void places_a_cache_outside_the_project_where_the_command_line_names_it() {
        assertThat(new Make("build.jenesis.Project", settings("aot.file", "../elsewhere/engine.aot")))
                .as("the command line and the user's own file are trusted with a location of their choice")
                .isNotNull();
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

    private Path cache() throws IOException {
        List<Path> caches = caches();
        assertThat(caches).as("one cache under " + root.resolve(".jenesis")).hasSize(1);
        return caches.getFirst();
    }

    private List<Path> caches() throws IOException {
        Path folder = root.resolve(".jenesis");
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().matches("engine-[0-9a-f]{12}\\.aot"))
                    .sorted()
                    .toList();
        }
    }

    private static Path engine() throws URISyntaxException {
        return Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static List<String> classPath() throws URISyntaxException {
        return List.of("-cp", engine().toString(), "build.jenesis.Make");
    }

    private void make(List<String> launch, List<String> settings, String... selectors) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        command.addAll(launch);
        command.addAll(List.of("-Djenesis.make.aot=true", "-Djenesis.make.global="));
        command.addAll(settings);
        command.addAll(List.of(selectors));
        Process process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor())
                .as(String.join(" ", command) + " exited with:\n" + output)
                .isZero();
    }
}
