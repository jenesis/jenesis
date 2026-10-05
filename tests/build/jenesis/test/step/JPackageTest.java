package build.jenesis.test.step;

import module java.base;
import java.util.jar.Attributes;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.JLink;
import build.jenesis.step.JPackage;
import build.jenesis.step.Layers;
import sample.Sample;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class JPackageTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, bundle;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        bundle = Files.createDirectory(root.resolve("bundle"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_jpackage(boolean process) throws IOException {
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "sample.Sample");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("app.jar")), manifest)) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            try (InputStream in = Sample.class.getResourceAsStream("Sample.class")) {
                requireNonNull(in).transferTo(jar);
            }
            jar.closeEntry();
        }
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--name", "Sample");
        configuration.setProperty("--main-jar", "app.jar");
        configuration.setProperty("--main-class", "sample.Sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        BuildStepResult result = new JPackage(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JPackage.PACKAGES)).isNotEmptyDirectory();
        assertThat(imageDirectory()).isDirectory();
    }

    @Test
    public void reads_the_options_of_its_own_format_beside_the_shared_ones() throws IOException {
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("app.jar")), manifest)) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            try (InputStream in = Sample.class.getResourceAsStream("Sample.class")) {
                requireNonNull(in).transferTo(jar);
            }
            jar.closeEntry();
        }
        Path process = Files.createDirectory(bundle.resolve("process"));
        SequencedProperties shared = new SequencedProperties();
        shared.setProperty("--name", "Sample");
        shared.setProperty("--main-jar", "app.jar");
        shared.store(process.resolve("jpackage.properties"));
        SequencedProperties image = new SequencedProperties();
        image.setProperty("--main-class", "sample.Sample");
        image.store(process.resolve("jpackage-app-image.properties"));
        SequencedProperties installer = new SequencedProperties();
        installer.setProperty("--linux-deb-maintainer", "dev@example.com");
        installer.store(process.resolve("jpackage-deb.properties"));
        BuildStepResult result = new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(Files.readString(supplement.resolve("command")))
                .as("an application image takes its own options and none of an installer, which jpackage would refuse")
                .contains("--main-class sample.Sample")
                .doesNotContain("--linux-deb-maintainer");
        assertThat(imageDirectory()).isDirectory();
    }

    @ParameterizedTest
    @CsvSource({
            "1-SNAPSHOT,        1",
            "1.0.0-SNAPSHOT,    1.0.0",
            "2.1.3+build.7,     2.1.3",
            "1.0.0,             1.0.0",
            "1.0.-beta,         1.0",
            "RELEASE,           RELEASE",
            "main-SNAPSHOT,     main-SNAPSHOT"})
    public void truncates_the_app_version_qualifier(String version, String expected) throws IOException {
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--app-version", version);
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        SequencedMap<String, SequencedMap<String, String>> properties = new ExposedJPackage().properties(
                new LinkedHashMap<>(Map.of("bundle", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED))))));
        assertThat(properties.get("bundle").get("--app-version"))
                .as("jpackage parses --app-version as a dotted version on every platform")
                .isEqualTo(expected);
    }

    @Test
    public void leaves_the_licence_out_of_an_application_image() throws IOException {
        mainJar();
        Files.writeString(Files.createDirectory(bundle.resolve("legal")).resolve("LICENSE"), "licence");
        new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(supplement.resolve("command"))
                .as("jpackage refuses a licence file for an application image")
                .content()
                .doesNotContain("--license-file");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void passes_the_licence_of_the_module_to_an_installable_package() throws IOException {
        assumeTrue(Stream.of(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
                .anyMatch(folder -> Files.isExecutable(Path.of(folder, "dpkg-deb"))), "dpkg-deb builds a Debian package");
        mainJar();
        Path licence = Files.createDirectory(bundle.resolve("legal")).resolve("LICENSE");
        Files.writeString(licence, "licence");
        new JPackage(ProcessHandler.Factory.TOOL).type("deb").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(supplement.resolve("command")).content().contains("--license-file " + licence);
        try (Stream<Path> files = Files.list(next.resolve(JPackage.PACKAGES))) {
            assertThat(files.map(file -> file.getFileName().toString())).anyMatch(name -> name.endsWith(".deb"));
        }
    }

    private void mainJar() throws IOException {
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "sample.Sample");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("app.jar")), manifest)) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            try (InputStream in = Sample.class.getResourceAsStream("Sample.class")) {
                requireNonNull(in).transferTo(jar);
            }
            jar.closeEntry();
        }
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--name", "sample");
        configuration.setProperty("--main-jar", "app.jar");
        configuration.setProperty("--main-class", "sample.Sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
    }

    private static class ExposedJPackage extends JPackage {

        private ExposedJPackage() {
            super(ProcessHandler.Factory.TOOL);
        }

        @Override
        public SequencedMap<String, SequencedMap<String, String>> properties(
                SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            return super.properties(arguments);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void skips_when_no_jars_are_present(boolean process) throws IOException {
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--main-jar", "app.jar");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        BuildStepResult result = new JPackage(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JPackage.PACKAGES)).doesNotExist();
    }

    @Test
    public void can_execute_jpackage_in_modular_mode() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        int compiled = ToolProvider.findFirst("javac").orElseThrow().run(System.out,
                System.err,
                "-d",
                classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString());
        assertThat(compiled).isZero();
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        int archived = ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", artifacts.resolve("sample.jar").toString(),
                "-C", classes.toString(), ".");
        assertThat(archived).isZero();
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--name", "Sample");
        configuration.setProperty("--module", "sample/sample.Sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        BuildStepResult result = new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(imageDirectory()).isDirectory();
    }

    @Test
    public void wraps_jlink_runtime_via_runtime_image() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out,
                System.err,
                "-d",
                classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        Path modules = Files.createDirectory(root.resolve("modules"));
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", modules.resolve("sample.jar").toString(),
                "-C", classes.toString(), ".")).isZero();
        Path runtime = bundle.resolve(JLink.RUNTIME);
        assertThat(ToolProvider.findFirst("jlink").orElseThrow().run(System.out, System.err,
                "--module-path", modules.toString(),
                "--add-modules", "sample",
                "--output", runtime.toString())).isZero();
        Files.writeString(Files.createDirectories(runtime.resolve("conf")).resolve("app.properties"),
                "greeting=bundled\n");
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--name", "Sample");
        configuration.setProperty("--module", "sample/sample.Sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        BuildStepResult result = new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("runtime", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        Path image = imageDirectory();
        assertThat(image).isDirectory();
        Path bundled;
        try (Stream<Path> walk = Files.walk(image)) {
            bundled = walk.filter(path -> path.endsWith(Path.of("conf", "app.properties")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Bundled runtime config not found in " + image));
        }
        assertThat(bundled).content().contains("greeting=bundled");
    }

    @Test
    public void ships_a_layer_beside_the_application_and_names_it_to_the_launcher() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out,
                System.err,
                "-d",
                classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        Path modules = Files.createDirectory(root.resolve("modules"));
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", modules.resolve("sample.jar").toString(),
                "-C", classes.toString(), ".")).isZero();
        assertThat(ToolProvider.findFirst("jlink").orElseThrow().run(System.out, System.err,
                "--module-path", modules.toString(),
                "--add-modules", "sample",
                "--output", bundle.resolve(JLink.RUNTIME).toString())).isZero();
        Files.writeString(Files.createDirectory(bundle.resolve("resolved")).resolve("layered.jar"), "layered");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("layer:render/runtime/module/layered", "resolved/layered.jar");
        index.store(bundle.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties membership = new SequencedProperties();
        membership.setProperty("modulepath.render", "layered.jar");
        membership.store(bundle.resolve(Layers.MEMBERSHIP));
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--name", "Sample");
        configuration.setProperty("--module", "sample/sample.Sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        BuildStepResult result = new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("runtime", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        Path launcher, shipped;
        try (Stream<Path> walk = Files.walk(imageDirectory())) {
            List<Path> files = walk.toList();
            launcher = files.stream().filter(path -> path.getFileName().toString().equals("Sample.cfg")).findFirst().orElseThrow();
            shipped = files.stream().filter(path -> path.endsWith(Path.of("layers", "layered.jar"))).findFirst().orElseThrow();
        }
        assertThat(shipped.getParent().getParent())
                .as("the layers travel beside the application folder, where no class path reaches them")
                .isEqualTo(launcher.getParent().getParent());
        assertThat(launcher).content().contains("-Djlayer.modulepath.render="
                + String.join(File.separator, "$APPDIR", "..", "layers", "layered.jar"));
    }

    @Test
    public void fails_on_duplicate_jar_file_names() throws IOException {
        Files.writeString(Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"), "one");
        Files.writeString(Files.createDirectory(bundle.resolve("resolved")).resolve("app.jar"), "two");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/runtime/maven/app", "resolved/app.jar");
        index.store(bundle.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--main-jar", "app.jar");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jpackage.properties"));
        assertThatThrownBy(() -> new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("resolved/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jpackage.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same file name 'app.jar'");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void skips_when_no_launcher_is_configured(boolean process) throws IOException {
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("app.jar")))) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            try (InputStream in = Sample.class.getResourceAsStream("Sample.class")) {
                requireNonNull(in).transferTo(jar);
            }
            jar.closeEntry();
        }
        BuildStepResult result = new JPackage(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).type("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JPackage.PACKAGES)).doesNotExist();
    }

    private Path imageDirectory() {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        return next.resolve(JPackage.PACKAGES + (mac ? "Sample.app" : "Sample"));
    }
}
