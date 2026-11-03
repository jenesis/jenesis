package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.AggregateJavadoc;
import build.jenesis.step.Inventory;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AggregateJavadocTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, persistent, source;
    private SequencedMap<String, BuildStepArgument> arguments;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        persistent = Files.createDirectory(root.resolve("persistent"));
        source = Files.createDirectory(root.resolve("source"));
        arguments = new LinkedHashMap<>();
    }

    @Test
    public void documents_every_staged_module_in_one_site_and_one_jar() throws IOException {
        module("greeter", null, Map.of("sample/greeter/Greeter.java", """
                package sample.greeter;
                /** Greets. */
                public class Greeter { }
                """));
        module("app", null, Map.of("sample/app/App.java", """
                package sample.app;
                /** Uses the greeter. */
                public class App {
                    /** @return a greeter */
                    public sample.greeter.Greeter greeter() { return null; }
                }
                """));

        BuildStepResult result = run(aggregate());

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC + "element-list")).hasContent("sample.app\nsample.greeter");
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/app/App.html"))
                .content()
                .as("a type of a sibling module is linked within the same site")
                .contains("../greeter/Greeter.html");
        try (ZipFile jar = new ZipFile(next.resolve(AggregateJavadoc.JAR).toFile())) {
            assertThat(jar.stream().map(ZipEntry::getName)).contains("META-INF/MANIFEST.MF",
                    "index.html",
                    "sample/app/App.html",
                    "sample/greeter/Greeter.html");
            assertThat(jar.stream().map(ZipEntry::getTimeLocal).distinct())
                    .containsExactly(LocalDateTime.of(1980, 2, 1, 0, 0));
        }
    }

    @Test
    public void documents_modules_that_declare_a_module_from_their_own_source_roots() throws IOException {
        module("greeter", "demo.greeter", Map.of(
                "module-info.java", "/** The greeter. */\nmodule demo.greeter { exports sample.greeter; }\n",
                "sample/greeter/Greeter.java", "package sample.greeter;\n/** Greets. */\npublic class Greeter { }\n"));
        module("app", "demo.app", Map.of(
                "module-info.java", "/** The app. */\nmodule demo.app { requires demo.greeter; exports sample.app; }\n",
                "sample/app/App.java", """
                        package sample.app;
                        /** Uses the greeter. */
                        public class App {
                            /** @return a greeter */
                            public sample.greeter.Greeter greeter() { return null; }
                        }
                        """));

        run(aggregate());

        assertThat(next.resolve(Javadoc.JAVADOC + "element-list"))
                .hasContent("module:demo.app\nsample.app\nmodule:demo.greeter\nsample.greeter");
        assertThat(next.resolve(Javadoc.JAVADOC + "demo.app/module-summary.html")).exists();
        assertThat(next.resolve(Javadoc.JAVADOC + "demo.greeter/sample/greeter/Greeter.html")).exists();
    }

    @Test
    public void leaves_out_test_modules_unstaged_modules_and_aggregators() throws IOException {
        module("greeter", null, Map.of("sample/greeter/Greeter.java",
                "package sample.greeter;\n/** Greets. */\npublic class Greeter { }\n"));
        module("greeter-test", null, Map.of("sample/greeter/test/GreeterTest.java",
                "package sample.greeter.test;\n/** Tests. */\npublic class GreeterTest { }\n"),
                Map.of("test", "greeter"));
        module("internal", null, Map.of("sample/internal/Internal.java",
                "package sample.internal;\n/** Internal. */\npublic class Internal { }\n"),
                Map.of("stage", "false"));
        module("parent", null, Map.of(), Map.of("packaging", "pom"));

        run(aggregate());

        assertThat(next.resolve(Javadoc.JAVADOC + "element-list")).hasContent("sample.greeter");
    }

    @Test
    public void hands_javadoc_the_compile_dependencies_of_every_module_but_not_its_siblings() throws IOException {
        Path library = jar(source.resolve("library-1.0.jar"));
        Path sibling = jar(source.resolve("greeter-1.0.jar"));
        Path runtime = jar(source.resolve("runtime-1.0.jar"));
        module("greeter", null, Map.of("sample/greeter/Greeter.java",
                "package sample.greeter;\n/** Greets. */\npublic class Greeter { }\n"),
                Map.of("identity.0", "maven/sample/greeter/1.0"));
        module("app", null, Map.of("sample/app/App.java",
                "package sample.app;\n/** Uses. */\npublic class App { }\n"),
                Map.of("dependency.0", "maven/sample/library/1.0 " + source.relativize(library).toString()
                                .replace(File.separatorChar, '/'),
                        "dependency.0.scope", "compile,runtime",
                        "dependency.1", "maven/sample/greeter/1.0 " + source.relativize(sibling).toString()
                                .replace(File.separatorChar, '/'),
                        "dependency.1.scope", "compile,runtime",
                        "dependency.2", "maven/sample/runtime/1.0 " + source.relativize(runtime).toString()
                                .replace(File.separatorChar, '/'),
                        "dependency.2.scope", "runtime"));

        run(aggregate());

        Function<Path, String> escaped = path -> path.toString().replace("\\", "\\\\");
        assertThat(supplement.resolve("javadoc.args"))
                .content()
                .as("an argument file escapes a backslash, as on Windows a path holds one")
                .contains(escaped.apply(library))
                .doesNotContain(escaped.apply(sibling))
                .doesNotContain(escaped.apply(runtime));
    }

    @Test
    public void refuses_a_staged_module_without_a_sources_jar() throws IOException {
        Path folder = Files.createDirectory(source.resolve("greeter"));
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module-greeter.path", "greeter");
        inventory.store(folder.resolve(Inventory.INVENTORY));
        arguments.put("build-greeter", new BuildStepArgument(folder, Map.of()));

        assertThatThrownBy(() -> run(aggregate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("greeter")
                .hasMessageContaining("jenesis.project.sources=true");
    }

    @Test
    public void documents_nothing_unless_enabled() throws IOException {
        module("greeter", null, Map.of("sample/greeter/Greeter.java",
                "package sample.greeter;\n/** Greets. */\npublic class Greeter { }\n"));

        BuildStepResult result = run(new AggregateJavadoc(ProcessHandler.Factory.TOOL));

        assertThat(result.next()).isTrue();
        assertThat(next).isEmptyDirectory();
    }

    private static AggregateJavadoc aggregate() {
        return new AggregateJavadoc(ProcessHandler.Factory.TOOL).enabled(true);
    }

    private void module(String path, String name, Map<String, String> files) throws IOException {
        module(path, name, files, Map.of());
    }

    private void module(String path, String name, Map<String, String> files, Map<String, String> keys)
            throws IOException {
        String prefix = "module-" + path + ".";
        Path built = Files.createDirectory(source.resolve("build-" + path));
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty(prefix + "path", path);
        inventory.setProperty(prefix + "release", Integer.toString(Runtime.version().feature()));
        if (name != null) {
            inventory.setProperty(prefix + "module", name);
        }
        keys.forEach((key, value) -> inventory.setProperty(prefix + key,
                key.startsWith("dependency.") && !key.endsWith(".scope") ? relocated(value, built) : value));
        inventory.store(built.resolve(Inventory.INVENTORY));
        arguments.put(built.getFileName().toString(), new BuildStepArgument(built, Map.of()));
        if (files.isEmpty()) {
            return;
        }
        Path sourced = Files.createDirectory(source.resolve("source-" + path));
        Path jar = sourced.resolve(path + "-sources.jar");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        SequencedProperties sources = new SequencedProperties();
        sources.setProperty(prefix + "sources.0", jar.getFileName().toString());
        sources.store(sourced.resolve(Inventory.INVENTORY));
        arguments.put(sourced.getFileName().toString(), new BuildStepArgument(sourced, Map.of()));
    }

    private static String relocated(String value, Path folder) {
        int space = value.indexOf(' ');
        return value.substring(0, space + 1) + "../" + value.substring(space + 1);
    }

    private static Path jar(Path jar) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    private BuildStepResult run(AggregateJavadoc step) throws IOException {
        try {
            return step.apply(Runnable::run, new BuildStepContext(previous, next, supplement, persistent), arguments)
                    .toCompletableFuture()
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }
}
