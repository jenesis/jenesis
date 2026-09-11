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

public class SignaturesTest {

    private static final String PRIMARY = "B4D5C1E7000000000000000000000000000000AA";
    private static final String SUBKEY = "00000000000000000000000000000000000000BB";

    @TempDir
    private Path root;
    private Path previous, next, supplement, input, artifacts;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
        artifacts = Files.createDirectory(root.resolve("artifacts"));
    }

    private void resolved(String coordinate, String version, String checksum) throws IOException {
        Path file = input.resolve(Inventory.INVENTORY);
        SequencedProperties properties = Files.isRegularFile(file)
                ? SequencedProperties.ofFiles(file)
                : new SequencedProperties();
        int index = (int) properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("module.dependency."))
                .filter(name -> name.indexOf('.', "module.dependency.".length()) < 0)
                .count();
        Path jar = Files.createFile(artifacts.resolve(index + ".jar"));
        properties.setProperty("module.path", "");
        properties.setProperty("module.dependency." + index, coordinate
                + "/" + version
                + " " + input.relativize(jar)
                + (checksum == null ? "" : " " + checksum));
        properties.setProperty("module.dependency." + index + ".scope", "compile");
        properties.setProperty("module.dependency." + index + ".group", "main");
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

    private SequencedProperties run(Signatures signatures) throws IOException {
        signatures.apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        Path file = next.resolve(Signatures.SIGNATURES);
        return Files.isRegularFile(file) ? SequencedProperties.ofFiles(file) : new SequencedProperties();
    }

    private String report() throws IOException {
        Path file = next.resolve("reports/signatures/signatures.txt");
        return Files.isRegularFile(file) ? Files.readString(file) : "";
    }

    private static SequencedMap<String, SequencedSet<String>> declared(String fingerprint, String... tokens) {
        SequencedMap<String, SequencedSet<String>> declared = new LinkedHashMap<>();
        declared.put(fingerprint, new LinkedHashSet<>(List.of(tokens)));
        return declared;
    }

    private Signatures step(Path signature, String... status) {
        return new Signatures(Map.of("maven", publishing(signature)), "")
                .verification(Verification.UNPINNED)
                .factory(reporting(status));
    }

    @Test
    public void records_the_primary_key_that_signed_an_unpinned_artifact() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        SequencedProperties recorded = run(step(signature("lib"), validated(PRIMARY)));
        assertThat(recorded.getProperty("main/maven/org.example/lib"))
                .as("the primary key fingerprint is recorded, not the signing subkey")
                .isEqualTo("OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_an_artifact_signed_by_a_key_other_than_the_declared_one() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))
                .declared(declared("OpenPGP/" + SUBKEY, "main/maven/org.example/lib"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenPGP/" + PRIMARY)
                .hasMessageContaining("OpenPGP/" + SUBKEY)
                .hasMessageContaining("add OpenPGP/" + PRIMARY + " to a @jenesis.signature line");
    }

    @Test
    public void accepts_an_artifact_signed_by_the_declared_key() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        SequencedProperties recorded = run(step(signature("lib"), validated(PRIMARY))
                .declared(declared("OpenPGP/" + PRIMARY, "main/maven/org.example/lib")));
        assertThat(recorded.stringPropertyNames())
                .as("a coordinate an existing declaration already accepts needs no new line")
                .isEmpty();
        assertThat(report()).contains("main/maven/org.example/lib OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_an_artifact_whose_signature_does_not_match() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"), "[GNUPG:] BADSIG DEADBEEF Example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match the file");
    }

    @Test
    public void rejects_an_artifact_signed_by_a_revoked_key_despite_a_valid_signature() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"),
                validated(PRIMARY),
                "[GNUPG:] REVKEYSIG DEADBEEF Example")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    public void names_the_missing_public_key_rather_than_recording_nothing() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"),
                "[GNUPG:] ERRSIG DEADBEEF 1 8 00 1000 9 DEADBEEF",
                "[GNUPG:] NO_PUBKEY DEADBEEF")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEADBEEF is not available");
    }

    @Test
    public void tolerates_an_artifact_without_a_published_signature() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThat(run(step(null, validated(PRIMARY))).stringPropertyNames()).isEmpty();
    }

    @Test
    public void rejects_an_artifact_without_a_published_signature_when_strict() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(null, validated(PRIMARY)).verification(Verification.STRICT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no detached signature is published");
    }

    @Test
    public void skips_a_coordinate_that_arrived_with_a_pin_checksum() throws IOException {
        resolved("maven/org.example/lib", "1.0", "SHA-256/cafebabe");
        assertThat(run(step(signature("lib"), validated(PRIMARY))).stringPropertyNames()).isEmpty();
    }

    @Test
    public void verifies_a_pinned_coordinate_when_every_artifact_is_requested() throws IOException {
        resolved("maven/org.example/lib", "1.0", "SHA-256/cafebabe");
        SequencedProperties recorded = run(step(signature("lib"), validated(PRIMARY))
                .verification(Verification.ALL));
        assertThat(recorded.getProperty("main/maven/org.example/lib")).isEqualTo("OpenPGP/" + PRIMARY);
    }

    @Test
    public void verifies_nothing_when_switched_off() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThat(run(step(signature("lib"), validated(PRIMARY)).verification(Verification.NONE))
                .stringPropertyNames()).isEmpty();
    }

    @Test
    public void writes_a_report_naming_every_verified_coordinate() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        run(step(signature("lib"), validated(PRIMARY)));
        assertThat(next.resolve("reports/signatures/signatures.txt")).content()
                .contains("main/maven/org.example/lib OpenPGP/" + PRIMARY);
    }

    @Test
    public void accepts_a_key_listed_for_every_artifact_of_a_maven_group() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        SequencedProperties recorded = run(step(signature("lib"), validated(PRIMARY))
                .declared(declared("OpenPGP/" + PRIMARY, "main/maven/org.example/*")));
        assertThat(recorded.stringPropertyNames())
                .as("a coordinate an existing declaration already accepts needs no new line")
                .isEmpty();
        assertThat(report()).contains("main/maven/org.example/lib OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_a_key_outside_the_one_listed_for_a_maven_group() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))
                .declared(declared("OpenPGP/" + SUBKEY, "main/maven/org.example/*"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only OpenPGP/" + SUBKEY + " is declared for it");
    }

    @Test
    public void accepts_any_of_the_keys_declared_for_one_coordinate() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        SequencedMap<String, SequencedSet<String>> declared = declared("OpenPGP/" + SUBKEY,
                "main/maven/org.example/lib");
        declared.put("OpenPGP/" + PRIMARY, new LinkedHashSet<>(List.of("main/maven/org.example/lib")));
        run(step(signature("lib"), validated(PRIMARY)).declared(declared));
        assertThat(report())
                .as("a rotation is accepted by adding the new key, not by removing the old one")
                .contains("main/maven/org.example/lib OpenPGP/" + PRIMARY);
    }

    @Test
    public void a_group_wildcard_does_not_reach_a_different_group() throws IOException {
        resolved("maven/org.other/lib", "1.0", null);
        SequencedProperties recorded = run(step(signature("lib"), validated(PRIMARY))
                .declared(declared("OpenPGP/" + SUBKEY, "main/maven/org.example/*")));
        assertThat(recorded.getProperty("main/maven/org.other/lib"))
                .as("org.other is undeclared, so its signer is recorded rather than rejected")
                .isEqualTo("OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_a_wildcard_that_does_not_name_a_maven_group() {
        assertThatThrownBy(() -> Signatures.declare(new LinkedHashMap<>(),
                "OpenPGP/" + PRIMARY,
                "main/module/some.module/*",
                "@jenesis.signature OpenPGP/x main/module/some.module/*",
                "module-info.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<groupId>/* or <group>/maven/<groupId>/*");
    }

    @Test
    public void rejects_a_declaration_that_names_no_coordinate() {
        assertThatThrownBy(() -> Signatures.declare(new LinkedHashMap<>(),
                "OpenPGP/" + PRIMARY,
                null,
                "@jenesis.signature OpenPGP/x",
                "module-info.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected <algorithm>/<fingerprint> <token>...");
    }

    @Test
    public void rejects_a_scheme_other_than_openpgp() {
        assertThatThrownBy(() -> Signatures.declare(new LinkedHashMap<>(),
                "PGP/" + PRIMARY,
                "org.example/lib",
                "@jenesis.signature PGP/x org.example/lib",
                "module-info.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown signature scheme 'PGP'")
                .hasMessageContaining("expected OpenPGP");
    }

    @Test
    public void rejects_a_fingerprint_that_is_not_hexadecimal() {
        assertThatThrownBy(() -> Signatures.declare(new LinkedHashMap<>(),
                "OpenPGP/not-a-fingerprint",
                "org.example/lib",
                "@jenesis.signature OpenPGP/not-a-fingerprint org.example/lib",
                "module-info.java"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected hexadecimal characters only");
    }

    @Test
    public void normalises_a_declared_fingerprint_to_upper_case() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        run(step(signature("lib"), validated(PRIMARY))
                .declared(declared("openpgp/" + PRIMARY.toLowerCase(Locale.ROOT), "main/maven/org.example/lib")));
        assertThat(report())
                .as("a lower-case declaration is the same key, not a contradiction")
                .contains("main/maven/org.example/lib OpenPGP/" + PRIMARY);
    }

    @Test
    public void accepts_a_pom_signed_by_the_key_that_signed_the_artifact() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        Path pom = Files.writeString(root.resolve("lib.pom"), "<project/>");
        SequencedProperties recorded = run(new Signatures(
                Map.of("maven", publishing(signature("lib"), pom, signature("pom"))), "")
                .verification(Verification.UNPINNED)
                .factory(reporting(List.of(validated(PRIMARY)), List.of(validated(PRIMARY)))));
        assertThat(recorded.getProperty("main/maven/org.example/lib")).isEqualTo("OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_a_pom_signed_by_a_key_other_than_the_artifact_key() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        Path pom = Files.writeString(root.resolve("lib.pom"), "<project/>");
        assertThatThrownBy(() -> run(new Signatures(
                Map.of("maven", publishing(signature("lib"), pom, signature("pom"))), "")
                .verification(Verification.UNPINNED)
                .factory(reporting(List.of(validated(PRIMARY)), List.of(validated(SUBKEY))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("its POM is signed by OpenPGP/" + SUBKEY)
                .hasMessageContaining("but its artifact by OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_a_pom_whose_signature_does_not_match_it() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        Path pom = Files.writeString(root.resolve("lib.pom"), "<project/>");
        assertThatThrownBy(() -> run(new Signatures(
                Map.of("maven", publishing(signature("lib"), pom, signature("pom"))), "")
                .verification(Verification.UNPINNED)
                .factory(reporting(List.of(validated(PRIMARY)),
                        List.of("[GNUPG:] BADSIG DEADBEEF Example")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("for its POM, the signature does not match the file")
                .hasMessageContaining("re-serialises POMs");
    }

    @Test
    public void tolerates_an_unsigned_pom_unless_strict() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThat(run(step(signature("lib"), validated(PRIMARY)))
                .getProperty("main/maven/org.example/lib")).isEqualTo("OpenPGP/" + PRIMARY);
    }

    @Test
    public void rejects_an_unsigned_pom_beside_a_signed_artifact_when_strict() throws IOException {
        resolved("maven/org.example/lib", "1.0", null);
        assertThatThrownBy(() -> run(step(signature("lib"), validated(PRIMARY))
                .verification(Verification.STRICT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("its artifact is signed but its POM publishes no signature");
    }
}
