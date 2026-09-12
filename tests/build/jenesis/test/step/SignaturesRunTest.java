package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;
import build.jenesis.maven.MavenRepository;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Signatures;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIf("gpgAvailable")
public class SignaturesRunTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, input, home, jar, detached;
    private String fingerprint;

    static boolean gpgAvailable() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
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
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/compile/maven/org.example/lib/1.0", "lib.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties declarations = new SequencedProperties();
        declarations.setProperty("OpenPGP/" + fingerprint, "main/maven/org.example/lib");
        declarations.store(input.resolve(BuildStep.SIGNATURES));
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

    private void run(Path keyring) throws IOException {
        Map<String, Repository> repositories = Map.of("maven", (MavenRepository) (_, _, _, _, type, _, checksum) ->
                Optional.ofNullable("jar".equals(type) && "asc".equals(checksum)
                        ? RepositoryItem.ofFile(detached)
                        : null));
        new Signatures(repositories)
                .verification(Verification.DECLARED)
                .factory(ProcessHandler.OfProcess.of(List.of("gpg", "--homedir", keyring.toString())))
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
        assertThatCode(() -> run(home)).doesNotThrowAnyException();
    }

    @Test
    public void rejects_an_artifact_whose_bytes_changed_after_signing() throws Exception {
        Files.writeString(jar, "tampered bytes\n");
        assertThatThrownBy(() -> run(home))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the file");
    }

    @Test
    public void rejects_a_signature_from_a_key_gpg_does_not_hold() throws Exception {
        Path stranger = Files.createDirectory(root.resolve("stranger"));
        Files.setPosixFilePermissions(stranger, PosixFilePermissions.fromString("rwx------"));
        assertThatThrownBy(() -> run(stranger)).hasMessageContaining("is not available");
    }
}
