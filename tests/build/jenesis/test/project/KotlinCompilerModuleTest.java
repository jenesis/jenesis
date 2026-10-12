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
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.KotlinCompilerModule;
import build.jenesis.step.Dependencies;
import java.lang.classfile.Attributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KotlinCompilerModuleTest {

    private static final String KOTLIN_VERSION = "2.2.0", INCREMENTAL_VERSION = "2.3.21";

    @TempDir
    private Path root, project, tests, testRoot;

    @Test
    public void compiles_a_kotlin_source_against_a_downloaded_compiler() throws IOException, ReflectiveOperationException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample {
                    fun greet(): String = "Hello world!"
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path classes = root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        Path sampleClass = classes.resolve("sample/Sample.class");
        assertThat(sampleClass).isNotEmptyFile();

        Path artifacts = root
                .resolve("kotlin")
                .resolve("dependencies")
                .resolve("resolve")
                .resolve("output");
        URL[] runtimeUrls = collectJarUrls(artifacts).stream().toArray(URL[]::new);
        URL[] urls = Stream.concat(
                        Stream.of(classes.toUri().toURL()),
                        Arrays.stream(runtimeUrls))
                .toArray(URL[]::new);
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            Class<?> sample = loader.loadClass("sample.Sample");
            Object instance = sample.getConstructor().newInstance();
            Object greeting = sample.getMethod("greet").invoke(instance);
            assertThat(greeting).isEqualTo("Hello world!");
        }
    }

    @Test
    public void includeResources_false_excludes_non_kotlin_non_java_files_from_output() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample
                """);
        Files.writeString(sampleDir.resolve("app.properties"), "key=value");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE)))
                        .includeResources(false),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path classes = root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(classes.resolve("sample/Sample.class")).isNotEmptyFile();
        assertThat(classes.resolve("sample/app.properties"))
                .as("resources are excluded when includeResources(false)")
                .doesNotExist();
    }

    @Test
    public void skips_module_info_so_kotlinc_does_not_parse_it() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample
                """);
        Files.writeString(project.resolve(BuildStep.SOURCES + "module-info.java"), """
                module sample {
                    exports sample;
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path classes = root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(classes.resolve("sample/Sample.class"))
                .as("kotlinc compiled the Kotlin source while ignoring module-info.java")
                .isNotEmptyFile();
        assertThat(classes.resolve("module-info.class"))
                .as("kotlinc must not parse or emit module-info.java; javac owns it")
                .doesNotExist();
    }

    @Test
    public void compiles_a_kotlin_source_below_meta_inf_versions_for_its_release_into_the_versioned_classes() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample { fun name(): String = "base" }
                """);
        Files.writeString(sampleDir.resolve("Greeting.kt"), """
                package sample
                internal fun greeting(): String = "hello"
                """);
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "META-INF/versions/17/sample"))
                .resolve("Sample.kt"), """
                package sample
                class Sample { fun name(): String = greeting() + " 17" }
                """);
        Path processFolder = Files.createDirectories(project.resolve("process"));
        SequencedProperties javacProperties = new SequencedProperties();
        javacProperties.setProperty("--release", "11");
        javacProperties.store(processFolder.resolve("javac.properties"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path output = root.resolve("kotlin").resolve(KotlinCompilerModule.CLASSES).resolve("output");
        Path base = output.resolve(BuildStep.CLASSES + "sample/Sample.class"),
                versioned = output.resolve(BuildStep.CLASSES + "META-INF/versions/17/sample/Sample.class");
        assertThat(majorVersion(base)).as("the base is compiled for the module's release").isEqualTo(55);
        assertThat(majorVersion(versioned)).as("the overlay is compiled for its own release").isEqualTo(61);
        assertThat(output.resolve("manifest.mf")).content().contains("Multi-Release: true");
    }

    @Test
    public void the_tests_of_a_module_see_its_internal_declarations() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Shout.kt"), """
                package sample
                internal fun shout(text: String): String = text.uppercase()
                """);
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("kotlin", new KotlinCompilerModule(
                Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))), "project", standardLibrary(executor, project, "project"));
        executor.execute();
        Path classes = root.resolve("kotlin").resolve(KotlinCompilerModule.CLASSES).resolve("output").resolve(BuildStep.CLASSES);
        Path jar = Files.createDirectories(tests.resolve("resolved")).resolve("sample.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar));
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        versions.store(tests.resolve(BuildStep.VERSIONS));
        Files.writeString(Files.createDirectories(tests.resolve(BuildStep.SOURCES + "sample")).resolve("ShoutTest.kt"), """
                package sample
                class ShoutTest { fun shouts(): String = shout("hi") }
                """);
        Files.writeString(tests.resolve(BuildStep.MODULE), "test=sample\n");
        Files.writeString(tests.resolve(BuildStep.METADATA), "project=demo\nartifact=sample\nversion=1.0.0\n");
        Files.writeString(tests.resolve(BuildStep.DEPENDENCIES), "main/compile/maven/demo/sample/1.0.0=resolved/sample.jar\n");
        Files.writeString(tests.resolve(Dependencies.GRAPH),
                "vertex/main/compile/maven/demo/sample=1.0.0\\t\\tfalse\\ttrue\n");
        BuildExecutor testExecutor = BuildExecutor.of(testRoot,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        testExecutor.addSource("tests", tests);
        testExecutor.addModule("kotlin", new KotlinCompilerModule(
                Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))), "tests", standardLibrary(testExecutor, tests, "tests"));
        testExecutor.execute();
        assertThat(testRoot.resolve("kotlin").resolve(KotlinCompilerModule.CLASSES).resolve("output")
                .resolve(BuildStep.CLASSES + "sample/ShoutTest.class"))
                .as("the tested jar is a friend of the test half, so its internal function is visible")
                .isNotEmptyFile();
    }

    @Test
    public void compiles_against_the_declared_dependencies_and_not_the_compiler_runtime() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt"), """
                package sample
                import kotlinx.coroutines.runBlocking
                fun answer(): Int = runBlocking { 42 }
                """);
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("kotlin", new KotlinCompilerModule(
                Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        assertThatThrownBy(executor::execute)
                .as("the coroutines library kotlinc runs on is no dependency the module declared")
                .hasStackTraceContaining("kotlinx");
    }

    private static int majorVersion(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }

    @Test
    public void picks_up_release_from_upstream_javac_properties() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample
                """);
        Path processFolder = Files.createDirectories(project.resolve("process"));
        SequencedProperties javacProperties = new SequencedProperties();
        javacProperties.setProperty("--release", "11");
        javacProperties.store(processFolder.resolve("javac.properties"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path classes = root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        Path sampleClass = classes.resolve("sample/Sample.class");
        assertThat(sampleClass).isNotEmptyFile();
        byte[] bytes = Files.readAllBytes(sampleClass);
        int majorVersion = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
        assertThat(majorVersion)
                .as("--release=11 propagated to -jvm-target so the .class file carries major version 55 (Java 11)")
                .isEqualTo(55);
    }

    @Test
    public void release_8_reaches_kotlin_as_the_jvm_target_1_8_it_names_java_8_by() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample
                """);
        SequencedProperties javacProperties = new SequencedProperties();
        javacProperties.setProperty("--release", "8");
        javacProperties.store(Files.createDirectories(project.resolve("process")).resolve("javac.properties"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        byte[] bytes = Files.readAllBytes(root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES)
                .resolve("sample/Sample.class"));
        assertThat(((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF))
                .as("kotlinc refuses the target 8 and takes Java 8 as 1.8, which emits major version 52")
                .isEqualTo(52);
    }

    @Test
    public void recompiles_incrementally_only_what_a_change_reaches() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-build-tools-impl", INCREMENTAL_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Untouched.kt"), """
                package sample
                class Untouched { fun name(): String = "untouched" }
                """);
        Files.writeString(sampleDir.resolve("Edited.kt"), """
                package sample
                class Edited { fun value(): String = "first" }
                """);
        Files.writeString(sampleDir.resolve("User.kt"), """
                package sample
                class User { fun use(): String = "used " + Edited().value() }
                """);
        Files.writeString(sampleDir.resolve("Removed.kt"), """
                package sample
                class Removed
                """);
        Files.writeString(project.resolve(BuildStep.SOURCES + "sample/data.txt"), "data");
        incremental();
        Path classes = root.resolve("kotlin").resolve(KotlinCompilerModule.CLASSES).resolve("output").resolve(BuildStep.CLASSES);
        FileTime untouched = Files.getLastModifiedTime(classes.resolve("sample/Untouched.class")),
                user = Files.getLastModifiedTime(classes.resolve("sample/User.class"));
        assertThat(classes.resolve("sample/Removed.class")).isNotEmptyFile();

        Files.writeString(sampleDir.resolve("Edited.kt"), """
                package sample
                class Edited { fun value(): String = "second" }
                """);
        Files.delete(sampleDir.resolve("Removed.kt"));
        incremental();
        assertThat(Files.getLastModifiedTime(classes.resolve("sample/Untouched.class")))
                .as("a source the change does not reach keeps the class the previous run compiled")
                .isEqualTo(untouched);
        assertThat(Files.getLastModifiedTime(classes.resolve("sample/User.class")))
                .as("a body changed behind an unchanged signature leaves the class that calls it as it was")
                .isEqualTo(user);
        assertThat(classes.resolve("sample/Removed.class"))
                .as("the class of a removed source is removed with it")
                .doesNotExist();
        assertThat(classes.resolve("sample/data.txt"))
                .as("the resources are placed beside the classes the compiler wrote")
                .hasContent("data");
        assertThat(new String(Files.readAllBytes(classes.resolve("sample/Edited.class")), StandardCharsets.ISO_8859_1))
                .as("the edited source is compiled again")
                .contains("second");

        Files.writeString(sampleDir.resolve("Edited.kt"), """
                package sample
                class Edited { fun value(): Int = 2 }
                """);
        incremental();
        assertThat(Files.getLastModifiedTime(classes.resolve("sample/User.class")))
                .as("a changed signature recompiles the source that uses it")
                .isNotEqualTo(user);
        assertThat(Files.getLastModifiedTime(classes.resolve("sample/Untouched.class"))).isEqualTo(untouched);
    }

    @Test
    public void hands_a_jvm_option_to_the_kotlin_daemon_when_compiling_incrementally() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-build-tools-impl", INCREMENTAL_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt"), """
                package sample
                class Sample
                """);
        SequencedProperties kotlincProperties = new SequencedProperties();
        kotlincProperties.setProperty("-J-Xss4m", "");
        kotlincProperties.store(Files.createDirectories(project.resolve("process")).resolve("kotlinc.properties"));
        incremental();
        List<String> command = Arrays.asList(Files.readString(root
                .resolve("kotlin")
                .resolve("compiled")
                .resolve("supplement")
                .resolve("command")).split(" "));
        assertThat(command.get(command.indexOf("-Xss4m") - 1))
                .as("a -J line configures the JVM of the Kotlin daemon that compiles the module")
                .isEqualTo("--jvm");
        assertThat(root.resolve("kotlin").resolve(KotlinCompilerModule.CLASSES).resolve("output").resolve(BuildStep.CLASSES).resolve("sample/Sample.class"))
                .isNotEmptyFile();
    }

    @Test
    public void compiles_a_kotlin_source_below_meta_inf_versions_in_the_kotlin_daemon_when_compiling_incrementally() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-build-tools-impl", INCREMENTAL_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt"), """
                package sample
                class Sample { fun name(): String = "base" }
                """);
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "META-INF/versions/17/sample"))
                .resolve("Sample.kt"), """
                package sample
                class Sample { fun name(): String = "17" }
                """);
        SequencedProperties javacProperties = new SequencedProperties();
        javacProperties.setProperty("--release", "11");
        javacProperties.store(Files.createDirectories(project.resolve("process")).resolve("javac.properties"));
        incremental();
        Path output = root.resolve("kotlin").resolve(KotlinCompilerModule.CLASSES).resolve("output");
        assertThat(majorVersion(output.resolve(BuildStep.CLASSES + "sample/Sample.class"))).isEqualTo(55);
        assertThat(majorVersion(output.resolve(BuildStep.CLASSES + "META-INF/versions/17/sample/Sample.class")))
                .as("the overlay is compiled for its own release")
                .isEqualTo(61);
        assertThat(output.resolve("manifest.mf")).content().contains("Multi-Release: true");
        assertThat(Files.readString(root.resolve("kotlin").resolve("compiled").resolve("supplement").resolve("command-17")))
                .as("the overlay is compiled in the Kotlin daemon rather than in a JVM of its own")
                .contains("--tool")
                .doesNotContain("K2JVMCompiler");
    }

    private void incremental() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))).incremental(true),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();
    }

    @Test
    public void hands_the_lines_of_process_kotlinc_properties_to_kotlinc_and_its_j_lines_to_the_jvm() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample {
                    fun greet(name: String): String = "Hello " + name
                }
                """);
        SequencedProperties kotlincProperties = new SequencedProperties();
        kotlincProperties.setProperty("-module-name", "custom-name");
        kotlincProperties.setProperty("-java-parameters", "");
        kotlincProperties.setProperty("-api-version", "2.0");
        kotlincProperties.setProperty("-language-version", "2.0");
        kotlincProperties.setProperty("-J-Xss4m", "");
        kotlincProperties.store(Files.createDirectories(project.resolve("process")).resolve("kotlinc.properties"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path classes = root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(classes.resolve("META-INF/custom-name.kotlin_module"))
                .as("-module-name reached kotlinc, which names the module file after it")
                .isNotEmptyFile();
        ClassModel sample = ClassFile.of().parse(classes.resolve("sample/Sample.class"));
        assertThat(sample.methods().stream()
                .filter(method -> method.methodName().equalsString("greet"))
                .flatMap(method -> method.findAttribute(Attributes.methodParameters()).stream()))
                .as("-java-parameters reached kotlinc, which then writes the MethodParameters attribute")
                .isNotEmpty();
        List<String> command = Arrays.asList(Files.readString(root
                .resolve("kotlin")
                .resolve("compiled")
                .resolve("supplement")
                .resolve("command")).split(" "));
        assertThat(command.indexOf("-Xss4m"))
                .as("a -J line goes to the JVM that runs kotlinc, ahead of its main class")
                .isNotNegative()
                .isLessThan(command.indexOf("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"));
        assertThat(command.indexOf("-api-version"))
                .as("a compiler argument follows kotlinc's main class")
                .isGreaterThan(command.indexOf("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler"));
    }

    @Test
    public void kotlin_can_reference_java_sources_supplied_to_the_same_step() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Greeter.java"), """
                package sample;
                public class Greeter {
                    public String hello() { return "Hello"; }
                }
                """);
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                class Sample {
                    fun greet(): String = Greeter().hello() + " from Kotlin"
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path classes = root
                .resolve("kotlin")
                .resolve(KotlinCompilerModule.CLASSES)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(classes.resolve("sample/Sample.class"))
                .as("Sample.kt resolved the Greeter type from the .java source companion and compiled")
                .isNotEmptyFile();
    }

    @Test
    public void downloads_the_full_compiler_dependency_set_from_the_kotlin_pom() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("kotlinc/kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable", KOTLIN_VERSION);
        properties.store(project.resolve(BuildStep.VERSIONS));
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), "package sample\nclass Sample\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                        Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))),
                "project", standardLibrary(executor, project, "project"));
        executor.execute();

        Path artifacts = root
                .resolve("kotlin")
                .resolve("dependencies")
                .resolve("resolve")
                .resolve("output");
        List<String> names = listCoordinates(artifacts);
        assertThat(names).anyMatch(name -> name.contains("kotlin-compiler-embeddable"));
        assertThat(names).anyMatch(name -> name.contains("kotlin-stdlib"));
        assertThat(names).anyMatch(name -> name.contains("kotlinx-coroutines-core"));
        assertThat(names).anyMatch(name -> name.contains("annotations"));
    }

    @Test
    public void requires_step_picks_module_coordinate_when_module_resolver_available() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of(),
                        Map.of("module", (_, _, _, _, _, _) -> new Resolver.Resolution(new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()))),
                "project");
        executor.execute("kotlin/" + "required");

        Path requiredOutput = root.resolve("kotlin").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("kotlinc/runtime/module/kotlin.compiler.embeddable");
        assertThat(requiredOutput.resolve(BuildStep.VERSIONS)).doesNotExist();
    }

    @Test
    public void requires_step_picks_maven_coordinate_when_only_maven_resolver_available() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of(),
                        Map.of("maven", (_, _, _, _, _, _) -> new Resolver.Resolution(new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()))),
                "project");
        executor.execute("kotlin/" + "required");

        Path requiredOutput = root.resolve("kotlin").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("kotlinc/runtime/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable/RELEASE");
        assertThat(requiredOutput.resolve(BuildStep.VERSIONS)).doesNotExist();
    }

    @Test
    public void requires_step_prefers_maven_over_module_when_both_available() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of(),
                        Map.of(
                                "module", (_, _, _, _, _, _) -> new Resolver.Resolution(new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()),
                                "maven", (_, _, _, _, _, _) -> new Resolver.Resolution(new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()))),
                "project");
        executor.execute("kotlin/" + "required");

        Path requiredOutput = root.resolve("kotlin").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .singleElement()
                .satisfies(name -> assertThat(name).startsWith("kotlinc/runtime/maven/"));
    }

    @Test
    public void requires_step_fails_when_no_supported_prefix_is_registered() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of(),
                        Map.of("vendor", (_, _, _, _, _, _) -> new Resolver.Resolution(new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()))),
                "project");

        assertThatThrownBy(() -> executor.execute("kotlin/" + "required"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("No suitable resolver");
    }

    @Test
    public void tool_emits_an_independent_resolution_trail() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(Map.of("maven", files()), Map.of("maven", Resolver.identity()))
                        .tool("custom"),
                "project");
        executor.execute("kotlin/dependencies");

        Path resolvedOutput = root.resolve("kotlin").resolve("dependencies").resolve("resolve").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(resolvedOutput.resolve(BuildStep.DEPENDENCIES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("custom/runtime/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable/RELEASE");
    }

    @Test
    public void exposes_resolved_alongside_classes_so_coordinates_reach_the_pin_stage() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "kotlin",
                new KotlinCompilerModule(
                        Map.of(),
                        Map.of("maven", (_, _, _, _, _, _) -> new Resolver.Resolution(new LinkedHashMap<>(), List.of(), new LinkedHashMap<>()))),
                "project");
        SequencedMap<String, Path> outputs = executor.execute();

        assertThat(outputs).containsKeys("kotlin/classes", "kotlin/artifacts");
    }

    private static String standardLibrary(BuildExecutor executor, Path folder, String source) throws IOException {
        Path file = folder.resolve(BuildStep.REQUIRES);
        SequencedProperties requires = Files.exists(file) ? SequencedProperties.ofFiles(file) : new SequencedProperties();
        requires.setProperty("main/compile/maven/org.jetbrains.kotlin/kotlin-stdlib/" + KOTLIN_VERSION, "");
        requires.store(file);
        executor.addModule(source + "-libraries", new Dependencies(
                Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))).group("main"), source);
        return source + "-libraries";
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

    private Repository files() {
        return (_, coordinate, _) -> {
            Path file = Files.write(
                    Files.createDirectories(root.resolve("served")).resolve(coordinate.replace('/', '-') + ".jar"),
                    coordinate.getBytes(StandardCharsets.UTF_8));
            return Optional.of(RepositoryItem.ofFile(file));
        };
    }

    private static List<URL> collectJarUrls(Path folder) throws IOException {
        List<URL> urls = new ArrayList<>();
        for (Path jar : Dependencies.all(folder)) {
            urls.add(jar.toUri().toURL());
        }
        return urls;
    }

    private static List<String> listCoordinates(Path folder) throws IOException {
        return new ArrayList<>(SequencedProperties
                .ofFiles(folder.resolve(BuildStep.DEPENDENCIES))
                .stringPropertyNames());
    }
}
