package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.KeyExpiry;
import build.jenesis.OpenPgpRepository;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;
import build.jenesis.maven.MavenRepository;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIf("gpgAvailable")
public class SignaturesRunTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, input, home, vault, jar, detached;
    private String fingerprint;

    static boolean gpgAvailable() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        for (String command : List.of("gpg", "gpgv")) {
            try {
                if (new ProcessBuilder(command, "--version")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor() != 0) {
                    return false;
                }
            } catch (IOException | InterruptedException _) {
                return false;
            }
        }
        return true;
    }

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
        home = Files.createDirectory(root.resolve("gnupg"));
        vault = Files.createDirectory(root.resolve("keys"));
        Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"));
        gpg("--quick-generate-key", "Jenesis Signature Test <test@example.invalid>", "default", "default", "never");
        fingerprint = fingerprint();
        jar = Files.writeString(input.resolve("lib.jar"), "artifact bytes\n");
        detached = root.resolve("lib.jar.asc");
        gpg("--detach-sign", "--armor", "--output", detached.toString(), jar.toString());
        vaulted(fingerprint);
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/compile/maven/org.example/lib/1.0", "lib.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties declarations = new SequencedProperties();
        declarations.setProperty("OpenPGP/" + fingerprint, "main/maven/org.example/lib");
        declarations.store(input.resolve(BuildStep.SIGNATURES));
    }

    private void vaulted(String key) throws Exception {
        gpg("--export", "--output", vault.resolve(key + ".gpg").toString(), key);
    }

    private void gpg(String... arguments) throws Exception {
        gpgIn(home, arguments);
    }

    private void gpgIn(Path keyring, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("gpg",
                "--homedir", keyring.toString(),
                "--batch", "--yes", "--passphrase", "", "--pinentry-mode", "loopback"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectErrorStream(false)
                .start();
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Failed to run gpg "
                    + String.join(" ", arguments)
                    + (error.isBlank() ? "" : ":\n" + error));
        }
    }

    private String fingerprint() throws Exception {
        return fingerprintIn(home);
    }

    private String fingerprintOf(String identity) throws Exception {
        Process process = new ProcessBuilder("gpg",
                "--homedir", home.toString(),
                "--batch", "--with-colons", "--fingerprint").start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        String candidate = null;
        for (String line : output.split("\n")) {
            if (line.startsWith("fpr:")) {
                candidate = line.split(":")[9];
            } else if (line.startsWith("uid:") && line.contains(identity)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No key for " + identity + " in:\n" + output);
    }

    private String fingerprintIn(Path keyring) throws Exception {
        Process process = new ProcessBuilder("gpg",
                "--homedir", keyring.toString(),
                "--batch", "--with-colons", "--fingerprint").start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        for (String line : output.split("\n")) {
            if (line.startsWith("fpr:")) {
                return line.split(":")[9];
            }
        }
        throw new IllegalStateException("No fingerprint in:\n" + output);
    }

    private void run(Path keyring) throws IOException {
        run(keyring, KeyExpiry.SIGNING);
    }

    private static Repository vaulted(Path vault) {
        return (_, coordinate) -> {
            Path candidate = vault.resolve(coordinate + ".gpg");
            return Files.isRegularFile(candidate)
                    ? Optional.of(RepositoryItem.ofFile(candidate))
                    : Optional.empty();
        };
    }

    private Map<String, Repository> serving(Repository keys) {
        return Map.of("maven", (MavenRepository) (_, _, _, _, type, _, checksum) ->
                Optional.ofNullable("jar".equals(type) && "asc".equals(checksum)
                        ? RepositoryItem.ofFile(detached)
                        : null),
                "OpenPGP", keys);
    }

    private void run(Path keyring, KeyExpiry expiry) throws IOException {
        new Signatures(serving(vaulted(keyring)))
                .verification(Verification.DECLARED)
                .expiry(expiry)
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
    }

    @Test
    public void accepts_the_fingerprint_gpg_reports_for_a_genuine_signature() {
        assertThatCode(() -> run(vault)).doesNotThrowAnyException();
    }

    @Test
    public void rejects_an_artifact_whose_bytes_changed_after_signing() throws Exception {
        Files.writeString(jar, "tampered bytes\n");
        assertThatThrownBy(() -> run(vault))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the file");
    }

    private void signedByAnExpiringKey(String expiry) throws Exception {
        gpg("--quick-generate-key",
                "Jenesis Expiry Test <expiry@example.invalid>",
                "default",
                "default",
                expiry);
        String expiring = fingerprintOf("expiry@example.invalid");
        vaulted(expiring);
        gpg("--detach-sign",
                "--armor",
                "--local-user", expiring,
                "--output", detached.toString(),
                jar.toString());
        SequencedProperties declarations = new SequencedProperties();
        declarations.setProperty("OpenPGP/" + expiring, "main/maven/org.example/lib");
        declarations.store(input.resolve(BuildStep.SIGNATURES));
    }

    @Test
    public void reads_every_expiry_mode_from_a_key_that_really_expired() throws Exception {
        signedByAnExpiringKey("seconds=5");
        long expired = System.currentTimeMillis() + 6_000;
        while (System.currentTimeMillis() < expired) {
            Thread.sleep(200);
        }
        assertThatCode(() -> run(vault, KeyExpiry.SIGNING))
                .as("gpgv reports EXPKEYSIG, and the signature predates the expiry it reports")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> run(vault, KeyExpiry.CURRENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has expired");
        assertThatCode(() -> run(vault, KeyExpiry.IGNORED))
                .as("ignored accepts it whenever it signed")
                .doesNotThrowAnyException();
    }

    @Test
    public void reads_a_status_stream_whose_user_id_is_not_utf_8() throws Exception {
        List<String> emitting = List.of("sh", "-c", "printf '"
                + "[GNUPG:] GOODSIG DEADBEEF \\311amonn McManus <test@example.invalid>\\n"
                + "[GNUPG:] VALIDSIG " + fingerprint + " 2026-09-11 1000 0 4 0 1 8 00 " + fingerprint + "\\n'");
        assertThatCode(() -> new Signatures(serving(vaulted(vault)))
                .verification(Verification.DECLARED)
                .factory(ProcessHandler.OfProcess.of(emitting))
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join())
                .as("a Latin-1 user id is data, not a build error: only the ASCII status tokens are read")
                .doesNotThrowAnyException();
    }

    @Test
    public void accepts_a_verifier_named_by_path_rather_than_on_the_path() throws Exception {
        Path wrapper = root.resolve("gpg-wrapper");
        Files.writeString(wrapper, "#!/bin/sh\nexec gpgv \"$@\"\n");
        Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwx------"));
        assertThatCode(() -> new Signatures(serving(vaulted(vault)))
                .verification(Verification.DECLARED)
                .command(wrapper.toAbsolutePath().toString())
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join())
                .as("an absolute path to a wrapper names the verifier without changing the PATH")
                .doesNotThrowAnyException();
    }

    @Test
    public void reads_the_keyring_a_process_configuration_names() throws Exception {
        Files.createDirectory(input.resolve("process"));
        Files.writeString(input.resolve("process/gpgv.properties"),
                "--keyring=" + vault.resolve(fingerprint + ".gpg") + "\n");
        assertThatCode(() -> new Signatures(serving(vaulted(Files.createDirectory(root.resolve("bare")))))
                .verification(Verification.DECLARED)
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join())
                .as("process-gpgv.properties adds a keyring beside the one the build assembles")
                .doesNotThrowAnyException();
    }

    @Test
    public void rejects_a_signature_from_a_key_gpg_does_not_hold() throws Exception {
        Path stranger = Files.createDirectory(root.resolve("stranger"));
        Files.setPosixFilePermissions(stranger, PosixFilePermissions.fromString("rwx------"));
        assertThatThrownBy(() -> run(stranger)).hasMessageContaining("is not available");
    }
}
