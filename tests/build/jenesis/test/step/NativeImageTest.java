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
import build.jenesis.step.NativeImage;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NativeImageTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, bundle, shim;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        bundle = Files.createDirectory(root.resolve("bundle"));
        Files.writeString(Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"), "jar");
        shim = root.resolve("native-image-shim");
        Files.writeString(shim, "#!/bin/sh\nexit 0\n");
        shim.toFile().setExecutable(true);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void emits_a_modular_native_image_command() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("name", "sample-app");
        launcher.setProperty("mainModule", "sample");
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);

        BuildStepResult result = run(PathPlacement.MODULE_PATH);
        assertThat(result.next()).isTrue();
        assertThat(command())
                .contains("--no-fallback")
                .contains("--module-path")
                .contains("-o " + next.resolve(NativeImage.NATIVE).resolve("sample-app"))
                .endsWith("--module sample/sample.Sample");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void emits_a_classpath_native_image_command() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);

        BuildStepResult result = run(PathPlacement.CLASS_PATH);
        assertThat(result.next()).isTrue();
        assertThat(command())
                .contains("--no-fallback")
                .contains("-cp")
                .doesNotContain("--module-path")
                .endsWith("sample.Sample");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void splits_module_and_class_path_and_roots_all_for_an_inferred_image() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("name", "sample-app");
        launcher.setProperty("mainModule", "sample");
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);
        modularJar(bundle.resolve(BuildStep.ARTIFACTS).resolve("sample.jar"), "sample");

        BuildStepResult result = run(PathPlacement.INFERRED);
        assertThat(result.next()).isTrue();
        assertThat(command())
                .contains("--module-path")
                .contains("-cp")
                .contains("--add-modules ALL-MODULE-PATH,ALL-DEFAULT")
                .endsWith("--module sample/sample.Sample");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void resolves_runtime_dependencies_from_a_configured_group() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);
        Path deps = Files.createDirectory(bundle.resolve("deps"));
        Files.writeString(deps.resolve("extra-lib.jar"), "jar");
        Files.writeString(deps.resolve("main-lib.jar"), "jar");
        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("extra/runtime/maven/extra-lib", "deps/extra-lib.jar");
        dependencies.setProperty("main/runtime/maven/main-lib", "deps/main-lib.jar");
        dependencies.store(bundle.resolve(BuildStep.DEPENDENCIES));

        BuildStepResult result = new NativeImage(PathPlacement.CLASS_PATH, ProcessHandler.OfProcess.of(List.of(shim.toString())))
                .group("extra")
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                                bundle,
                                Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(command())
                .contains("extra-lib.jar")
                .doesNotContain("main-lib.jar");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void reads_the_artifacts_folder_shallowly() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);
        Path nested = Files.createDirectories(bundle.resolve(BuildStep.ARTIFACTS).resolve("sub"));
        Files.writeString(nested.resolve("nested.jar"), "jar");

        BuildStepResult result = run(PathPlacement.CLASS_PATH);
        assertThat(result.next()).isTrue();
        assertThat(command())
                .as("the artifacts folder is read as direct children, not walked into subfolders")
                .contains("app.jar")
                .doesNotContain("nested.jar");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void passes_a_reachability_config_directory() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainModule", "sample");
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);
        Path config = Files.createDirectory(bundle.resolve("native-image"));

        BuildStepResult result = run(PathPlacement.MODULE_PATH);
        assertThat(result.next()).isTrue();
        assertThat(command()).contains("-H:ConfigurationFileDirectories=" + config);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void records_the_release_and_the_licence_of_the_graalvm_it_runs_through_a_linked_launcher() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);
        Path home = Files.createDirectory(root.resolve("graalvm"));
        Files.writeString(home.resolve(BuildStep.RELEASE), "IMPLEMENTOR=\"GraalVM Community\"\nGRAALVM_VERSION=\"25.0.2\"\n");
        Files.writeString(home.resolve("LICENSE.txt"), "GPLv2 with the Classpath Exception");
        Files.writeString(home.resolve("THIRD_PARTY_LICENSE.txt"), "third parties");
        Files.writeString(Files.createDirectories(home.resolve("lib/svm")).resolve("LICENSE_NATIVEIMAGE.txt"), "native image");
        Files.createSymbolicLink(home.resolve("LICENSE_NATIVEIMAGE.txt"), Path.of("lib/svm/LICENSE_NATIVEIMAGE.txt"));
        Files.writeString(home.resolve("GRAALVM-README.md"), "read me");
        Path program = Files.copy(shim, Files.createDirectories(home.resolve("lib/svm/bin")).resolve("native-image"));
        Path linked = Files.createSymbolicLink(Files.createDirectory(home.resolve("bin")).resolve("native-image"),
                Path.of("../lib/svm/bin/native-image"));

        new NativeImage(PathPlacement.CLASS_PATH, ProcessHandler.OfProcess.of(List.of(linked.toString()))).apply(
                        Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                                bundle,
                                Map.of(Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join();

        assertThat(program).isExecutable();
        assertThat(next.resolve(BuildStep.RELEASE))
                .as("the release file sits in the home the link leads out of, not beside the linked program")
                .hasSameTextualContentAs(home.resolve(BuildStep.RELEASE));
        try (Stream<Path> files = Files.list(next.resolve(NativeImage.NATIVE).resolve(NativeImage.LICENSES).resolve("graalvm-25.0.2"))) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .as("the binary contains the GraalVM, so its licence and notice files travel beside it")
                    .containsExactlyInAnyOrder("LICENSE.txt", "THIRD_PARTY_LICENSE.txt", "LICENSE_NATIVEIMAGE.txt");
        }
        assertThat(next.resolve(NativeImage.NATIVE).resolve(NativeImage.LICENSES).resolve("graalvm-25.0.2/LICENSE_NATIVEIMAGE.txt"))
                .as("a licence the GraalVM links to from its home is taken as the file it names")
                .hasContent("native image");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void refuses_the_notices_of_a_jar_that_take_the_place_of_the_graalvm_licence() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        store(launcher);
        Files.writeString(Files.createDirectories(bundle.resolve("legal/graalvm-25.0.2")).resolve("LICENSE"), "a jar");
        Path home = Files.createDirectory(root.resolve("graalvm"));
        Files.writeString(home.resolve(BuildStep.RELEASE), "GRAALVM_VERSION=\"25.0.2\"\n");
        Path program = Files.copy(shim, Files.createDirectory(home.resolve("bin")).resolve("native-image"));

        assertThatThrownBy(() -> new NativeImage(PathPlacement.CLASS_PATH, ProcessHandler.OfProcess.of(List.of(program.toString()))).apply(
                        Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                                bundle,
                                Map.of(Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join())
                .rootCause()
                .hasMessageContaining("graalvm-25.0.2");
    }

    @Test
    public void skips_when_no_launcher_is_configured() throws IOException {
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("name", "sample-app");
        store(launcher);

        BuildStepResult result = run(PathPlacement.MODULE_PATH);
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(NativeImage.NATIVE)).doesNotExist();
        assertThat(supplement.resolve("command")).doesNotExist();
    }

    private void store(SequencedProperties launcher) throws IOException {
        launcher.store(bundle.resolve("launcher.properties"));
    }

    private void modularJar(Path target, String moduleName) throws IOException {
        byte[] moduleInfo = ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(moduleName),
                builder -> builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null)));
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            out.putNextEntry(new ZipEntry("module-info.class"));
            out.write(moduleInfo);
            out.closeEntry();
        }
    }

    private BuildStepResult run(PathPlacement modulePath) throws IOException {
        return new NativeImage(modulePath, ProcessHandler.OfProcess.of(List.of(shim.toString()))).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join();
    }

    private String command() throws IOException {
        return Files.readString(supplement.resolve("command"));
    }
}
