package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Json;
import build.jenesis.Make;
import org.assertj.core.api.InstanceOfAssertFactories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "shell scripts stand in for GraalVM")
public class MakeNativeTest {

    @TempDir
    private Path root;

    @TempDir
    private Path graalvm;

    @TempDir
    private Path records;

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
        Files.copy(Path.of(System.getProperty("java.home"), "release"), graalvm.resolve("release"));
        Path bin = Files.createDirectories(graalvm.resolve("bin"));
        script(bin.resolve("java"), """
                #!/bin/sh
                for argument do
                  shift
                  case "$argument" in
                    -agentlib:*) ;;
                    *) set -- "$@" "$argument" ;;
                  esac
                done
                exec 'JAVA' "$@"
                """.replace("JAVA", Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        script(bin.resolve("native-image"), """
                #!/bin/sh
                echo compiled >> 'RECORDS/native-image.calls'
                while [ $# -gt 0 ]; do
                  case "$1" in
                    -o) output="$2"; shift ;;
                    -H:ConfigurationFileDirectories=*)
                      configuration="${1#-H:ConfigurationFileDirectories=}"
                      configuration="${configuration%%,*}" ;;
                  esac
                  shift
                done
                rm -rf 'RECORDS/configuration'
                cp -R "$configuration" 'RECORDS/configuration'
                printf '%s\\n' '#!/bin/sh' \\
                  'echo "$@" >> RECORDS/binary.calls' \\
                  'exit $(cat RECORDS/binary.code 2>/dev/null || echo 0)' > "$output"
                chmod +x "$output"
                """.replace("RECORDS", records.toString()));
    }

    @Test
    public void compiles_an_image_on_the_first_build_and_runs_it_on_the_next() throws Exception {
        make("build");

        assertThat(Files.readAllLines(records.resolve("native-image.calls"))).hasSize(1);
        assertThat(records.resolve("binary.calls"))
                .as("the first build runs on a JVM, which records what the image needs")
                .doesNotExist();

        make("build");

        assertThat(Files.readAllLines(records.resolve("native-image.calls")))
                .as("an image is compiled once for an engine, its plugins and what the builds recorded")
                .hasSize(1);
        assertThat(Files.readAllLines(records.resolve("binary.calls")))
                .singleElement(InstanceOfAssertFactories.STRING)
                .contains("-Djenesis.make.mode=native")
                .contains("-Djava.home=" + System.getProperty("java.home"))
                .endsWith("build");
    }

    @Test
    public void runs_the_builds_of_a_project_on_a_jvm_once_its_image_hands_one_over_without_learning_anything()
            throws Exception {
        make("build");
        Files.writeString(records.resolve("binary.code"), "3");

        make("build");

        assertThat(Files.readAllLines(records.resolve("binary.calls"))).hasSize(1);
        assertThat(Files.readAllLines(records.resolve("native-image.calls")))
                .as("the build the image handed over recorded nothing new, so no other image is compiled")
                .hasSize(1);

        make("build");

        assertThat(Files.readAllLines(records.resolve("binary.calls")))
                .as("an image that cannot run a build is not started for it again")
                .hasSize(1);
    }

    @Test
    public void registers_every_serializable_class_of_the_engine_for_the_image() throws Exception {
        make("build");

        Set<String> serializable = new TreeSet<>(), lambdas = new TreeSet<>();
        Path configuration = records.resolve("configuration");
        for (Object entry : (List<?>) ((Map<?, ?>) Json.parse(Files.readString(
                configuration.resolve("reachability-metadata.json")))).get("reflection")) {
            Map<?, ?> type = (Map<?, ?>) entry;
            if (Boolean.TRUE.equals(type.get("serializable")) && type.get("type") instanceof String name) {
                serializable.add(name);
            }
        }
        for (Object entry : (List<?>) ((Map<?, ?>) Json.parse(Files.readString(
                configuration.resolve("serialization-config.json")))).get("lambdaCapturingTypes")) {
            lambdas.add((String) ((Map<?, ?>) entry).get("name"));
        }
        assertThat(classes(type -> Serializable.class.isAssignableFrom(type) && !type.isInterface()))
                .as("a native image deserialises no class its metadata does not name, and every step is serialised"
                        + " to key its cache entry")
                .isSubsetOf(serializable);
        assertThat(classes(type -> Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("$deserializeLambda$"))))
                .as("a native image serialises no lambda of a class its metadata does not name")
                .isSubsetOf(lambdas);
    }

    @Test
    public void refuses_the_native_mode_without_a_graalvm(@TempDir Path elsewhere) {
        assertThatThrownBy(() -> new Make("build.jenesis.Project", Map.of("make.root", root.toString(),
                "make.global", "",
                "make.mode", "native",
                "make.graalvm", elsewhere.toString())).run("build"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(elsewhere.toString())
                .hasMessageContaining("jenesis.make.graalvm");
    }

    @Test
    public void refuses_the_native_mode_on_a_jdk_of_another_release_than_its_graalvm() throws IOException {
        Files.writeString(graalvm.resolve("release"), "JAVA_VERSION=\"24.0.2\"\n");

        assertThatThrownBy(() -> new Make("build.jenesis.Project", Map.of("make.root", root.toString(),
                "make.global", "",
                "make.mode", "native",
                "make.graalvm", graalvm.toString())).run("build"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(graalvm.toString())
                .hasMessageContaining("24.0.2")
                .hasMessageContaining(System.getProperty("java.home"));
    }

    @Test
    public void reads_the_native_mode() {
        assertThat(new Make("build.jenesis.Project", Map.of("make.root", root.toString(),
                "make.global", "",
                "make.mode", "native")).mode()).isEqualTo(Make.Mode.NATIVE);
    }

    private static void script(Path file, String content) throws IOException {
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private static Path engine() throws URISyntaxException {
        return Path.of(Make.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static SequencedSet<String> classes(Predicate<Class<?>> filter) throws Exception {
        Path location = engine();
        SequencedSet<String> classes = new TreeSet<>();
        int found = 0;
        try (FileSystem jar = Files.isDirectory(location) ? null : FileSystems.newFileSystem(location)) {
            Path folder = jar == null ? location : jar.getPath("/");
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.filter(file -> file.toString().endsWith(".class")).toList()) {
                    String name = folder.relativize(file).toString();
                    if (name.equals("module-info.class") || name.startsWith("META-INF")) {
                        continue;
                    }
                    name = name.substring(0, name.length() - ".class".length())
                            .replace(file.getFileSystem().getSeparator(), ".");
                    found++;
                    if (filter.test(Class.forName(name, false, Make.class.getClassLoader()))) {
                        classes.add(name);
                    }
                }
            }
        }
        assertThat(found).as("the classes of the engine are found at " + location).isPositive();
        return classes;
    }

    private void make(String... selectors) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                engine().toString(),
                Make.class.getName(),
                "-Djenesis.make.mode=native",
                "-Djenesis.make.graalvm=" + graalvm,
                "-Djenesis.make.global="));
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
