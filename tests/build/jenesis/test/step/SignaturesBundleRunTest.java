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
import build.jenesis.step.Signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SignaturesBundleRunTest {

    private static final String COORDINATE =
            "https://repo1.maven.org/maven2/dev/sigstore/protobuf-specs/0.5.2/protobuf-specs-0.5.2";

    @TempDir
    private Path root;
    private Path next, supplement, input;

    @BeforeEach
    public void setUp() throws Exception {
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
        Path artifact = downloaded(".jar", "protobuf-specs-0.5.2.jar");
        downloaded(".jar.sigstore.json", "bundle.sigstore.json");
        downloaded(".pom", "pom.xml");
        downloaded(".pom.sigstore.json", "pom.sigstore.json");
        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("main/compile/maven/dev.sigstore/protobuf-specs/0.5.2",
                input.relativize(artifact).toString());
        dependencies.store(input.resolve(BuildStep.DEPENDENCIES));
    }

    @Test
    public void verifies_a_dependency_against_the_identity_a_declaration_names() throws Exception {
        declared("Sigstore/github.com/sigstore/protobuf-specs");
        assertThat(printed())
                .as("the declaration names the repository, the certificate names the release workflow in it")
                .contains("[VERIFIED]")
                .contains("https://github.com/sigstore/protobuf-specs/.github/workflows/java-release.yml");
    }

    @Test
    public void reads_a_trust_root_a_project_names_instead_of_the_one_it_carries() throws Exception {
        declared("Sigstore/github.com/sigstore/protobuf-specs");
        Path named = Files.writeString(root.resolve("trusted-root.json"),
                "{\"certificateAuthorities\":[],\"tlogs\":[]}");
        assertThatThrownBy(() -> run(step().trustedRoot(named.toUri())))
                .as("a named trust root replaces the one the tool carries, vouching for nothing if it says so")
                .hasMessageContaining("No certificate authority of the trust root was in use at");
    }

    @Test
    public void reads_a_bundle_without_ever_forking_a_signature_command() throws Exception {
        declared("Sigstore/github.com/sigstore/protobuf-specs");
        assertThatCode(() -> run(step().factory(_ -> {
            throw new IllegalStateException("a signature command was forked");
        }))).doesNotThrowAnyException();
    }

    @Test
    public void derives_an_issuer_from_the_host_where_no_pair_names_one() throws Exception {
        declared("Sigstore/github.com/sigstore/protobuf-specs");
        assertThatThrownBy(() -> run(step().issuers("")))
                .as("github.com is the exception its identities are issued away from, and the issuer is checked")
                .hasMessageContaining("issued by https://token.actions.githubusercontent.com"
                        + " rather than https://github.com")
                .hasMessageContaining("-Djenesis.sigstore.issuers=github.com"
                        + "=token.actions.githubusercontent.com");
    }

    @Test
    public void names_an_issuer_the_way_a_host_is_named_everywhere_else() throws Exception {
        declared("Sigstore/github.com/sigstore/protobuf-specs");
        assertThatThrownBy(() -> run(step().issuers("github.com=https://token.actions.githubusercontent.com")))
                .as("a scheme in a pair is the spelling of the other side, so it is refused rather than trimmed")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without a scheme: github.com=token.actions.githubusercontent.com");
    }

    @Test
    public void refuses_an_identity_that_only_begins_like_the_declared_repository() throws Exception {
        declared("Sigstore/github.com/sigstore/protobuf-spec");
        assertThatThrownBy(this::run)
                .as("a declaration covers a path below it, not a repository whose name merely starts alike")
                .hasMessageContaining("add Sigstore/github.com/sigstore/protobuf-specs");
    }

    @Test
    public void refuses_an_identity_of_another_repository_of_the_same_owner() throws Exception {
        declared("Sigstore/github.com/sigstore/sigstore-java");
        assertThatThrownBy(this::run)
                .hasMessageContaining("signed by https://github.com/sigstore/protobuf-specs");
    }

    @Test
    public void accepts_every_repository_of_an_owner_when_the_declaration_stops_there() throws Exception {
        declared("Sigstore/github.com/sigstore");
        assertThat(printed()).contains("[VERIFIED]");
    }

    private Path downloaded(String extension, String name) throws IOException {
        Path target = input.resolve(name);
        try (InputStream stream = URI.create(COORDINATE + extension).toURL().openStream()) {
            return Files.write(target, stream.readAllBytes());
        }
    }

    private void declared(String identity) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty(identity, "main/maven/dev.sigstore/*");
        properties.store(input.resolve(BuildStep.SIGNATURES));
    }

    private Signatures step() {
        MavenRepository repository = (_, _, _, _, type, _, extension) -> Optional.ofNullable(switch (type) {
            case "jar" -> "sigstore.json".equals(extension) ? input.resolve("bundle.sigstore.json") : null;
            case "pom" -> "sigstore.json".equals(extension)
                    ? input.resolve("pom.sigstore.json")
                    : input.resolve("pom.xml");
            default -> null;
        }).map(RepositoryItem::ofFile);
        return new Signatures(Map.of("maven", repository)).verification(Verification.DECLARED);
    }

    private void run() throws IOException {
        run(step());
    }

    private void run(Signatures signatures) throws IOException {
        signatures.apply(Runnable::run,
                        new BuildStepContext(root.resolve("previous"), next, supplement),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
    }

    private String printed() throws IOException {
        StringBuilder captured = new StringBuilder();
        run(step().printing(line -> captured.append(line).append(System.lineSeparator())));
        return captured.toString();
    }
}
