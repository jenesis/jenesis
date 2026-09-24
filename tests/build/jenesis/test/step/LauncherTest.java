package build.jenesis.test.step;

import build.jenesis.PathPlacement;
import module java.base;
import java.util.jar.Attributes;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Launcher;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;

public class LauncherTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, input;

    @BeforeEach
    public void setUp() throws IOException {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
    }

    @Test
    public void shades_the_launcher_and_explodes_the_class_path() throws IOException {
        writeLauncherJar(Files.createDirectory(input.resolve("resolved")).resolve("launcher.jar"));
        writeJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"), "sample/Sample.class");
        writeJar(input.resolve("resolved").resolve("lib.jar"), "lib/Lib.class");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("launcher/runtime/maven/build.jenesis/build.jenesis.launcher", "resolved/launcher.jar");
        index.setProperty("main/runtime/maven/lib", "resolved/lib.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", "sample.Sample");
        application.setProperty("name", "app");
        application.store(input.resolve("launcher.properties"));

        BuildStepResult result = Launcher.ofEnvironment(Environment.NONE, "launcher", PathPlacement.INFERRED).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("resolved/launcher.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("resolved/lib.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path jar = next.resolve(Launcher.LAUNCHER).resolve("app.jar");
        assertThat(jar).isRegularFile();
        SequencedSet<String> entries = entries(jar);
        assertThat(entries)
                .as("the launcher classes are shaded into the root, without its module-info or manifest")
                .contains("build/jenesis/launcher/Launcher.class")
                .doesNotContain("module-info.class", "build/jenesis/launcher/module-info.class");
        assertThat(entries)
                .as("the launcher's own licence and notice come with its classes, nothing else of its META-INF does")
                .contains("META-INF/LICENSE", "META-INF/NOTICE")
                .doesNotContain("META-INF/sbom/build.jenesis.launcher.cdx.json");
        assertThat(entries).contains(
                "application.properties",
                "jars/app.jar/sample/Sample.class",
                "jars/lib.jar/lib/Lib.class");
        assertThat(mainClass(jar)).isEqualTo("build.jenesis.launcher.Launcher");
        Properties descriptor = application(jar);
        assertThat(descriptor.getProperty("mainClass")).isEqualTo("sample.Sample");
        assertThat(descriptor.getProperty("mainModule")).isNull();
        assertThat(descriptor.getProperty("classpath")).isEqualTo("app.jar,lib.jar");
        assertThat(descriptor.getProperty("modulepath")).isEmpty();
    }

    @Test
    public void routes_a_modular_main_onto_the_module_path() throws IOException {
        writeLauncherJar(Files.createDirectory(input.resolve("resolved")).resolve("launcher.jar"));
        compileModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties index = new SequencedProperties();
        index.setProperty("launcher/runtime/maven/build.jenesis/build.jenesis.launcher", "resolved/launcher.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", "sample.Sample");
        application.setProperty("mainModule", "sample");
        application.setProperty("name", "sample");
        application.store(input.resolve("launcher.properties"));

        BuildStepResult result = Launcher.ofEnvironment(Environment.NONE, "launcher", PathPlacement.INFERRED).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("resolved/launcher.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path jar = next.resolve(Launcher.LAUNCHER).resolve("sample.jar");
        SequencedSet<String> entries = entries(jar);
        assertThat(entries).contains("jars/sample.jar/sample/Sample.class", "jars/sample.jar/module-info.class");
        Properties descriptor = application(jar);
        assertThat(descriptor.getProperty("mainModule")).isEqualTo("sample");
        assertThat(descriptor.getProperty("modulepath")).isEqualTo("sample.jar");
        assertThat(descriptor.getProperty("classpath")).isEmpty();
    }

    @Test
    public void honours_a_class_path_placement_for_a_modular_main() throws IOException {
        writeLauncherJar(Files.createDirectory(input.resolve("resolved")).resolve("launcher.jar"));
        compileModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties index = new SequencedProperties();
        index.setProperty("launcher/runtime/maven/build.jenesis/build.jenesis.launcher", "resolved/launcher.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", "sample.Sample");
        application.setProperty("mainModule", "sample");
        application.setProperty("name", "sample");
        application.store(input.resolve("launcher.properties"));

        BuildStepResult result = Launcher.ofEnvironment(Environment.NONE, "launcher", PathPlacement.CLASS_PATH).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("resolved/launcher.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path jar = next.resolve(Launcher.LAUNCHER).resolve("sample.jar");
        SequencedSet<String> entries = entries(jar);
        assertThat(entries)
                .as("a CLASS_PATH placement keeps even a modular jar on the class path, not the module path")
                .contains("jars/sample.jar/sample/Sample.class", "jars/sample.jar/module-info.class");
        assertThat(application(jar).getProperty("classpath")).isEqualTo("sample.jar");
        assertThat(application(jar).getProperty("modulepath")).isEmpty();
    }

    @Test
    public void stamps_every_entry_with_a_fixed_time_including_the_manifest() throws IOException {
        writeLauncherJar(Files.createDirectory(input.resolve("resolved")).resolve("launcher.jar"));
        writeJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"), "sample/Sample.class");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("launcher/runtime/maven/build.jenesis/build.jenesis.launcher", "resolved/launcher.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", "sample.Sample");
        application.store(input.resolve("launcher.properties"));

        BuildStepResult result = Launcher.ofEnvironment(Environment.NONE, "launcher", PathPlacement.INFERRED).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("resolved/launcher.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        try (ZipFile jar = new ZipFile(next.resolve(Launcher.LAUNCHER).resolve("application.jar").toFile())) {
            assertThat(jar.stream().map(ZipEntry::getTimeLocal))
                    .as("a launcher created at another moment carries the same bytes")
                    .containsOnly(BuildStep.timestamp(Environment.NONE).toLocalDateTime());
        }
    }

    @Test
    public void keeps_the_entry_times_of_the_jars_it_explodes_when_the_archive_timestamp_is_empty() throws IOException {
        writeLauncherJar(Files.createDirectory(input.resolve("resolved")).resolve("launcher.jar"));
        Instant modified = Instant.parse("2001-02-03T04:05:06Z");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(
                Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar")))) {
            JarEntry entry = new JarEntry("sample/Sample.class");
            entry.setTime(modified.toEpochMilli());
            jar.putNextEntry(entry);
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
        SequencedProperties index = new SequencedProperties();
        index.setProperty("launcher/runtime/maven/build.jenesis/build.jenesis.launcher", "resolved/launcher.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", "sample.Sample");
        application.store(input.resolve("launcher.properties"));
        Launcher launcher;
        launcher = Launcher.ofEnvironment(new Environment(Map.of("archive.timestamp", "")), "launcher", PathPlacement.INFERRED);

        BuildStepResult result = launcher.apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("resolved/launcher.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        try (ZipFile jar = new ZipFile(next.resolve(Launcher.LAUNCHER).resolve("application.jar").toFile())) {
            assertThat(jar.getEntry("jars/app.jar/sample/Sample.class").getTime()).isEqualTo(modified.toEpochMilli());
        }
    }

    private static void writeLauncherJar(Path path) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            entry(jar, "module-info.class");
            entry(jar, "build/jenesis/launcher/Launcher.class");
            entry(jar, "META-INF/LICENSE");
            entry(jar, "META-INF/NOTICE");
            entry(jar, "META-INF/sbom/build.jenesis.launcher.cdx.json");
        }
    }

    private static void writeJar(Path path, String entry) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            entry(jar, entry);
        }
    }

    private void compileModularJar(Path path) throws IOException {
        Path sources = Files.createDirectory(root.resolve("module-sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("module-classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", path.toString(), "-C", classes.toString(), ".")).isZero();
    }

    private static void entry(JarOutputStream jar, String name) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(new byte[] {1, 2, 3});
        jar.closeEntry();
    }

    private static SequencedSet<String> entries(Path zip) throws IOException {
        SequencedSet<String> names = new LinkedHashSet<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                names.add(enumeration.nextElement().getName());
            }
        }
        return names;
    }

    private static String mainClass(Path zip) throws IOException {
        try (JarFile file = new JarFile(zip.toFile())) {
            return file.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        }
    }

    private static Properties application(Path zip) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Properties properties = new Properties();
            try (InputStream in = file.getInputStream(file.getEntry("application.properties"))) {
                properties.load(in);
            }
            return properties;
        }
    }
}
