package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Project;

import static org.assertj.core.api.Assertions.assertThat;

public class CompileAvoidanceTest {

    private final List<String> printed = new CopyOnWriteArrayList<>();

    @TempDir
    private Path root;

    @Test
    public void compiles_no_dependent_again_when_only_a_method_body_of_its_dependency_changed() throws IOException {
        library("public static String greeting() { return \"Hello\"; }");
        application();
        build(Map.of("abi.mode", "modular"));
        library("public static String greeting() { return \"Hello\" + \"!\"; }");
        build(Map.of("abi.mode", "modular"));
        assertThat(compiled("library")).isTrue();
        assertThat(compiled("application"))
                .as("the library's abi is unchanged, so the application is not compiled again")
                .isFalse();
    }

    @Test
    public void compiles_a_dependent_again_when_the_abi_of_its_dependency_changed() throws IOException {
        library("public static String greeting() { return \"Hello\"; }");
        application();
        build(Map.of("abi.mode", "modular"));
        library("public static String greeting() { return \"Hello\"; } public static String farewell() { return \"Bye\"; }");
        build(Map.of("abi.mode", "modular"));
        assertThat(compiled("library")).isTrue();
        assertThat(compiled("application")).isTrue();
    }

    @Test
    public void compiles_a_dependent_again_on_any_change_of_its_dependency_without_an_abi() throws IOException {
        library("public static String greeting() { return \"Hello\"; }");
        application();
        build(Map.of());
        library("public static String greeting() { return \"Hello\" + \"!\"; }");
        build(Map.of());
        assertThat(compiled("application")).isTrue();
    }

    private void library(String body) throws IOException {
        Path library = Files.createDirectories(root.resolve("library/demo/library"));
        Files.writeString(root.resolve("library/module-info.java"), """
                module demo.library {
                    exports demo.library;
                }
                """);
        Files.writeString(library.resolve("Library.java"), """
                package demo.library;

                public class Library {

                    %s
                }
                """.formatted(body));
    }

    private void application() throws IOException {
        Path application = Files.createDirectories(root.resolve("application/demo/application"));
        Files.writeString(root.resolve("application/module-info.java"), """
                module demo.application {
                    requires demo.library;
                }
                """);
        Files.writeString(application.resolve("Application.java"), """
                package demo.application;

                public class Application {

                    public static String greeting() {
                        return demo.library.Library.greeting();
                    }
                }
                """);
    }

    private void build(Map<String, String> settings) throws IOException {
        printed.clear();
        Project.ofEnvironment(new Environment(settings).out(printed::add), root).build();
    }

    private boolean compiled(String module) {
        return printed.stream().anyMatch(line -> line.contains("[EXECUTED]")
                && line.contains("build-module-" + module + "/")
                && line.contains("/javac "));
    }
}
