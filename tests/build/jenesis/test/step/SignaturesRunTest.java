package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;
import build.jenesis.maven.MavenRepository;
import build.jenesis.step.Inventory;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIf("gpgAvailable")
public class SignaturesRunTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, input, home, jar, detached;
    private String fingerprint;

    static boolean gpgAvailable() {
        try {
            return new ProcessBuilder("gpg", "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor() == 0;
        } catch (IOException | InterruptedException _) {
            return false;
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
        home = Files.createDirectory(root.resolve("gnupg"));
        Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"));
        gpg("--quick-generate-key", "Jenesis Signature Test <test@example.invalid>", "default", "default", "never");
        fingerprint = fingerprint();
        jar = Files.writeString(input.resolve("lib.jar"), "artifact bytes\n");
        detached = root.resolve("lib.jar.asc");
        gpg("--detach-sign", "--armor", "--output", detached.toString(), jar.toString());
        SequencedProperties inventory = new SequencedProperties();
        inventory.setProperty("module.path", "");
        inventory.setProperty("module.dependency.0", "maven/org.example/lib/1.0 lib.jar");
        inventory.setProperty("module.dependency.0.scope", "compile");
        inventory.setProperty("module.dependency.0.group", "main");
        inventory.store(input.resolve(Inventory.INVENTORY));
    }

    private void gpg(String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("gpg",
                "--homedir", home.toString(),
                "--batch", "--yes", "--passphrase", "", "--pinentry-mode", "loopback"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectErrorStream(false)
                .start();
        process.getErrorStream().transferTo(OutputStream.nullOutputStream());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Failed to run gpg " + String.join(" ", arguments));
        }
    }

    private String fingerprint() throws Exception {
        Process process = new ProcessBuilder("gpg",
                "--homedir", home.toString(),
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

    private SequencedProperties run(Path signature, Verification verification) throws IOException {
        new Signatures(Map.of("maven", (MavenRepository) (_, _, _, _, _, _, checksum) ->
                Optional.ofNullable("asc".equals(checksum) && signature != null
                        ? RepositoryItem.ofFile(signature)
                        : null)), "")
                .verification(verification)
                .factory(ProcessHandler.OfProcess.of(List.of("gpg", "--homedir", home.toString())))
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        Path file = next.resolve(Signatures.SIGNATURES);
        return Files.isRegularFile(file) ? SequencedProperties.ofFiles(file) : new SequencedProperties();
    }

    @Test
    public void records_the_fingerprint_gpg_reports_for_a_genuine_signature() throws IOException {
        assertThat(run(detached, Verification.UNPINNED).getProperty("main/maven/org.example/lib"))
                .isEqualTo("OpenPGP/" + fingerprint);
    }

    @Test
    public void rejects_an_artifact_whose_bytes_changed_after_signing() throws Exception {
        Files.writeString(jar, "tampered bytes\n");
        assertThatThrownBy(() -> run(detached, Verification.UNPINNED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the artifact");
    }

    @Test
    public void rejects_a_signature_from_a_key_gpg_does_not_hold() throws Exception {
        Path stranger = Files.createDirectory(root.resolve("stranger"));
        Files.setPosixFilePermissions(stranger, PosixFilePermissions.fromString("rwx------"));
        assertThatThrownBy(() -> new Signatures(Map.of("maven", (MavenRepository) (_, _, _, _, _, _, checksum) ->
                Optional.ofNullable("asc".equals(checksum) ? RepositoryItem.ofFile(detached) : null)), "")
                .verification(Verification.UNPINNED)
                .factory(ProcessHandler.OfProcess.of(List.of("gpg", "--homedir", stranger.toString())))
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join())
                .hasMessageContaining("is not available");
    }
}
