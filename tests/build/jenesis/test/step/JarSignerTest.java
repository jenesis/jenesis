package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.step.JarSigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JarSignerTest {

    private static final String ALIAS = "test";

    @TempDir
    private Path root, project, secrets;

    private Path keystore, password;

    @AfterEach
    public void clearProperties() {
        for (String name : List.of("keystore", "storetype", "alias", "storepass", "keypass", "tsa", "arguments")) {
            System.clearProperty("jenesis.jarsigner." + name);
        }
    }

    @BeforeEach
    public void generateKey() throws IOException, InterruptedException {
        keystore = secrets.resolve("test.p12");
        password = secrets.resolve("test.pass");
        Files.writeString(password, "test-store-password");
        int code = new ProcessBuilder(keytool(),
                "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=Jenesis Test",
                "-keystore", keystore.toString(),
                "-storetype", "PKCS12",
                "-storepass:file", password.toString())
                .redirectErrorStream(true)
                .start()
                .waitFor();
        assertThat(code).as("keytool generated a throwaway key store").isZero();
        Path artifacts = Files.createDirectories(project.resolve(BuildStep.ARTIFACTS));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("sample.jar")))) {
            jar.putNextEntry(new JarEntry("sample/Sample.txt"));
            jar.write("sample".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    @Test
    public void signs_the_artifact_it_reads_under_its_own_name() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addStep("sign", newSigner(), "project");
        executor.execute();

        assertThat(signed()).isNotEmptyFile();
        try (JarFile jar = new JarFile(signed().toFile())) {
            assertThat(jar.stream().map(JarEntry::getName))
                    .as("the signature block and manifest digests are written into the jar")
                    .contains("META-INF/TEST.SF", "META-INF/TEST.RSA");
            assertThat(jar.getEntry("sample/Sample.txt"))
                    .as("the signed jar carries the content of the jar it read")
                    .isNotNull();
        }
    }

    @Test
    public void leaves_the_artifact_it_read_untouched() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addStep("sign", newSigner(), "project");
        executor.execute();

        try (JarFile jar = new JarFile(project.resolve(BuildStep.ARTIFACTS).resolve("sample.jar").toFile())) {
            assertThat(jar.getEntry("META-INF/TEST.SF"))
                    .as("a step writes into its own folder, never into the folder it read")
                    .isNull();
        }
    }

    @Test
    public void rejects_a_password_that_is_not_a_location() {
        assertThatThrownBy(() -> JarSigner.ofEnvironment(Environment.NONE).storepass("secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.jarsigner.storepass")
                .hasMessageContaining("'env <variable>' or 'file <path>'");
    }

    @Test
    public void reads_the_key_it_signs_with_from_the_environment() throws IOException {

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addStep("sign", JarSigner.ofEnvironment(new Environment(Map.of("jarsigner.keystore", keystore.toString(), "jarsigner.storetype", "PKCS12", "jarsigner.alias", ALIAS, "jarsigner.storepass", "file " + password))), "project");
        executor.execute();

        try (JarFile jar = new JarFile(signed().toFile())) {
            assertThat(jar.stream().map(JarEntry::getName))
                    .as("a key store nothing in the project names still signs the jar")
                    .contains("META-INF/TEST.SF");
        }
    }

    @Test
    public void refuses_to_ship_unsigned_when_no_key_store_was_supplied() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addStep("sign", JarSigner.ofEnvironment(Environment.NONE).alias(ALIAS), "project");

        assertThatThrownBy(executor::execute).rootCause()
                .as("a project that says it signs must not quietly produce an unsigned jar"
                        + " because a runner forgot the key")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("This project signs its jar but no key store is named")
                .hasMessageContaining("jenesis.jarsigner.keystore");
    }

    @Test
    public void refuses_a_key_store_whose_password_nothing_locates() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addStep("sign", JarSigner.ofEnvironment(Environment.NONE).keystore(keystore.toString()).alias(ALIAS), "project");

        assertThatThrownBy(executor::execute).rootCause()
                .as("jarsigner would ask for the password, and a build that cannot answer hangs")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.jarsigner.storepass");
    }

    @Test
    public void says_nothing_about_signing_until_something_names_it() {
        assertThat(JarSigner.ofEnvironment(Environment.NONE).configured())
                .as("nothing is set, so the archiver's jar is the artifact")
                .isFalse();
        assertThat(JarSigner.ofEnvironment(Environment.NONE).alias(ALIAS).configured())
                .as("a project that names the key it signs with has said it signs")
                .isTrue();
    }

    @Test
    public void fails_when_no_key_store_is_where_it_was_named() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addStep("sign",
                newSigner().keystore(secrets.resolve("absent.p12").toString()),
                "project");

        assertThatThrownBy(executor::execute).rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No key store at");
    }

    private Path signed() {
        return root.resolve("sign").resolve("output").resolve(BuildStep.ARTIFACTS).resolve("sample.jar");
    }

    private JarSigner newSigner() {
        return JarSigner.ofEnvironment(Environment.NONE)
                .keystore(keystore.toString())
                .storetype("PKCS12")
                .alias(ALIAS)
                .storepass("file " + password);
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
