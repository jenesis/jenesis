package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Language;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.project.InferredCompilerChainModule;
import build.jenesis.project.InferredDocumentationChainModule;
import build.jenesis.project.InternalModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LanguageTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    private static Path shared;

    private static Path jenesisJar, plugin;

    @TempDir(cleanup = CleanupMode.NEVER)
    private Path root, project;

    @BeforeAll
    public static void writePlugin() throws Exception {
        Path location = Path.of(BuildExecutor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
            jenesisJar = shared.resolve("build.jenesis.jar");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jenesisJar));
                 Stream<Path> walk = Files.walk(location)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    jar.putNextEntry(new JarEntry(location.relativize(file).toString().replace(File.separatorChar, '/')));
                    Files.copy(file, jar);
                    jar.closeEntry();
                }
            }
        } else {
            jenesisJar = location;
        }
        plugin = Files.createDirectories(shared.resolve("greet"));
        Files.writeString(plugin.resolve("module-info.java"), """
                module sample.greet {
                    requires build.jenesis;
                    requires java.compiler;
                    provides build.jenesis.LanguageFactory with sample.greet.Factory;
                }
                """);
        Path sources = Files.createDirectories(plugin.resolve("sample/greet"));
        Files.writeString(sources.resolve("Factory.java"), """
                package sample.greet;

                import build.jenesis.Environment;
                import build.jenesis.Permits;
                import build.jenesis.Repository;
                import build.jenesis.Resolver;
                import build.jenesis.Language;
                import build.jenesis.LanguageFactory;
                import java.util.LinkedHashSet;
                import java.util.List;
                import java.util.Map;
                import java.util.SequencedMap;

                public class Factory implements LanguageFactory {

                    @Override
                    public Language create(Environment environment,
                                           SequencedMap<String, String> properties,
                                           Permits permits,
                                           String group,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers) {
                        return new Language(new LinkedHashSet<>(List.of(properties.get("extension"))),
                                Language.Order.valueOf(properties.get("order")),
                                (executor, inherited) -> {
                                    executor.addStep("tool", new Tool(group));
                                    executor.addStep("compile", new Compile(), inherited.sequencedKeySet());
                                },
                                (executor, inherited) -> executor.addStep("document", new Document(), inherited.sequencedKeySet()));
                    }
                }
                """);
        Files.writeString(sources.resolve("Compile.java"), """
                package sample.greet;

                import build.jenesis.BuildStep;
                import build.jenesis.BuildStepArgument;
                import build.jenesis.BuildStepContext;
                import build.jenesis.BuildStepResult;
                import java.io.IOException;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.ArrayList;
                import java.util.List;
                import java.util.SequencedMap;
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.CompletionStage;
                import java.util.concurrent.Executor;
                import java.util.stream.Stream;
                import javax.tools.ToolProvider;

                record Compile() implements BuildStep {

                    @Override
                    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                                  BuildStepContext context,
                                                                  SequencedMap<String, BuildStepArgument> arguments)
                            throws IOException {
                        Path generated = Files.createDirectories(context.supplement().resolve("generated"));
                        Path classes = Files.createDirectories(context.next().resolve(BuildStep.CLASSES));
                        List<String> files = new ArrayList<>(), seen = new ArrayList<>();
                        for (BuildStepArgument argument : arguments.values()) {
                            Path compiled = argument.folder().resolve(BuildStep.CLASSES);
                            if (Files.isDirectory(compiled)) {
                                try (Stream<Path> walk = Files.walk(compiled)) {
                                    walk.filter(Files::isRegularFile)
                                            .forEach(file -> seen.add(compiled.relativize(file).toString().replace('\\\\', '/')));
                                }
                            }
                            Path sources = argument.folder().resolve(BuildStep.SOURCES);
                            if (!Files.isDirectory(sources)) {
                                continue;
                            }
                            try (Stream<Path> walk = Files.walk(sources)) {
                                for (Path greeting : walk.filter(file -> file.toString().endsWith(".greet")
                                        || file.toString().endsWith(".kt")).toList()) {
                                    String name = greeting.getFileName().toString().replaceAll("\\\\.(greet|kt)$", "");
                                    Path source = Files.createDirectories(generated.resolve(sources.relativize(greeting.getParent())))
                                            .resolve(name + ".java");
                                    Files.writeString(source, "package sample; public class " + name
                                            + " { public static String text() { return \\\"" + Files.readString(greeting) + "\\\"; } }");
                                    files.add(source.toString());
                                }
                            }
                        }
                        Files.write(context.next().resolve("seen.txt"), seen);
                        List<String> options = new ArrayList<>(List.of("-d", classes.toString()));
                        options.addAll(files);
                        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, options.toArray(String[]::new)) != 0) {
                            throw new IllegalStateException("Cannot compile " + files);
                        }
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }
                }
                """);
        Files.writeString(sources.resolve("Tool.java"), """
                package sample.greet;

                import build.jenesis.BuildStep;
                import build.jenesis.BuildStepArgument;
                import build.jenesis.BuildStepContext;
                import build.jenesis.BuildStepResult;
                import java.io.IOException;
                import java.nio.file.Files;
                import java.util.SequencedMap;
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.CompletionStage;
                import java.util.concurrent.Executor;

                record Tool(String group) implements BuildStep {

                    @Override
                    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                                  BuildStepContext context,
                                                                  SequencedMap<String, BuildStepArgument> arguments)
                            throws IOException {
                        Files.writeString(Files.createDirectories(context.next().resolve("resolved")).resolve("tool.jar"), "tool");
                        Files.writeString(context.next().resolve(BuildStep.DEPENDENCIES),
                                group + "\\\\:tool/runtime/maven/sample/tool=resolved/tool.jar SHA-256/0123\\n");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }
                }
                """);
        Files.writeString(sources.resolve("Document.java"), """
                package sample.greet;

                import build.jenesis.BuildStep;
                import build.jenesis.BuildStepArgument;
                import build.jenesis.BuildStepContext;
                import build.jenesis.BuildStepResult;
                import java.io.IOException;
                import java.nio.file.Files;
                import java.util.SequencedMap;
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.CompletionStage;
                import java.util.concurrent.Executor;

                record Document() implements BuildStep {

                    @Override
                    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                                  BuildStepContext context,
                                                                  SequencedMap<String, BuildStepArgument> arguments)
                            throws IOException {
                        Files.writeString(Files.createDirectories(context.next().resolve("javadoc")).resolve("index.html"), "greet");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }
                }
                """);
    }

    @Test
    public void compiles_a_language_before_javac_so_that_java_reads_its_classes() throws IOException {
        Path sources = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Greeting.greet"), "Hello");
        Files.writeString(sources.resolve("Caller.java"), """
                package sample;
                public class Caller {
                    public String call() { return Greeting.text(); }
                }
                """);
        Files.writeString(sources.resolve("data.txt"), "data");

        SequencedMap<String, Path> result = compile(Language.Order.BEFORE_JAVAC);

        Path compile = root.resolve("chain").resolve(InferredCompilerChainModule.COMPILE);
        assertThat(compile.resolve("greet/compile/output").resolve(BuildStep.CLASSES).resolve("sample/Greeting.class"))
                .isNotEmptyFile();
        assertThat(compile.resolve(InferredCompilerChainModule.JAVAC).resolve("output").resolve(BuildStep.CLASSES)
                .resolve("sample/Caller.class"))
                .as("javac compiles against the classes the language wrote")
                .isNotEmptyFile();
        Path resources = compile.resolve(InferredCompilerChainModule.RESOURCE).resolve("output").resolve(BuildStep.CLASSES);
        assertThat(resources.resolve("sample/data.txt")).hasContent("data");
        assertThat(resources.resolve("sample/Greeting.greet"))
                .as("a source of the language is no resource")
                .doesNotExist();
        assertThat(result).doesNotContainKey("chain/languages");
    }

    @Test
    public void hands_on_what_a_language_resolved_its_compiler_from_so_that_it_is_pinned() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Greeting.greet"),
                "Hello");

        SequencedMap<String, Path> result = compile(Language.Order.BEFORE_JAVAC);

        Path resolved = result.get("chain/" + InferredCompilerChainModule.RESOLVED);
        assertThat(resolved).as("the compile chain exposes the closures its languages resolved").isNotNull();
        SequencedProperties dependencies = SequencedProperties.ofFiles(resolved.resolve(BuildStep.DEPENDENCIES));
        String value = dependencies.getProperty("greet:tool/runtime/maven/sample/tool");
        assertThat(value).endsWith(" SHA-256/0123");
        assertThat(resolved.resolve(value.substring(0, value.indexOf(' ')))).hasContent("tool");
    }

    @Test
    public void hands_on_the_closure_a_language_plugin_was_loaded_from_so_that_it_is_pinned() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Greeting.greet"),
                "Hello");

        SequencedMap<String, Path> result = compile(Language.Order.BEFORE_JAVAC);

        Path resolved = result.get("chain/" + InferredCompilerChainModule.RESOLVED);
        SequencedProperties dependencies = SequencedProperties.ofFiles(resolved.resolve(BuildStep.DEPENDENCIES));
        List<String> closure = dependencies.stringPropertyNames().stream()
                .filter(key -> key.startsWith("greet/"))
                .toList();
        assertThat(closure)
                .as("the plugin's own closure is pinned beside what its compiler resolved, among %s",
                        dependencies.stringPropertyNames())
                .isNotEmpty();
        assertThat(resolved.resolve(dependencies.getProperty(closure.getFirst()).split(" ", 2)[0])).isNotEmptyFile();
    }

    @Test
    public void compiles_a_language_after_javac_so_that_it_reads_the_classes_of_javac() throws IOException {
        Path sources = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Base.java"), "package sample; public class Base {}");
        Files.writeString(sources.resolve("Greeting.greet"), "Hello");

        compile(Language.Order.AFTER_JAVAC);

        assertThat(root.resolve("chain").resolve(InferredCompilerChainModule.COMPILE).resolve("greet/compile/output")
                .resolve("seen.txt"))
                .as("the language is handed what javac compiled")
                .content()
                .contains("sample/Base.class");
    }

    @Test
    public void compiles_the_sources_of_an_extension_it_claims_in_place_of_a_language_switched_off() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Greeting.kt"),
                "Hello");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("chain",
                new InferredCompilerChainModule(Map.of(), Map.of())
                        .kotlin(false)
                        .languages(new LinkedHashMap<>(Map.of("kotlin", greet(Language.Order.BEFORE_JAVAC, ".kt")))),
                "project");
        executor.execute();

        Path compile = root.resolve("chain").resolve(InferredCompilerChainModule.COMPILE);
        assertThat(compile.resolve("kotlin/compile/output").resolve(BuildStep.CLASSES).resolve("sample/Greeting.class"))
                .isNotEmptyFile();
    }

    @Test
    public void refuses_a_language_plugin_named_like_a_language_the_build_compiles_by_itself() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Greeting.kt"),
                "Hello");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("chain",
                new InferredCompilerChainModule(Map.of(), Map.of())
                        .languages(new LinkedHashMap<>(Map.of("kotlin", greet(Language.Order.BEFORE_JAVAC, ".kt")))),
                "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kotlin")
                .hasMessageContaining("jenesis.plugins.properties");
    }

    @Test
    public void leaves_a_language_out_of_a_module_without_its_sources() throws IOException {
        Path sources = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Only.java"), "package sample; public class Only {}");

        compile(Language.Order.BEFORE_JAVAC);

        assertThat(root.resolve("chain").resolve(InferredCompilerChainModule.COMPILE).resolve("greet")).doesNotExist();
    }

    @Test
    public void documents_a_language_alone_at_the_root_of_the_documentation() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Greeting.greet"),
                "Hello");

        Path javadoc = document().resolve("javadoc");

        assertThat(javadoc.resolve("index.html")).hasContent("greet");
    }

    @Test
    public void documents_a_language_beside_java_in_a_folder_of_its_own() throws IOException {
        Path sources = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Greeting.greet"), "Hello");
        Files.writeString(sources.resolve("Caller.java"), "package sample; /** Calls. */ public class Caller {}");

        Path javadoc = document().resolve("javadoc");

        assertThat(javadoc.resolve("greet/index.html")).hasContent("greet");
        assertThat(javadoc.resolve("sample/Caller.html")).isNotEmptyFile();
    }

    private SequencedMap<String, Path> compile(Language.Order order) throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("chain",
                new InferredCompilerChainModule(Map.of(), Map.of())
                        .languages(new LinkedHashMap<>(Map.of("greet", greet(order, ".greet")))),
                "project");
        return executor.execute();
    }

    private Path document() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("doc",
                new InferredDocumentationChainModule(new LinkedHashSet<>(), Map.of(), Map.of())
                        .languages(new LinkedHashMap<>(Map.of("greet", greet(Language.Order.BEFORE_JAVAC, ".greet")))),
                "project");
        return executor.execute().get("doc/document/aggregate");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }

    private static InternalModule greet(Language.Order order, String extension) {
        return InternalModule.ofEnvironment(Environment.NONE,
                        "module",
                        extension.equals(".kt") ? "kotlin" : "greet",
                        plugin,
                        Map.of("module", jenesis()),
                        Map.of("module", ModularJarResolver.ofEnvironment(Environment.NONE, true)))
                .properties(new LinkedHashMap<>(Map.of("extension", extension, "order", order.name())));
    }

    private static Repository jenesis() {
        return (_, coordinate, _) -> coordinate.equals("build.jenesis") || coordinate.startsWith("build.jenesis/")
                ? Optional.of(RepositoryItem.ofFile(jenesisJar))
                : Optional.empty();
    }
}
