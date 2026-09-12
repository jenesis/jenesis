package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;
import build.jenesis.maven.MavenRepository;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Signatures;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SignaturesTest {

    private static final String PRIMARY = "B4D5C1E7000000000000000000000000000000AA";
    private static final String SUBKEY = "00000000000000000000000000000000000000BB";

    @TempDir
    private Path root;
    private Path previous, next, supplement, input;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
    }

    private void resolved(String coordinate, String version, String checksum) throws IOException {
        Path file = input.resolve(BuildStep.DEPENDENCIES);
        SequencedProperties properties = Files.isRegularFile(file)
                ? SequencedProperties.ofFiles(file)
                : new SequencedProperties();
        Path jar = Files.createFile(input.resolve(coordinate.replace('/', '.') + "-" + version + ".jar"));
        properties.setProperty("main/compile/" + coordinate + "/" + version,
                input.relativize(jar) + (checksum == null ? "" : " " + checksum));
        properties.store(file);
    }

    private void declared(String fingerprint, String... tokens) throws IOException {
        Path file = input.resolve(BuildStep.SIGNATURES);
        SequencedProperties properties = Files.isRegularFile(file)
                ? SequencedProperties.ofFiles(file)
                : new SequencedProperties();
        properties.setProperty(fingerprint, String.join(" ", tokens));
        properties.store(file);
    }

    private Path signature(String name) throws IOException {
        return Files.writeString(root.resolve(name + ".asc"), "-----BEGIN PGP SIGNATURE-----\n");
    }

    private static MavenRepository publishing(Path signature) {
        return publishing(signature, null, null);
    }

    private static MavenRepository publishing(Path signature, Path pom, Path pomSignature) {
        return (_, _, _, _, type, _, checksum) -> {
            Path served = switch (type) {
                case "jar" -> "asc".equals(checksum) ? signature : null;
                case "pom" -> "asc".equals(checksum) ? pomSignature : pom;
                default -> null;
            };
            return Optional.ofNullable(served == null ? null : RepositoryItem.ofFile(served));
        };
    }

    private static Function<List<String>, ProcessHandler> reporting(String... status) {
        return reporting(List.of(status));
    }

    @SafeVarargs
    private static Function<List<String>, ProcessHandler> reporting(List<String>... rounds) {
        AtomicInteger round = new AtomicInteger();
        return ProcessHandler.OfTool.of(new ToolProvider() {
            @Override
            public String name() {
                return "gpg";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                List<String> status = rounds[Math.min(round.getAndIncrement(), rounds.length - 1)];
                status.forEach(out::println);
                return status.isEmpty() ? 1 : 0;
            }
        });
    }

    private static String validated(String fingerprint) {
        return "[GNUPG:] VALIDSIG " + SUBKEY + " 2026-09-11 1000 0 4 0 1 8 00 " + fingerprint;
    }

    private void run(Signatures signatures) throws IOException {
        signatures.apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
    }

    private Signatures step(Path signature, String... status) {
        return new Signatures(Map.of("maven", publishing(signature)))
                .verification(Verification.DECLARED)
                .factory(reporting(status));
    }

    @Test
    public void accepts_an_artifact_signed_by_the_declared_key() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY))))
                .as("the primary key fingerprint is compared, not the signing subkey")
                .doesNotThrowAnyException();
    }

    @Test
    public void rejects_an_artifact_signed_by_a_key_other_than_the_declared_one() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + SUBKEY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenPGP/" + PRIMARY)
                .hasMessageContaining("OpenPGP/" + SUBKEY)
                .hasMessageContaining("add OpenPGP/" + PRIMARY + " to a @jenesis.signature line");
    }

    @Test
    public void rejects_an_artifact_whose_signature_does_not_match() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(signature("lib"), "[GNUPG:] BADSIG DEADBEEF Example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the file");
    }

    @Test
    public void rejects_an_artifact_signed_by_a_revoked_key_despite_a_valid_signature() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(signature("lib"),
                validated(PRIMARY),
                "[GNUPG:] REVKEYSIG DEADBEEF Example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    public void names_the_missing_public_key_rather_than_passing_silently() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(signature("lib"),
                "[GNUPG:] ERRSIG DEADBEEF 1 8 00 1000 9 DEADBEEF",
                "[GNUPG:] NO_PUBKEY DEADBEEF")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEADBEEF is not available");
    }

    @Test
    public void rejects_a_declared_coordinate_that_publishes_no_signature() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(null, validated(PRIMARY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no signature is published");
    }

    @Test
    public void ignores_a_coordinate_no_declaration_covers() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY))))
                .as("declared verifies what is declared and leaves the rest alone")
                .doesNotThrowAnyException();
    }

    @Test
    public void rejects_a_coordinate_no_declaration_covers_when_strict() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))
                .verification(Verification.STRICT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no @jenesis.signature line declares a key for it");
    }

    @Test
    public void verifies_a_coordinate_that_arrived_with_a_pin_checksum() throws IOException {
        resolved("maven/org.example/lib", "1.0", "SHA-256/cafebabe");
        declared("OpenPGP/" + SUBKEY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))))
                .as("a pin records which bytes arrived, never who produced them")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void verifies_nothing_when_switched_off() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + SUBKEY, "main/maven/org.example/lib");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY))
                .verification(Verification.NONE)))
                .doesNotThrowAnyException();
    }

    @Test
    public void accepts_a_key_listed_for_every_artifact_of_a_maven_group() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/*");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY)))).doesNotThrowAnyException();
    }

    @Test
    public void rejects_a_key_outside_the_one_listed_for_a_maven_group() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + SUBKEY, "main/maven/org.example/*");
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only OpenPGP/" + SUBKEY + " is declared for it");
    }

    @Test
    public void accepts_any_of_the_keys_declared_for_one_coordinate() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + SUBKEY, "main/maven/org.example/lib");
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY))))
                .as("a rotation is accepted by adding the new key, not by removing the old one")
                .doesNotThrowAnyException();
    }

    @Test
    public void a_group_wildcard_does_not_reach_a_different_group() throws IOException {
        resolved("maven/org.other/lib", "1.0", null);
        declared("OpenPGP/" + SUBKEY, "main/maven/org.example/*");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY))))
                .as("org.other is undeclared, so nothing claims to know its signer")
                .doesNotThrowAnyException();
    }

    @Test
    public void matches_a_declared_fingerprint_regardless_of_case() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("openpgp/" + PRIMARY.toLowerCase(Locale.ROOT), "main/maven/org.example/lib");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY))))
                .as("a lower-case declaration is the same key, not a contradiction")
                .doesNotThrowAnyException();
    }

    @Test
    public void accepts_a_pom_signed_by_the_key_that_signed_the_artifact() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        Path pom = Files.writeString(root.resolve("lib.pom"), "<project/>");
        assertThatCode(() -> run(new Signatures(
                Map.of("maven", publishing(signature("lib"), pom, signature("pom"))))
                .verification(Verification.DECLARED)
                .factory(reporting(List.of(validated(PRIMARY)), List.of(validated(PRIMARY))))))
                .doesNotThrowAnyException();
    }

    @Test
    public void rejects_a_pom_signed_by_a_key_other_than_the_artifact_key() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        Path pom = Files.writeString(root.resolve("lib.pom"), "<project/>");
        assertThatThrownBy(() -> run(new Signatures(
                Map.of("maven", publishing(signature("lib"), pom, signature("pom"))))
                .verification(Verification.DECLARED)
                .factory(reporting(List.of(validated(PRIMARY)), List.of(validated(SUBKEY))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("its POM is signed by OpenPGP/" + SUBKEY)
                .hasMessageContaining("but its artifact by OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_a_pom_whose_signature_does_not_match_it() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        Path pom = Files.writeString(root.resolve("lib.pom"), "<project/>");
        assertThatThrownBy(() -> run(new Signatures(
                Map.of("maven", publishing(signature("lib"), pom, signature("pom"))))
                .verification(Verification.DECLARED)
                .factory(reporting(List.of(validated(PRIMARY)),
                        List.of("[GNUPG:] BADSIG DEADBEEF Example")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("for its POM, the signature does not match the file")
                .hasMessageContaining("re-serialises POMs");
    }

    @Test
    public void tolerates_an_unsigned_pom_unless_strict() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatCode(() -> run(step(signature("lib"), validated(PRIMARY)))).doesNotThrowAnyException();
    }

    @Test
    public void rejects_an_unsigned_pom_beside_a_signed_artifact_when_strict() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib");
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))
                .verification(Verification.STRICT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("its artifact is signed but its POM publishes no signature");
    }
}
