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
        assertThat(trained.getParent()).isEqualTo(root.resolve(".jenesis").resolve("aot"));
        FileTime written = Files.getLastModifiedTime(trained);

        make(classPath(), List.of(), "build");

        assertThat(cache())
                .as("the second build reuses what the first one trained")
                .isEqualTo(trained);
        assertThat(Files.getLastModifiedTime(trained)).isEqualTo(written);
    }

    @Test
    public void hands_the_tools_the_folder_of_its_own_cache() throws Exception {
        Path folder = aotFolder(make(classPath(), List.of(), "configuration"));
        assertThat(folder.getFileName()).hasToString("aot");
        assertThat(folder.getParent().getFileName()).hasToString(".jenesis");
        assertThat(Files.isSameFile(folder.getParent().getParent(), root))
                .as("the tools a build forks start from caches of their own beside the engine's, in " + folder)
                .isTrue();
    }

    @Test
    public void hands_the_tools_the_cache_folder_a_command_line_names_relative_to_its_working_directory() throws Exception {
        Path folder = aotFolder(make(classPath(), List.of("-Djenesis.aot.folder=caches"), "configuration"));
        assertThat(folder.getFileName()).hasToString("caches");
        assertThat(Files.isSameFile(folder.getParent(), root))
                .as("a relative folder on a command line is resolved against the folder the command runs in,"
                        + " the project root here, not " + folder)
                .isTrue();
    }

    @Test
    public void reuses_the_cache_of_an_engine_run_from_its_classes(@TempDir Path exploded) throws Exception {
        Path engine = engine();
        if (Files.isDirectory(engine)) {
            exploded = engine;
        } else {
            try (JarFile jar = new JarFile(engine.toFile())) {
                for (JarEntry entry : Collections.list(jar.entries())) {
                    Path target = exploded.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, target);
                        }
                    }
                }
            }
        }
        List<String> launch = List.of("-cp", exploded.toString(), "build.jenesis.Make");
        make(launch, List.of(), "build");
        Path trained = cache();
        FileTime written = Files.getLastModifiedTime(trained);

        make(launch, List.of(), "build");

        assertThat(cache())
                .as("the build relaunched from the packed classes keeps the folder its launcher prepared")
                .isEqualTo(trained);
        assertThat(Files.getLastModifiedTime(trained)).isEqualTo(written);
        assertThat(root.resolve(".jenesis").resolve("aot").resolve("engine.jar")).isRegularFile();
    }

    @Test
    public void trains_a_cache_for_an_engine_run_from_the_module_path() throws Exception {
        make(List.of("-p", engine().toString(), "-m", "build.jenesis/build.jenesis.Make"), List.of(), "build");
        Path trained = cache();

        make(List.of("-p", engine().toString(), "-m", "build.jenesis/build.jenesis.Make"), List.of(), "build");

        assertThat(cache())
                .as("an engine that runs as a named module is relaunched as one, and its cache reused")
                .isEqualTo(trained);
        assertThat(root.resolve(".jenesis").resolve("aot").resolve("engine.jar"))
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
    public void clears_the_folder_of_caches_once_it_outlived_its_lifetime() throws Exception {
        make(classPath(), List.of(), "build");
        Path folder = root.resolve(".jenesis").resolve("aot"), stamp = folder.resolve("aot.properties");
        Path tool = Files.createDirectories(folder.resolve("0123456789abcdef0123456789abcdef")).resolve("cache.aot");
        Files.writeString(tool, "a tool's cache");
        Properties aged = new Properties();
        try (Reader reader = Files.newBufferedReader(stamp)) {
            aged.load(reader);
        }
        aged.setProperty("created", Instant.now().minus(Duration.ofHours(2)).toString());
        try (Writer writer = Files.newBufferedWriter(stamp)) {
            aged.store(writer, null);
        }

        make(classPath(), List.of("-Djenesis.aot.lifetime=PT1H"), "build");

        assertThat(tool).as("the folder is cleared at once, the tools' caches with the engine's").doesNotExist();
        assertThat(cache()).as("the engine's cache is trained again").exists();
    }

    @Test
    public void keeps_the_folder_of_caches_while_the_engine_and_the_jvm_stay_the_same() throws Exception {
        make(classPath(), List.of(), "build");
        Path tool = Files.createDirectories(root.resolve(".jenesis").resolve("aot").resolve("0123456789abcdef0123456789abcdef"))
                .resolve("cache.aot");
        Files.writeString(tool, "a tool's cache");

        make(classPath(), List.of(), "build");

        assertThat(tool).exists();
    }

    @Test
    public void clears_the_folder_of_caches_started_for_another_engine() throws Exception {
        Path folder = Files.createDirectories(root.resolve(".jenesis").resolve("aot"));
        Files.writeString(folder.resolve("aot.properties"), "engine=0123456789abcdef\ncreated=" + Instant.now() + "\n");
        Path stale = folder.resolve("engine-0123456789ab.aot"),
                tool = Files.createDirectories(folder.resolve("0123456789abcdef0123456789abcdef")).resolve("cache.aot");
        Files.writeString(stale, "trained for another engine");
        Files.writeString(tool, "trained beside another engine");

        make(classPath(), List.of(), "build");

        assertThat(stale).as("no cache of another engine is kept beside the one that fits").doesNotExist();
        assertThat(tool).doesNotExist();
        assertThat(cache()).exists();
    }

    @Test
    public void rejects_a_mode_it_does_not_know() {
        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings("make.mode", "cached")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.make.mode")
                .hasMessageContaining("source, compiled, aot or daemon");
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
        Files.writeString(root.resolve("jenesis.properties"), "jenesis.aot.folder=../elsewhere\n");

        assertThatThrownBy(() -> new Make("build.jenesis.Project", settings()))
                .as("a project would otherwise have a build write and sweep files wherever it names")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.aot.folder");
    }

    @Test
    public void places_a_cache_outside_the_project_where_the_command_line_names_it() {
        assertThat(new Make("build.jenesis.Project", settings("aot.folder", "../elsewhere")))
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
        assertThat(caches).as("one cache under " + root.resolve(".jenesis").resolve("aot")).hasSize(1);
        return caches.getFirst();
    }

    private List<Path> caches() throws IOException {
        Path folder = root.resolve(".jenesis").resolve("aot");
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().matches("engine-[0-9a-f]{12}\\.aot"))
                    .sorted()
                    .toList();
        }
    }

    private static Path aotFolder(String output) {
        assertThat(output).as("the tools start from caches of their own").contains("jenesis.process.mode=aot ");
        Matcher matcher = Pattern.compile("jenesis\\.aot\\.folder=(.+?) +\\[set]").matcher(output);
        assertThat(matcher.find()).as("jenesis.aot.folder is set in:\n" + output).isTrue();
        return Path.of(matcher.group(1));
    }

    private static Path engine() throws URISyntaxException {
        return Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static List<String> classPath() throws URISyntaxException {
        return List.of("-cp", engine().toString(), "build.jenesis.Make");
    }

    private String make(List<String> launch, List<String> settings, String... selectors) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        command.addAll(launch);
        command.addAll(List.of("-Djenesis.make.mode=aot", "-Djenesis.make.global="));
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
        return output;
    }
}
