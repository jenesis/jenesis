package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.InferredJavaToolchainModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredJavaToolchainModuleTest {

    @TempDir
    private Path input, root;

    @Test
    public void compiles_and_archives_through_the_inferred_chain() throws IOException {
        Path sources = Files.createDirectories(input.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Sample.java"), "package sample; public class Sample { }");

        BuildExecutor executor = newExecutor();
        executor.addSource("input", input);
        executor.addModule("output", toolchain(), "input");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps).containsKeys("output/classes", "output/artifacts");
        assertThat(steps.get("output/classes").resolve(BuildStep.CLASSES).resolve("sample/Sample.class")).exists();
    }

    @Test
    public void the_generator_override_switches_off_source_generation() throws IOException {
        Path sources = Files.createDirectories(input.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Sample.java"), "package sample; public class Sample { }");

        BuildExecutor executor = newExecutor();
        executor.addSource("input", input);
        executor.addModule("output", toolchain().generator(null), "input");
        executor.execute();

        assertThat(root.resolve("output").resolve("generated"))
                .as("no generation stage is wired once the generator configurator is dropped")
                .doesNotExist();
        assertThat(root.resolve("output").resolve("artifacts")).exists();
    }

    @Test
    public void a_toolchain_without_a_compiler_names_what_is_missing() {
        assertThatThrownBy(() -> toolchain().compiler(null).accept(null, new LinkedHashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compiler");
    }

    @Test
    public void signs_the_artifact_the_archiver_wrote() throws IOException {
        Path sources = Files.createDirectories(input.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Sample.java"), "package sample; public class Sample { }");
        generateKey();

        BuildExecutor executor = newExecutor();
        executor.addSource("input", input);
        executor.addModule("output", toolchain().signer(signer -> signer
                .keystore(input.resolve("test.p12").toString())
                .storetype("PKCS12")
                .alias("test")
                .storepass("file " + input.resolve("test.pass"))), "input");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps)
                .as("the signed jar takes the place of the jar the archiver wrote")
                .containsKey("output/artifacts");
        try (JarFile jar = new JarFile(steps.get("output/artifacts")
                .resolve(BuildStep.ARTIFACTS)
                .resolve("classes.jar")
                .toFile())) {
            assertThat(jar.stream().map(JarEntry::getName)).contains("META-INF/TEST.SF");
        }
    }

    @Test
    public void archives_without_signing_when_no_key_store_is_named() throws IOException {
        Path sources = Files.createDirectories(input.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Sample.java"), "package sample; public class Sample { }");

        BuildExecutor executor = newExecutor();
        executor.addSource("input", input);
        executor.addModule("output", toolchain(), "input");
        SequencedMap<String, Path> steps = executor.execute();

        try (JarFile jar = new JarFile(steps.get("output/artifacts")
                .resolve(BuildStep.ARTIFACTS)
                .resolve("classes.jar")
                .toFile())) {
            assertThat(jar.stream().map(JarEntry::getName))
                    .as("nothing names a key store, so the archiver's jar is the artifact")
                    .noneMatch(name -> name.endsWith(".SF"));
        }
    }

    private void generateKey() throws IOException {
        Path password = input.resolve("test.pass");
        Files.writeString(password, "test-store-password");
        int code;
        try {
            code = new ProcessBuilder(keytool(),
                    "-genkeypair",
                    "-alias", "test",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "1",
                    "-dname", "CN=Jenesis Test",
                    "-keystore", input.resolve("test.p12").toString(),
                    "-storetype", "PKCS12",
                    "-storepass:file", password.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        assertThat(code).as("keytool generated a throwaway key store").isZero();
    }

    private InferredJavaToolchainModule toolchain() {
        return new InferredJavaToolchainModule(new LinkedHashSet<>(List.of(input)), Map.of(), Map.of());
    }

    private static String keytool() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool").toString();
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
