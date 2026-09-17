package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class MakeClosureTest {

    @TempDir
    private Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"Make.java", "Toolchain.java"})
    public void compiles_without_any_other_source_of_this_project(String name) throws IOException {
        Path source = Path.of("sources/build/jenesis").resolve(name);
        Assumptions.assumeTrue(Files.isRegularFile(source), "runs from a source checkout");
        Path isolated = Files.createDirectories(folder.resolve("sources/build/jenesis"));
        Files.copy(source, isolated.resolve(name));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            files.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of(folder.resolve("sources")));
            files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT,
                    List.of(Files.createDirectory(folder.resolve("classes"))));
            boolean compiled = compiler.getTask(null,
                    files,
                    diagnostics,
                    null,
                    null,
                    files.getJavaFileObjects(isolated.resolve(name))).call();
            assertThat(compiled)
                    .as("Make is what a project vendors to bootstrap the tool, and java Make.java compiles"
                            + " whatever it names, as it compiles Toolchain when a version is required:"
                            + " a reference to any other class of this project drags that class and its own"
                            + " closure into every build's first step. "
                            + diagnostics.getDiagnostics())
                    .isTrue();
        }
    }
}
