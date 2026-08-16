package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenRepository;
import build.jenesis.maven.MavenVersionNegotiator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenDefaultVersionNegotiatorTest {

    static Stream<Arguments> versionPairs() {
        return Stream.of(
                pair("1", "1", 0),
                pair("1", "2", -1),
                pair("1.0", "1", 0),
                pair("1.0.0", "1", 0),
                pair("1.0.0", "1.0", 0),
                pair("1.0.1", "1.0", 1),
                pair("1.10", "1.2", 1),
                pair("1.10", "1.9", 1),
                pair("2.0", "1.99", 1),
                pair("1.0-alpha", "1.0", -1),
                pair("1.0-alpha-1", "1.0-alpha-2", -1),
                pair("1.0-alpha", "1.0-beta", -1),
                pair("1.0-beta", "1.0-milestone", -1),
                pair("1.0-milestone", "1.0-rc", -1),
                pair("1.0-rc", "1.0-cr", 0),
                pair("1.0-rc", "1.0-snapshot", -1),
                pair("1.0-snapshot", "1.0", -1),
                pair("1.0", "1.0-ga", 0),
                pair("1.0", "1.0-final", 0),
                pair("1.0", "1.0-release", 0),
                pair("1.0-ga", "1.0-final", 0),
                pair("1.0-ga", "1.0-release", 0),
                pair("1.0", "1.0-sp", -1),
                pair("1.0-sp", "1.0-sp1", -1),
                pair("1.0-sp", "1.0-sp-1", -1),
                pair("1.0-sp1", "1.0-sp2", -1),
                pair("1.0-foo", "1.0", 1),
                pair("1.0-foo", "1.0-bar", 1),
                pair("1.0-foo", "1.0-foo1", -1),
                pair("1.0-SNAPSHOT", "1.0-snapshot", 0),
                pair("1.0-RC", "1.0-rc", 0),
                pair("1.0-Alpha", "1.0-alpha", 0),
                pair("1.0a", "1.0-a", 0),
                pair("1.0a1", "1.0-alpha-1", 0),
                pair("1.0a1", "1.0a2", -1),
                pair("1.0rc1", "1.0-rc-1", 0),
                pair("1.0rc1", "1.0rc2", -1),
                pair("2.0a1", "1.0", 1),
                pair("1.0-alpha-1", "1.0-1", -1),
                pair("1.0-1", "1.0", 1),
                pair("1.0-1", "1.0.1", -1),
                pair("1.0", "1.0-1", -1),
                pair("1.0.0-1", "1.0-1", 0),
                pair("1-1", "1.1", -1),
                pair("1.0-SNAPSHOT", "1.0", -1),
                pair("1.0-SNAPSHOT", "1.0.0", -1),
                pair("1.0-SNAPSHOT", "1.1", -1),
                pair("1.0-SNAPSHOT", "1.0-rc1", 1),
                pair("1.0.0.0", "1.0", 0),
                pair("1.0.0.0.0", "1.0", 0),
                pair("1.0.0.1", "1.0", 1),
                pair("1.0.0.0.1", "1.0", 1),
                pair("1.0a", "1.0b", -1),
                pair("1.0pre", "1.0", 1),
                pair("99999999999999999999.1", "99999999999999999999.2", -1),
                pair("1.0.0", "1.0-ga", 0));
    }

    private static Arguments pair(String left, String right, int expected) {
        return Arguments.of(left, right, expected);
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @MethodSource("versionPairs")
    public void compares_like_maven(String left, String right, int expected) {
        assertThat(Integer.signum(MavenDefaultVersionNegotiator.compareVersions(left, right)))
                .as("compareVersions(%s, %s)", left, right)
                .isEqualTo(expected);
        assertThat(Integer.signum(MavenDefaultVersionNegotiator.compareVersions(right, left)))
                .as("compareVersions(%s, %s) reversed", right, left)
                .isEqualTo(-expected);
    }

    @Test
    public void resolves_latest_without_release() throws IOException {
        String resolved = closest().resolve(Runnable::run,
                metadata("<metadata><versioning><latest>1.0-SNAPSHOT</latest></versioning></metadata>"),
                "group",
                "artifact",
                null,
                null,
                "LATEST");
        assertThat(resolved).isEqualTo("1.0-SNAPSHOT");
    }

    @Test
    public void resolves_range_without_latest_and_release() throws IOException {
        String resolved = closest().resolve(Runnable::run,
                metadata("<metadata><versioning><versions>"
                        + "<version>1.0</version><version>2.0</version>"
                        + "</versions></versioning></metadata>"),
                "group",
                "artifact",
                null,
                null,
                "[1.0,2.0)");
        assertThat(resolved).isEqualTo("1.0");
    }

    @Test
    public void release_without_release_fails() {
        assertThatThrownBy(() -> closest().resolve(Runnable::run,
                metadata("<metadata><versioning><latest>1.0-SNAPSHOT</latest></versioning></metadata>"),
                "group",
                "artifact",
                null,
                null,
                "RELEASE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Property not defined: release");
    }

    @Test
    public void maven_and_closest_take_a_declared_version_verbatim() throws IOException {
        for (MavenVersionNegotiator negotiator : List.of(maven(), closest())) {
            assertThat(negotiator.resolve(Runnable::run,
                    unreadableMetadata(),
                    "group",
                    "artifact",
                    null,
                    null,
                    "1.0")).isEqualTo("1.0");
        }
    }

    @Test
    public void latest_and_release_ignore_the_declared_version() throws IOException {
        MavenRepository repository = metadata("<metadata><versioning>"
                + "<latest>3.0-SNAPSHOT</latest><release>2.0</release>"
                + "</versioning></metadata>");
        assertThat(latest().resolve(Runnable::run, repository, "group", "artifact", null, null, "1.0"))
                .isEqualTo("3.0-SNAPSHOT");
        assertThat(release().resolve(Runnable::run, repository, "group", "artifact", null, null, "1.0"))
                .isEqualTo("2.0");
    }

    @Test
    public void maven_and_closest_keep_the_nearest_version_when_no_range_competes() throws IOException {
        for (MavenVersionNegotiator negotiator : List.of(maven(), closest())) {
            assertThat(negotiator.resolve(Runnable::run,
                    unreadableMetadata(),
                    "group",
                    "artifact",
                    null,
                    null,
                    "1.0",
                    new LinkedHashSet<>(List.of("1.0", "2.0")))).isEqualTo("1.0");
        }
    }

    @Test
    public void maven_satisfies_every_competing_range_at_once() throws IOException {
        assertThat(maven().resolve(
                Runnable::run,
                versions("1.0", "2.0", "3.0"),
                "group",
                "artifact",
                null,
                null,
                "1.0",
                new LinkedHashSet<>(List.of("1.0", "[1.0,3.0)", "[2.0,4.0)"))))
                .as("3.0 satisfies the second range but not the first, and 1.0 is not the highest")
                .isEqualTo("2.0");
    }

    @Test
    public void maven_fails_when_no_version_satisfies_every_range() {
        assertThatThrownBy(() -> maven().resolve(
                Runnable::run,
                versions("1.0", "2.0", "3.0"),
                "group",
                "artifact",
                null,
                null,
                "1.0",
                new LinkedHashSet<>(List.of("[1.0,2.0)", "[2.0,3.0)"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not resolve version conflict for group:artifact");
    }

    @Test
    public void closest_leaves_a_competing_range_unheard() throws IOException {
        assertThat(closest().resolve(
                Runnable::run,
                unreadableMetadata(),
                "group",
                "artifact",
                null,
                null,
                "1.0",
                new LinkedHashSet<>(List.of("1.0", "[2.0,4.0)"))))
                .as("the nearest declaration stands and no metadata is consulted to argue with it")
                .isEqualTo("1.0");
    }

    @Test
    public void no_two_factories_supply_the_same_negotiator() throws IOException {
        Set<String> distinct = new HashSet<>();
        for (Supplier<MavenVersionNegotiator> supplier : factories()) {
            distinct.add(HexFormat.of().formatHex(serialize(supplier)));
        }
        assertThat(distinct).hasSize(4);
    }

    @Test
    public void every_factory_survives_a_serialization_round_trip() throws Exception {
        for (Supplier<MavenVersionNegotiator> supplier : factories()) {
            Object restored;
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialize(supplier)))) {
                restored = in.readObject();
            }
            assertThat(restored).isInstanceOf(Supplier.class);
            assertThat(((Supplier<?>) restored).get()).isInstanceOf(MavenVersionNegotiator.class);
        }
    }

    private static List<Supplier<MavenVersionNegotiator>> factories() {
        return List.of(MavenDefaultVersionNegotiator.maven(),
                MavenDefaultVersionNegotiator.latest(),
                MavenDefaultVersionNegotiator.release(),
                MavenDefaultVersionNegotiator.closest());
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static MavenVersionNegotiator maven() {
        return MavenDefaultVersionNegotiator.maven().get();
    }

    private static MavenVersionNegotiator latest() {
        return MavenDefaultVersionNegotiator.latest().get();
    }

    private static MavenVersionNegotiator release() {
        return MavenDefaultVersionNegotiator.release().get();
    }

    private static MavenVersionNegotiator closest() {
        return MavenDefaultVersionNegotiator.closest().get();
    }

    private static MavenRepository versions(String... versions) {
        StringBuilder xml = new StringBuilder("<metadata><versioning><versions>");
        for (String version : versions) {
            xml.append("<version>").append(version).append("</version>");
        }
        return metadata(xml.append("</versions></versioning></metadata>").toString());
    }

    private static MavenRepository unreadableMetadata() {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) {
                return Optional.empty();
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) {
                throw new AssertionError("Fetched metadata for " + groupId + ":" + artifactId);
            }
        };
    }

    private static MavenRepository metadata(String xml) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) {
                return Optional.empty();
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) {
                return Optional.of(() -> new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            }
        };
    }
}
