package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.step.Bind;
import build.jenesis.step.Classifiers;
import java.util.jar.Attributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassifiersTest {

    @TempDir
    private Path root;
    private Path module, configuration, built;

    @BeforeEach
    public void setUp() throws Exception {
        module = Files.createDirectory(root.resolve("native"));
        configuration = Files.createDirectory(root.resolve("build.jenesis"));
        built = Files.createDirectories(root.resolve("built").resolve(BuildStep.ARTIFACTS));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Implementation-Title", "native");
        manifest.getMainAttributes().putValue("Variant", "none");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(built.resolve("native.jar")), manifest)) {
            out.putNextEntry(new JarEntry("sample/Native.class"));
            out.write(new byte[]{1, 2, 3});
            out.closeEntry();
        }
    }

    @Test
    public void publishes_a_variant_holding_the_jar_and_the_files_of_its_folders() throws IOException {
        Path variant = Files.createDirectories(root.resolve("variants/linux/lib"));
        Files.writeString(variant.resolve("native.so"), "binary");
        Files.createDirectories(root.resolve("variants/linux/META-INF"));
        Files.writeString(root.resolve("variants/linux/META-INF/MANIFEST.MF"), "Manifest-Version: 1.0\nVariant: linux\n");
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=../variants/linux\n");

        Path jar = classify(configured()).resolve(Classifiers.CLASSIFIERS + "linux.jar");

        try (JarFile file = new JarFile(jar.toFile())) {
            assertThat(file.stream().map(JarEntry::getName))
                    .contains("sample/Native.class", "lib/", "lib/native.so")
                    .doesNotHaveDuplicates();
            assertThat(file.getManifest().getMainAttributes().getValue("Variant"))
                    .as("the folder's manifest is merged over the module's, its value winning")
                    .isEqualTo("linux");
            assertThat(file.getManifest().getMainAttributes().getValue("Implementation-Title"))
                    .as("an attribute the folder does not set keeps the module's value")
                    .isEqualTo("native");
        }
    }

    @Test
    public void publishes_the_jar_as_it_is_under_a_classifier_that_names_no_folder() throws IOException {
        Files.writeString(configuration.resolve(Classifiers.FILE), "legacy=\n");

        Classifiers classifiers = configured();

        assertThat(classifiers.folders()).containsOnlyKeys("legacy");
        assertThat(classifiers.folders().get("legacy")).isEmpty();
        assertThat(classify(classifiers).resolve(Classifiers.CLASSIFIERS + "legacy.jar"))
                .as("a classifier that names no folder republishes the module's jar under a name of its own")
                .hasSameBinaryContentAs(built.resolve("native.jar"));
    }

    @Test
    public void writes_every_entry_at_the_archive_timestamp() throws IOException {
        Files.createDirectories(root.resolve("variants/linux"));
        Files.writeString(root.resolve("variants/linux/native.so"), "binary");
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=../variants/linux\n");
        OffsetDateTime timestamp = OffsetDateTime.parse("1980-02-01T00:00:00Z");

        Path jar = classify(configured().timestamp(timestamp)).resolve(Classifiers.CLASSIFIERS + "linux.jar");

        try (JarFile file = new JarFile(jar.toFile())) {
            assertThat(file.stream().map(JarEntry::getTimeLocal).distinct())
                    .containsExactly(timestamp.toLocalDateTime());
        }
    }

    @Test
    public void refuses_a_file_the_jar_holds_already() throws IOException {
        Files.createDirectories(root.resolve("variants/linux/sample"));
        Files.writeString(root.resolve("variants/linux/sample/Native.class"), "other");
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=../variants/linux\n");

        assertThatThrownBy(() -> classify(configured()))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sample/Native.class")
                .hasMessageContaining("holds already");
    }

    @Test
    public void refuses_a_folder_that_does_not_exist_naming_the_line() throws IOException {
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=../variants/linux\n");

        assertThatThrownBy(this::configured)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linux=../variants/linux")
                .hasMessageContaining("which does not exist, and a plugin writes no folder above its own output");
    }

    @Test
    public void gathers_a_folder_that_a_step_of_the_module_wrote() throws IOException {
        Path written = Files.createDirectories(root.resolve("built/variants/linux/lib"));
        Files.writeString(written.resolve("native.so"), "compiled");
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=variants/linux\n");

        Classifiers classifiers = configured();

        assertThat(classifiers.folders().get("linux"))
                .as("a folder the project does not hold is left to the steps the variant reads")
                .containsEntry("variants/linux", null);
        try (JarFile file = new JarFile(classify(classifiers).resolve(Classifiers.CLASSIFIERS + "linux.jar").toFile())) {
            assertThat(file.stream().map(JarEntry::getName)).contains("sample/Native.class", "lib/native.so");
        }
    }

    @Test
    public void merges_the_folder_of_the_project_with_the_one_a_step_wrote_and_refuses_a_file_both_hold() throws IOException {
        Files.createDirectories(module.resolve("variants/linux"));
        Files.writeString(module.resolve("variants/linux/notice.txt"), "notice");
        Files.createDirectories(root.resolve("built/variants/linux"));
        Files.writeString(root.resolve("built/variants/linux/native.so"), "compiled");
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=variants/linux\n");

        try (JarFile file = new JarFile(classify(configured()).resolve(Classifiers.CLASSIFIERS + "linux.jar").toFile())) {
            assertThat(file.stream().map(JarEntry::getName)).contains("notice.txt", "native.so");
        }

        Files.writeString(root.resolve("built/variants/linux/notice.txt"), "written");
        Classifiers classifiers = configured();
        assertThatThrownBy(() -> classify(classifiers))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adds notice.txt from both");
    }

    @Test
    public void refuses_a_folder_that_neither_the_project_holds_nor_a_step_wrote() throws IOException {
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=variants/linux\n");

        Classifiers classifiers = configured();

        assertThatThrownBy(() -> classify(classifiers))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names the folder variants/linux, which neither the project holds nor a step of the module wrote");
    }

    @Test
    public void refuses_a_folder_outside_the_project() throws IOException {
        Files.createDirectories(root.resolveSibling(root.getFileName() + "-outside"));
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux=../../" + root.getFileName() + "-outside\n");

        assertThatThrownBy(this::configured)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lies outside the project");
    }

    @Test
    public void refuses_a_classifier_the_build_publishes_itself() throws IOException {
        Files.writeString(configuration.resolve(Classifiers.FILE), "sources=\n");

        assertThatThrownBy(this::configured)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publishes itself");
    }

    @Test
    public void refuses_a_classifier_maven_cannot_name() throws IOException {
        Files.writeString(configuration.resolve(Classifiers.FILE), "linux/x86=\n");

        assertThatThrownBy(this::configured)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letters, digits, _, . and -");
    }

    @Test
    public void declares_no_classifier_with_an_empty_file() throws IOException {
        Files.writeString(configuration.resolve(Classifiers.FILE), "");

        assertThat(configured()).isNull();
    }

    @Test
    public void reads_only_the_file_of_the_first_location_that_holds_one() throws IOException {
        Path profile = Files.createDirectory(configuration.resolve("linux"));
        Files.writeString(profile.resolve(Classifiers.FILE), "");
        Files.writeString(configuration.resolve(Classifiers.FILE), "legacy=\n");

        assertThat(Classifiers.configured(new LinkedHashSet<>(List.of(profile, configuration)), module, root))
                .as("an empty file of an active profile declares no classifier and shadows the file below it")
                .isNull();
    }

    @Test
    public void remembers_the_profile_that_declares_a_classifier_which_is_not_active() throws IOException {
        Path profile = Files.createDirectory(configuration.resolve("platform-a"));
        Files.writeString(profile.resolve(Classifiers.FILE), "platform-a=\n");

        Classifiers classifiers = configured();

        assertThat(classifiers.folders()).isEmpty();
        assertThat(classifiers.dormant()).containsOnlyKeys("platform-a");
        assertThat(classifiers.dormant().get("platform-a")).containsExactly("platform-a");
    }

    private Classifiers configured() throws IOException {
        return Classifiers.configured(new LinkedHashSet<>(List.of(configuration)), module, root);
    }

    private Path classify(Classifiers classifiers) throws IOException {
        BuildExecutor buildExecutor = BuildExecutor.of(root.resolve("target"),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        buildExecutor.addSource("built", new Bind(Map.of(Path.of(""), Path.of(""))), root.resolve("built"));
        buildExecutor.addModule("classifiers", classifiers, "built");
        return buildExecutor.execute().get("classifiers");
    }
}
