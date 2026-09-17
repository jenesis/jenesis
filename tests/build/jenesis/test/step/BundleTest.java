package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bundle;

import static org.assertj.core.api.Assertions.assertThat;

public class BundleTest {

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
    public void bundles_a_non_modular_main_onto_the_class_path() throws IOException {
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));
        writePlainJar(Files.createDirectory(input.resolve("resolved")).resolve("lib.jar"));
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/runtime/maven/lib", "resolved/lib.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Bundle().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("resolved/lib.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path zip = next.resolve(Bundle.BUNDLE).resolve("bundle.zip");
        assertThat(zip).isRegularFile();
        SequencedSet<String> entries = entries(zip);
        assertThat(entries).contains(
                "application.unix.args", "application.windows.args", "jars/app.jar", "jars/lib.jar");
        assertThat(application(zip))
                .as("the descriptor is the launch itself, run as `java @application.unix.args`")
                .containsExactly("--class-path", path("app.jar", "lib.jar"), "sample.Sample");
        assertThat(application(zip, "windows"))
                .as("a bundle is unpacked wherever, so it carries a file per path separator"
                        + " rather than the separator of whoever built it")
                .containsExactly("--class-path", "jars/app.jar;jars/lib.jar", "sample.Sample");
    }

    @Test
    public void bundles_a_modular_main_onto_the_module_path() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file",
                Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar").toString(),
                "-C", classes.toString(), ".")).isZero();
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Bundle().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path zip = next.resolve(Bundle.BUNDLE).resolve("bundle.zip");
        SequencedSet<String> entries = entries(zip);
        assertThat(entries).contains("application.unix.args", "jars/sample.jar");
        assertThat(application(zip)).containsExactly(
                "--module-path", path("sample.jar"),
                "--module", "sample/sample.Sample");
    }

    @Test
    public void a_class_path_jar_beside_a_module_is_recorded_as_java_options() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        Path artifacts = Files.createDirectory(input.resolve(BuildStep.ARTIFACTS));
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", artifacts.resolve("sample.jar").toString(),
                "-C", classes.toString(), ".")).isZero();
        writePlainJar(artifacts.resolve("lib.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Bundle().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/lib.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path zip = next.resolve(Bundle.BUNDLE).resolve("bundle.zip");
        assertThat(entries(zip)).contains("jars/sample.jar", "jars/lib.jar");
        assertThat(application(zip))
                .as("a class-path jar beside a module roots the whole module path")
                .containsExactly(
                        "--class-path", path("lib.jar"),
                        "--module-path", path("sample.jar"),
                        "--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT",
                        "--module", "sample/sample.Sample");
    }

    @Test
    public void bundles_an_aliased_dependency_onto_the_module_path() throws IOException {
        Path resolved = Files.createDirectory(input.resolve("resolved"));
        writeLibraryJar(resolved.resolve("alias.lib.jar"));
        Files.writeString(PathPlacement.declaration(resolved.resolve("alias.lib.jar")), "alias.lib");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/runtime/maven/lib", "resolved/alias.lib.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), """
                module sample {
                    requires alias.lib;
                }
                """);
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                "-p", resolved.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file",
                Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar").toString(),
                "-C", classes.toString(), ".")).isZero();
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Bundle().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("resolved/alias.lib.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path zip = next.resolve(Bundle.BUNDLE).resolve("bundle.zip");
        SequencedSet<String> entries = entries(zip);
        assertThat(entries)
                .as("a jar named for its alias derives that module name wherever it is unpacked")
                .contains("jars/sample.jar", "jars/alias.lib.jar");
        assertThat(application(zip)).containsExactly(
                "--module-path", path("alias.lib.jar", "sample.jar"),
                "--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT",
                "--module", "sample/sample.Sample");
    }

    @Test
    public void skips_a_module_without_a_main() throws IOException {
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));

        BuildStepResult result = new Bundle().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Bundle.BUNDLE)).doesNotExist();
    }

    @Test
    public void stamps_every_entry_with_the_archive_timestamp() throws IOException {
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Bundle().apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        try (ZipFile zip = new ZipFile(next.resolve(Bundle.BUNDLE).resolve("bundle.zip").toFile())) {
            assertThat(zip.stream().map(ZipEntry::getTimeLocal))
                    .as("a bundle created at another moment carries the same bytes")
                    .containsOnly(BuildStep.timestamp().toLocalDateTime());
        }
    }

    private static void writeLibraryJar(Path path) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("alias/lib/Type.class"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
    }

    private static void writePlainJar(Path path) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
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

    private static List<String> application(Path zip) throws IOException {
        return application(zip, "unix");
    }

    private static List<String> application(Path zip, String platform) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            try (InputStream in = file.getInputStream(
                    file.getEntry("application." + platform + ".args"))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(line -> line.substring(1, line.length() - 1))
                        .toList();
            }
        }
    }

    private static String path(String... names) {
        return Stream.of(names).map(name -> "jars/" + name).collect(Collectors.joining(":"));
    }
}
