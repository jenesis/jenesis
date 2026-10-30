package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;

import static org.assertj.core.api.Assertions.assertThat;

public class HermeticRunTest {

    private static final List<String> OFFLINE = List.of("-Djenesis.repository.offline=true",
            "-Djenesis.dependency.pin=strict");

    @TempDir
    private Path root, machine;

    @ParameterizedTest
    @ValueSource(strings = {"maven", "modular", "modular_to_maven"})
    public void a_pinned_project_builds_and_tests_offline_from_a_fresh_checkout(String layout) throws IOException {
        project(layout);
        List<String> settings = List.of("-Djenesis.project.layout=" + layout);
        make("the project is resolved and pinned once while the network is reachable", settings, "pin");
        make("pinning again reads what the first pin wrote", settings, "pin");
        fresh();
        make("a fresh checkout builds and runs its tests from what the machine keeps, every artifact pinned",
                Stream.concat(settings.stream(), OFFLINE.stream()).toList());
        assertThat(tested()).contains("1 tests successful");
    }

    @ParameterizedTest
    @ValueSource(strings = {"maven", "modular"})
    public void a_project_keeping_no_local_repository_builds_offline_from_its_own_folder(String layout)
            throws IOException {
        project(layout);
        List<String> settings = List.of("-Djenesis.project.layout=" + layout,
                "-Djenesis.maven.local=",
                "-Djenesis.module.local=");
        make("the project is resolved and pinned once while the network is reachable", settings, "pin");
        make("a build from the pins keeps every pinned artifact in the project's own folder", settings, "pin");
        delete(root.resolve("target"));
        make("a checkout whose .jenesis folder was kept builds and runs its tests with the network refused",
                Stream.concat(settings.stream(), OFFLINE.stream()).toList());
        assertThat(tested()).contains("1 tests successful");
        try (Stream<Path> kept = Files.walk(machine).filter(Files::isRegularFile)) {
            assertThat(kept.toList())
                    .as("with both local repositories switched off, nothing is kept outside the project")
                    .isEmpty();
        }
    }

    @Test
    public void a_project_aliasing_the_modules_of_its_tests_never_asks_the_module_index() throws IOException {
        write("sources/module-info.java", "module sample {\n    exports sample;\n}\n");
        write("sources/sample/Sample.java", sample("sample"));
        write("tests/module-info.java", """
                /**
                 * @jenesis.test sample
                 * @jenesis.alias org.junit.jupiter.api org.junit.jupiter/junit-jupiter-api
                 * @jenesis.alias org.junit.jupiter.engine org.junit.jupiter/junit-jupiter-engine
                 * @jenesis.alias org.junit.platform.console org.junit.platform/junit-platform-console
                 * @jenesis.bom pin-sample.properties
                 */
                open module sample.test {
                    requires sample;
                    requires org.junit.jupiter.api;
                }
                """);
        write("tests/sample/test/SampleTest.java", test("sample.test", "sample.Sample"));
        write("build.jenesis/pin-sample.properties", "org.junit.jupiter/junit-jupiter-api=5.11.3\n");
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        List<String> settings = List.of("-Djenesis.project.layout=modular_to_maven",
                "-Djenesis.module.uri=http://127.0.0.1:" + closed + "/",
                "-Djenesis.pin.file=" + root.resolve("build.jenesis/pin-sample.properties"),
                "-Djenesis.pin.groups=*");
        make("the test framework's own modules resolve as the aliases name them, so no module name is looked up",
                settings,
                "pin");
        make("pinning again reads the bill of materials the first pin wrote", settings, "pin");
        assertThat(Files.readAllLines(root.resolve("build.jenesis/pin-sample.properties")))
                .as("an aliased module is pinned by the coordinate its alias names, never by its module name")
                .filteredOn(line -> !line.isBlank() && !line.startsWith("#"))
                .allMatch(line -> line.substring(0, line.indexOf('=')).contains("/"))
                .anyMatch(line -> line.startsWith("org.junit.platform/junit-platform-console="));
        fresh();
        make("a fresh checkout of the aliased project builds offline, every artifact pinned",
                Stream.concat(settings.stream(), OFFLINE.stream()).toList());
        assertThat(tested()).contains("1 tests successful");
    }

    private void write(String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String sample(String packageName) {
        return """
                package %s;

                public class Sample {
                    public static int answer() {
                        return 42;
                    }
                }
                """.formatted(packageName);
    }

    private static String test(String packageName, String sample) {
        return """
                package %s;

                public class SampleTest {
                    @org.junit.jupiter.api.Test
                    public void answers() {
                        if (%s.answer() != 42) {
                            throw new AssertionError();
                        }
                    }
                }
                """.formatted(packageName, sample);
    }

    private void make(String reason, List<String> settings, String... selectors) throws IOException {
        List<String> arguments = new ArrayList<>(List.of("-Djenesis.make.root=" + root,
                "-Djenesis.make.global=",
                "-Djenesis.maven.local=" + Files.createDirectories(machine.resolve("m2")),
                "-Djenesis.module.local=" + Files.createDirectories(machine.resolve("modules")),
                "-Djenesis.print.progress=false"));
        arguments.addAll(settings);
        arguments.addAll(List.of(selectors));
        StringWriter out = new StringWriter(), err = new StringWriter();
        int code = ToolProvider.findFirst("jenesis-make").orElseThrow().run(new PrintWriter(out),
                new PrintWriter(err),
                arguments.toArray(String[]::new));
        assertThat(code).as("%s%n%s%n%s", reason, out, err).isZero();
    }

    private void project(String layout) throws IOException {
        if (layout.equals("maven")) {
            Files.writeString(root.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>sample</groupId>
                        <artifactId>sample</artifactId>
                        <version>1.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.junit.jupiter</groupId>
                                <artifactId>junit-jupiter-api</artifactId>
                                <version>5.11.3</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """);
            write("src/main/java/sample/Sample.java", sample("sample"));
            write("src/test/java/sample/SampleTest.java", test("sample", "Sample"));
        } else {
            write("sources/module-info.java", "module sample {\n    exports sample;\n}\n");
            write("sources/sample/Sample.java", sample("sample"));
            write("tests/module-info.java", """
                    /**
                     * @jenesis.test sample
                     */
                    open module sample.test {
                        requires sample;
                        requires org.junit.jupiter.api;
                    }
                    """);
            write("tests/sample/test/SampleTest.java", test("sample.test", "sample.Sample"));
        }
    }

    private void fresh() throws IOException {
        delete(root.resolve("target"));
        delete(root.resolve(".jenesis"));
    }

    private static void delete(Path location) throws IOException {
        if (Files.exists(location)) {
            try (Stream<Path> files = Files.walk(location)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private String tested() throws IOException {
        try (Stream<Path> files = Files.walk(root.resolve("target"))) {
            Path output = files.filter(file -> file.endsWith(Path.of("test", "executed", "supplement", "output")))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(output);
        }
    }
}
