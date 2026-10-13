package build.jenesis.test.maven;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenDefaultRepositoryTest {

    private final Map<String, String> settings = new HashMap<>();

    @TempDir
    private Path repository, local, result;

    @Test
    public void cached_repository_prepended_with_overlay_resolves_sibling_without_caching() throws IOException {
        Path sibling = local.resolve("sibling.jar");
        Files.writeString(sibling, "sibling-content");
        Repository overlay = (_, coordinate, _) -> coordinate.equals("group/artifact/1")
                ? Optional.of(RepositoryItem.ofFile(sibling))
                : Optional.empty();
        int[] remoteCalls = {0};
        MavenRepository remote = (_, _, _, _, _, _, _) -> {
            remoteCalls[0]++;
            return Optional.empty();
        };
        Path cache = Files.createDirectory(result.resolve("cache"));
        MavenRepository merged = remote.cached(cache).prepend(overlay);

        Optional<RepositoryItem> first = merged.fetch(Runnable::run, "group", "artifact", "1", "jar", null, null);
        assertThat(first).isPresent();
        assertThat(first.get().file()).contains(sibling);
        assertThat(remoteCalls[0]).isZero();
        try (Stream<Path> stream = Files.list(cache)) {
            assertThat(stream).as("sibling must not enter the upstream cache").isEmpty();
        }

        Files.writeString(sibling, "updated-content");
        Optional<RepositoryItem> second = merged.fetch(Runnable::run, "group", "artifact", "1", "jar", null, null);
        assertThat(second).isPresent();
        assertThat(Files.readString(second.get().file().orElseThrow())).isEqualTo("updated-content");
        assertThat(remoteCalls[0]).isZero();
    }

    @Test
    public void cached_repository_prepended_with_overlay_falls_through_to_remote_on_overlay_miss() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "remote-content");
        Repository emptyOverlay = (_, _, _) -> Optional.empty();
        Path cache = Files.createDirectory(result.resolve("cache"));
        MavenRepository merged = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)
                .cached(cache)
                .prepend(emptyOverlay);

        Optional<RepositoryItem> first = merged.fetch(Runnable::run, "group", "artifact", "1", "jar", null, null);
        assertThat(first).isPresent();
        try (InputStream stream = first.get().toInputStream()) {
            assertThat(new String(stream.readAllBytes())).isEqualTo("remote-content");
        }
        try (Stream<Path> stream = Files.list(cache)) {
            assertThat(stream).as("cache must populate for upstream fetches").isNotEmpty();
        }
    }

    @Test
    public void can_fetch_dependency() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
    }

    @Test
    public void can_fetch_and_cache_dependency() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), local, Map.of(), null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).content().isEqualTo("foo");
    }

    @Test
    public void resolves_a_snapshot_by_the_timestamped_file_its_metadata_names() throws IOException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact/1.0-SNAPSHOT"));
        Files.writeString(folder.resolve("maven-metadata.xml"), """
                <metadata modelVersion="1.1.0">
                  <groupId>group</groupId>
                  <artifactId>artifact</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <versioning>
                    <snapshot>
                      <timestamp>20261001.194937</timestamp>
                      <buildNumber>10</buildNumber>
                    </snapshot>
                    <snapshotVersions>
                      <snapshotVersion>
                        <classifier>sources</classifier>
                        <extension>jar</extension>
                        <value>1.0-20261001.194937-9</value>
                      </snapshotVersion>
                      <snapshotVersion>
                        <extension>jar</extension>
                        <value>1.0-20261001.194937-10</value>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                </metadata>
                """);
        Files.writeString(folder.resolve("artifact-1.0-20261001.194937-10.jar"), "binary");
        Files.writeString(folder.resolve("artifact-1.0-20261001.194937-9-sources.jar"), "sources");
        MavenDefaultRepository mavenRepository = new MavenDefaultRepository(repository.toUri(), local, Map.of(), null);
        try (InputStream inputStream = mavenRepository.fetch(Runnable::run, "group", "artifact", "1.0-SNAPSHOT", "jar", null, null)
                .orElseThrow()
                .toInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("binary");
        }
        try (InputStream inputStream = mavenRepository.fetch(Runnable::run, "group", "artifact", "1.0-SNAPSHOT", "jar", "sources", null)
                .orElseThrow()
                .toInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("sources");
        }
        assertThat(local.resolve("group/artifact/1.0-SNAPSHOT/artifact-1.0-20261001.194937-10.jar"))
                .as("a timestamped snapshot never changes, so it is cached under its own name")
                .content().isEqualTo("binary");
    }

    @Test
    public void resolves_a_snapshot_by_the_timestamp_and_build_number_of_its_metadata() throws IOException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact/1.0-SNAPSHOT"));
        Files.writeString(folder.resolve("maven-metadata.xml"), """
                <metadata>
                  <versioning>
                    <snapshot>
                      <timestamp>20261001.194937</timestamp>
                      <buildNumber>3</buildNumber>
                    </snapshot>
                  </versioning>
                </metadata>
                """);
        Files.writeString(folder.resolve("artifact-1.0-20261001.194937-3.pom"), "<project/>");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)
                .fetch(Runnable::run, "group", "artifact", "1.0-SNAPSHOT", "pom", null, null)
                .orElseThrow()
                .toInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<project/>");
        }
    }

    @Test
    public void prefers_a_snapshot_the_local_repository_holds() throws IOException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact/1.0-SNAPSHOT"));
        Files.writeString(folder.resolve("maven-metadata.xml"), """
                <metadata>
                  <versioning>
                    <snapshot>
                      <timestamp>20261001.194937</timestamp>
                      <buildNumber>3</buildNumber>
                    </snapshot>
                  </versioning>
                </metadata>
                """);
        Files.writeString(folder.resolve("artifact-1.0-20261001.194937-3.jar"), "remote");
        Files.writeString(Files.createDirectories(local.resolve("group/artifact/1.0-SNAPSHOT")).resolve("artifact-1.0-SNAPSHOT.jar"),
                "installed");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), local, Map.of(), null)
                .fetch(Runnable::run, "group", "artifact", "1.0-SNAPSHOT", "jar", null, null)
                .orElseThrow()
                .toInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("installed");
        }
    }

    @Test
    public void resolves_a_snapshot_by_its_plain_name_without_metadata() throws IOException {
        Files.writeString(Files.createDirectories(repository.resolve("group/artifact/1.0-SNAPSHOT")).resolve("artifact-1.0-SNAPSHOT.jar"),
                "plain");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)
                .fetch(Runnable::run, "group", "artifact", "1.0-SNAPSHOT", "jar", null, null)
                .orElseThrow()
                .toInputStream()) {
            assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("plain");
        }
    }

    @Test
    public void fetches_without_caching_when_local_repository_is_read_only() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        local.toFile().setWritable(false, false);
        try {
            Assumptions.assumeFalse(Files.isWritable(local), "read-only enforcement requires a non-root user");
            Path dependency = result.resolve("dependency.jar");
            try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), local, Map.of(), null)
                    .fetch(Runnable::run, "group", "artifact", "1", "jar", null, null).orElseThrow().toInputStream()) {
                Files.copy(inputStream, dependency);
            }
            assertThat(dependency).content().isEqualTo("foo");
            assertThat(local.resolve("group/artifact/1/artifact-1.jar")).doesNotExist();
        } finally {
            local.toFile().setWritable(true, false);
        }
    }

    @Test
    public void serves_a_cached_file_without_its_remote_checksum_and_downloads_nothing_while_offline()
            throws IOException {
        Files.writeString(Files.createDirectories(local.resolve("group/artifact/1")).resolve("artifact-1.jar"), "cached");
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            URI remote = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
            MavenDefaultRepository offline = new MavenDefaultRepository(remote, local, Map.of("SHA1", remote), null)
                    .connection(new Repository.Connection().insecure(true).offline(true));

            try (InputStream inputStream = offline.fetch(Runnable::run, "group", "artifact", "1", "jar", null, null)
                    .orElseThrow()
                    .toInputStream()) {
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("cached");
            }
            assertThatThrownBy(() -> offline.fetch(Runnable::run, "group", "artifact", "2", "jar", null, null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining(remote + "group/artifact/2/artifact-2.jar")
                    .hasMessageContaining("while offline");
            assertThat(hits.get()).as("neither the checksum nor the missing artifact is asked for").isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void resolves_metadata_offline_from_the_copy_an_earlier_resolution_stored() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<metadata/>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI remote = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
            Path store = result.resolve("store");
            Repository.Connection online = new Repository.Connection().insecure(true);
            new MavenDefaultRepository(remote, null, Map.of(), null).connection(online).cached(store)
                    .fetchMetadata(Runnable::run, "group", "artifact", null);
            server.stop(0);

            try (InputStream inputStream = new MavenDefaultRepository(remote, null, Map.of(), null)
                    .connection(online.offline(true))
                    .cached(store)
                    .fetchMetadata(Runnable::run, "group", "artifact", null)
                    .orElseThrow()
                    .toInputStream()) {
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<metadata/>");
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void can_fetch_and_validate_dependency() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest("foo".getBytes(StandardCharsets.UTF_8));
        try (Writer writer = Files.newBufferedWriter(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.md5"))) {
            writer.write(HexFormat.of().formatHex(hash));
        }
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                Map.of("MD5", repository.toUri()),
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.md5")).content().isEqualTo(HexFormat.of().formatHex(hash));
    }

    @Test
    public void can_fetch_and_fail_validate_dependency() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (Writer writer = Files.newBufferedWriter(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.md5"))) {
            writer.write(HexFormat.of().formatHex(digest.digest("bar".getBytes(StandardCharsets.UTF_8))));
        }
        MavenRepository repository = new MavenDefaultRepository(this.repository.toUri(),
                local,
                Map.of("MD5", this.repository.toUri()),
                null);
        assertThatThrownBy(() -> repository.fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null)).isInstanceOf(IllegalStateException.class);
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).doesNotExist();
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.md5")).doesNotExist();
    }

    @Test
    public void can_validate_cached_dependency() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest("foo".getBytes(StandardCharsets.UTF_8));
        try (Writer writer = Files.newBufferedWriter(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.md5"))) {
            writer.write(HexFormat.of().formatHex(hash));
        }
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                Map.of("MD5", repository.toUri()),
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.md5")).content().isEqualTo(HexFormat.of().formatHex(hash));
    }

    @Test
    public void can_validate_cached_dependency_with_cached_hash() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest("foo".getBytes(StandardCharsets.UTF_8));
        try (Writer writer = Files.newBufferedWriter(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.md5"))) {
            writer.write(HexFormat.of().formatHex(hash));
        }
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                Map.of("MD5", repository.toUri()),
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.md5")).content().isEqualTo(HexFormat.of().formatHex(hash));
    }

    @Test
    public void can_fail_validate_cached_dependency() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (Writer writer = Files.newBufferedWriter(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.md5"))) {
            writer.write(HexFormat.of().formatHex(digest.digest("bar".getBytes(StandardCharsets.UTF_8))));
        }
        MavenRepository repository = new MavenDefaultRepository(this.repository.toUri(),
                local,
                Map.of("MD5", this.repository.toUri()),
                null);
        assertThat(repository.fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null)).isEmpty();
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).doesNotExist();
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.md5")).doesNotExist();
    }

    @Test
    public void can_validate_dependency_when_stream_not_fully_drained() throws IOException, NoSuchAlgorithmException {
        String content = "foo\n";
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), content);
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        try (Writer writer = Files.newBufferedWriter(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.md5"))) {
            writer.write(HexFormat.of().formatHex(hash));
        }
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                null,
                Map.of("MD5", repository.toUri()),
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            int read = inputStream.read();
            assertThat(read).isEqualTo('f');
        }
    }

    @Test
    public void versionResolver_swaps_version_in_path_and_filename() {
        URI substituted = MavenDefaultRepository.versionResolver().apply(
                URI.create("https://repo.maven.apache.org/maven2/org/assertj/assertj-core/4.0.0-M1/assertj-core-4.0.0-M1.jar"),
                "3.27.0").orElseThrow();
        assertThat(substituted).hasToString(
                "https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.27.0/assertj-core-3.27.0.jar");
    }

    @Test
    public void versionResolver_handles_hyphenated_artifact_ids() {
        URI substituted = MavenDefaultRepository.versionResolver().apply(
                URI.create("https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/5.13.0-M3/junit-jupiter-5.13.0-M3.jar"),
                "5.11.3").orElseThrow();
        assertThat(substituted).hasToString(
                "https://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/5.11.3/junit-jupiter-5.11.3.jar");
    }

    @Test
    public void versionResolver_preserves_classifier() {
        URI substituted = MavenDefaultRepository.versionResolver().apply(
                URI.create("https://repo.maven.apache.org/maven2/org/assertj/assertj-core/4.0.0-M1/assertj-core-4.0.0-M1-sources.jar"),
                "3.27.0").orElseThrow();
        assertThat(substituted).hasToString(
                "https://repo.maven.apache.org/maven2/org/assertj/assertj-core/3.27.0/assertj-core-3.27.0-sources.jar");
    }

    @Test
    public void versionResolver_preserves_non_jar_extension() {
        URI substituted = MavenDefaultRepository.versionResolver().apply(
                URI.create("https://repo.maven.apache.org/maven2/foo/bar/1.0/bar-1.0.pom"),
                "2.0").orElseThrow();
        assertThat(substituted).hasToString(
                "https://repo.maven.apache.org/maven2/foo/bar/1.0/bar-1.0.pom".replace("1.0", "2.0"));
    }

    @Test
    public void versionResolver_handles_versions_with_dashes() {
        URI substituted = MavenDefaultRepository.versionResolver().apply(
                URI.create("https://example.test/g/a/1.0.0-SNAPSHOT/a-1.0.0-SNAPSHOT.jar"),
                "2.0.0-M5").orElseThrow();
        assertThat(substituted).hasToString(
                "https://example.test/g/a/2.0.0-M5/a-2.0.0-M5.jar");
    }

    @Test
    public void versionResolver_rejects_non_maven_layout() {
        assertThat(MavenDefaultRepository.versionResolver().apply(
                URI.create("https://example.test/some/random/path/foo.jar"),
                "9.9")).isEmpty();
    }

    @Test
    public void versionResolver_rejects_url_without_two_trailing_segments() {
        assertThat(MavenDefaultRepository.versionResolver().apply(
                URI.create("https://example.test/foo.jar"),
                "9.9")).isEmpty();
    }

    @Test
    public void rejects_coordinate_escaping_local_root() {
        MavenRepository repository = new MavenDefaultRepository(this.repository.toUri(), local, Map.of(), null);
        assertThatThrownBy(() -> repository.fetch(Runnable::run,
                "..",
                "artifact",
                "1",
                "jar",
                null,
                null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Resolved path escapes");
    }

    @Test
    public void resolves_a_downloaded_artifact_when_no_sidecar_is_published() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MavenRepository repository = new MavenDefaultRepository(this.repository.toUri(),
                local,
                Map.of("MD5", this.repository.toUri()),
                null);
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = repository.fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar")).exists();
    }

    @Test
    public void resolves_a_cached_artifact_without_a_sidecar() throws IOException {
        Files.writeString(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        MavenRepository repository = new MavenDefaultRepository(this.repository.toUri(),
                local,
                Map.of("MD5", this.repository.toUri()),
                null);
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = repository.fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
    }

    @Test
    public void rejects_a_downloaded_artifact_when_the_sidecar_mismatches() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        Files.writeString(repository.resolve("group/artifact/1/artifact-1.jar.sha1"),
                HexFormat.of().formatHex(new byte[20]));
        MavenRepository repository = new MavenDefaultRepository(this.repository.toUri(),
                local,
                Map.of("SHA1", this.repository.toUri()),
                null);
        assertThatThrownBy(() -> repository.fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed checksum validation");
    }

    @Test
    public void validates_with_weaker_sidecar_when_stronger_absent() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        byte[] hash = MessageDigest.getInstance("SHA-1").digest("foo".getBytes(StandardCharsets.UTF_8));
        try (Writer writer = Files.newBufferedWriter(repository
                .resolve("group/artifact/1")
                .resolve("artifact-1.jar.sha1"))) {
            writer.write(HexFormat.of().formatHex(hash));
        }
        Map<String, URI> validations = new LinkedHashMap<>();
        validations.put("SHA256", repository.toUri());
        validations.put("SHA1", repository.toUri());
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                validations,
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.sha1"))
                .content().isEqualTo(HexFormat.of().formatHex(hash));
    }

    @Test
    public void stops_probing_sidecars_once_the_strongest_validated() throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        byte[] hash = MessageDigest.getInstance("SHA512").digest("foo".getBytes(StandardCharsets.UTF_8));
        Files.writeString(repository.resolve("group/artifact/1/artifact-1.jar.sha512"),
                HexFormat.of().formatHex(hash));
        Files.writeString(repository.resolve("group/artifact/1/artifact-1.jar.sha1"),
                HexFormat.of().formatHex(new byte[20]));
        Map<String, URI> validations = new LinkedHashMap<>();
        validations.put("SHA512", repository.toUri());
        validations.put("SHA1", repository.toUri());
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                validations,
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.sha512"))
                .content().isEqualTo(HexFormat.of().formatHex(hash));
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.sha1"))
                .as("a weaker sidecar must not be read once a stronger one validated")
                .doesNotExist();
    }

    @Test
    public void stops_probing_sidecars_of_a_cached_artifact_once_the_strongest_validated()
            throws IOException, NoSuchAlgorithmException {
        Files.writeString(Files
                .createDirectories(local.resolve("group/artifact/1"))
                .resolve("artifact-1.jar"), "foo");
        byte[] hash = MessageDigest.getInstance("SHA512").digest("foo".getBytes(StandardCharsets.UTF_8));
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact/1"))
                .resolve("artifact-1.jar.sha512"), HexFormat.of().formatHex(hash));
        Files.writeString(repository.resolve("group/artifact/1/artifact-1.jar.sha1"),
                HexFormat.of().formatHex(new byte[20]));
        Map<String, URI> validations = new LinkedHashMap<>();
        validations.put("SHA512", repository.toUri());
        validations.put("SHA1", repository.toUri());
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                validations,
                null).fetch(Runnable::run,
                "group",
                "artifact",
                "1",
                "jar",
                null,
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        assertThat(local.resolve("group/artifact/1/artifact-1.jar.sha1"))
                .as("a weaker sidecar must not be read once a stronger one validated")
                .doesNotExist();
    }

    @Test
    public void can_fetch_metadata() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact"))
                .resolve("maven-metadata.xml"), "foo");
        Path dependency = result.resolve("dependency.jar");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null).fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
    }

    @Test
    public void does_not_store_metadata_in_the_local_repository() throws IOException, NoSuchAlgorithmException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact"));
        Files.writeString(folder.resolve("maven-metadata.xml"), "foo");
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest("foo".getBytes(StandardCharsets.UTF_8));
        Files.writeString(folder.resolve("maven-metadata.xml.md5"), HexFormat.of().formatHex(hash));
        Path dependency = result.resolve("dependency.xml");
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(),
                local,
                Map.of("MD5", repository.toUri()),
                null).fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, dependency);
        }
        assertThat(dependency).content().isEqualTo("foo");
        try (Stream<Path> stream = Files.walk(local)) {
            assertThat(stream.filter(Files::isRegularFile))
                    .as("metadata names a mutable path and must not enter the local repository")
                    .isEmpty();
        }
    }

    @Test
    public void reads_metadata_again_once_it_changed() throws IOException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact"));
        Files.writeString(folder.resolve("maven-metadata.xml"), "first");
        MavenRepository store = new MavenDefaultRepository(repository.toUri(), local, Map.of(), null);
        Path first = result.resolve("first.xml");
        try (InputStream inputStream = store.fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, first);
        }
        assertThat(first).content().isEqualTo("first");
        Files.writeString(folder.resolve("maven-metadata.xml"), "second");
        Path second = result.resolve("second.xml");
        try (InputStream inputStream = store.fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, second);
        }
        assertThat(second).content().isEqualTo("second");
    }

    @Test
    public void refreshes_cached_metadata_while_the_repository_answers() throws IOException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact"));
        Files.writeString(folder.resolve("maven-metadata.xml"), "first");
        Path cache = Files.createDirectory(result.resolve("cache"));
        MavenRepository store = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null).cached(cache);
        Path first = result.resolve("first.xml");
        try (InputStream inputStream = store.fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, first);
        }
        assertThat(first).content().isEqualTo("first");
        Files.writeString(folder.resolve("maven-metadata.xml"), "second");
        Path second = result.resolve("second.xml");
        try (InputStream inputStream = store.fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, second);
        }
        assertThat(second).content().isEqualTo("second");
        assertThat(cache.resolve("group%2Fartifact%2Fmaven-metadata.xml")).content().isEqualTo("second");
    }

    @Test
    public void serves_cached_metadata_when_the_repository_no_longer_answers() throws IOException {
        Path folder = Files.createDirectories(repository.resolve("group/artifact"));
        Files.writeString(folder.resolve("maven-metadata.xml"), "foo");
        Path cache = Files.createDirectory(result.resolve("cache"));
        MavenRepository store = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null).cached(cache);
        try (InputStream inputStream = store.fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, result.resolve("online.xml"));
        }
        Files.delete(folder.resolve("maven-metadata.xml"));
        Path offline = result.resolve("offline.xml");
        try (InputStream inputStream = store.fetchMetadata(Runnable::run,
                "group",
                "artifact",
                null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, offline);
        }
        assertThat(offline).content().isEqualTo("foo");
    }

    @Test
    public void serves_cached_metadata_when_the_repository_cannot_be_reached() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact"))
                .resolve("maven-metadata.xml"), "foo");
        Path cache = Files.createDirectory(result.resolve("cache"));
        try (InputStream inputStream = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)
                .cached(cache)
                .fetchMetadata(Runnable::run, "group", "artifact", null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, result.resolve("online.xml"));
        }
        Path offline = result.resolve("offline.xml");
        try (InputStream inputStream = unreachable()
                .cached(cache)
                .fetchMetadata(Runnable::run, "group", "artifact", null).orElseThrow().toInputStream()) {
            Files.copy(inputStream, offline);
        }
        assertThat(offline).content().isEqualTo("foo");
    }

    @Test
    public void fails_on_metadata_when_the_repository_cannot_be_reached_and_nothing_is_cached() throws IOException {
        Path cache = Files.createDirectory(result.resolve("cache"));
        MavenRepository store = unreachable().cached(cache);
        assertThatThrownBy(() -> store.fetchMetadata(Runnable::run, "group", "artifact", null))
                .isInstanceOf(IOException.class)
                .hasMessage("offline");
    }

    private static MavenRepository unreachable() {
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
                                                          String checksum) throws IOException {
                throw new IOException("offline");
            }
        };
    }

    @Test
    public void factory_queries_comma_separated_repositories_in_declared_order() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("first/group/artifact/1"))
                .resolve("artifact-1.jar"), "first-content");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/group/artifact/1"))
                .resolve("artifact-1.jar"), "second-content");
        settings.put("maven.uri",
                repository.resolve("first").toUri() + "," + repository.resolve("second").toUri());
        settings.put("maven.local", local.toString());
        try {
            Optional<RepositoryItem> item = MavenDefaultRepository.ofEnvironment(new Environment(settings)).fetch(Runnable::run,
                    "group",
                    "artifact",
                    "1",
                    "jar",
                    null,
                    null);
            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("first-content");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_falls_back_to_later_repository_on_miss() throws IOException {
        Files.createDirectories(repository.resolve("first"));
        Files.writeString(Files
                .createDirectories(repository.resolve("second/group/artifact/1"))
                .resolve("artifact-1.jar"), "second-content");
        settings.put("maven.uri",
                repository.resolve("first").toUri() + "," + repository.resolve("second").toUri());
        settings.put("maven.local", local.toString());
        try {
            Optional<RepositoryItem> item = MavenDefaultRepository.ofEnvironment(new Environment(settings)).fetch(Runnable::run,
                    "group",
                    "artifact",
                    "1",
                    "jar",
                    null,
                    null);
            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("second-content");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_filter_argument_restricts_repository_to_matching_group_ids() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("first/special/artifact/1"))
                .resolve("artifact-1.jar"), "first-special");
        Files.writeString(Files
                .createDirectories(repository.resolve("first/group/artifact/1"))
                .resolve("artifact-1.jar"), "first-group");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/special/artifact/1"))
                .resolve("artifact-1.jar"), "second-special");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/group/artifact/1"))
                .resolve("artifact-1.jar"), "second-group");
        settings.put("maven.uri",
                repository.resolve("first").toUri() + "|special," + repository.resolve("second").toUri());
        settings.put("maven.local", local.toString());
        try {
            MavenRepository merged = MavenDefaultRepository.ofEnvironment(new Environment(settings));
            try (InputStream stream = merged.fetch(Runnable::run, "special", "artifact", "1", "jar", null, null)
                    .orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("first-special");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "group", "artifact", "1", "jar", null, null)
                    .orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("second-group");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_filter_matches_sub_groups_on_dot_boundary() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("first/special/sub/artifact/1"))
                .resolve("artifact-1.jar"), "first-sub");
        Files.writeString(Files
                .createDirectories(repository.resolve("first/specialx/artifact/1"))
                .resolve("artifact-1.jar"), "first-specialx");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/special/sub/artifact/1"))
                .resolve("artifact-1.jar"), "second-sub");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/specialx/artifact/1"))
                .resolve("artifact-1.jar"), "second-specialx");
        settings.put("maven.uri",
                repository.resolve("first").toUri() + "|special," + repository.resolve("second").toUri());
        settings.put("maven.local", local.toString());
        try {
            MavenRepository merged = MavenDefaultRepository.ofEnvironment(new Environment(settings));
            try (InputStream stream = merged.fetch(Runnable::run, "special.sub", "artifact", "1", "jar", null, null)
                    .orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("first-sub");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "specialx", "artifact", "1", "jar", null, null)
                    .orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("second-specialx");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_resolves_named_reference_entries() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("first/group/artifact/1"))
                .resolve("artifact-1.jar"), "first-content");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/group/artifact/1"))
                .resolve("artifact-1.jar"), "second-content");
        settings.put("maven.uri", "@corp.test.mirrors");
        settings.put("corp.test.mirrors",
                repository.resolve("first").toUri() + "," + repository.resolve("second").toUri());
        settings.put("maven.local", local.toString());
        try {
            Optional<RepositoryItem> item = MavenDefaultRepository.ofEnvironment(new Environment(settings)).fetch(Runnable::run,
                    "group",
                    "artifact",
                    "1",
                    "jar",
                    null,
                    null);
            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("first-content");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("corp.test.mirrors");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_filter_wraps_a_reference_expansion() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("first/special/artifact/1"))
                .resolve("artifact-1.jar"), "first-special");
        Files.writeString(Files
                .createDirectories(repository.resolve("first/group/artifact/1"))
                .resolve("artifact-1.jar"), "first-group");
        Files.writeString(Files
                .createDirectories(repository.resolve("second/group/artifact/1"))
                .resolve("artifact-1.jar"), "second-group");
        settings.put("maven.uri",
                "@corp.test.mirrors|special," + repository.resolve("second").toUri());
        settings.put("corp.test.mirrors", repository.resolve("first").toUri().toString());
        settings.put("maven.local", local.toString());
        try {
            MavenRepository merged = MavenDefaultRepository.ofEnvironment(new Environment(settings));
            try (InputStream stream = merged.fetch(Runnable::run, "special", "artifact", "1", "jar", null, null)
                    .orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("first-special");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "group", "artifact", "1", "jar", null, null)
                    .orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("second-group");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("corp.test.mirrors");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_splices_the_default_for_a_bare_reference() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("first/group/artifact/1"))
                .resolve("artifact-1.jar"), "first-content");
        settings.put("maven.uri", repository.resolve("first").toUri() + ",@");
        settings.put("maven.local", local.toString());
        try {
            Optional<RepositoryItem> item = MavenDefaultRepository.ofEnvironment(new Environment(settings)).fetch(Runnable::run,
                    "group",
                    "artifact",
                    "1",
                    "jar",
                    null,
                    null);
            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes())).isEqualTo("first-content");
            }
        } finally {
            settings.remove("maven.uri");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_fails_on_unresolved_reference() {
        settings.put("maven.uri", "@corp.test.unset");
        settings.put("maven.local", local.toString());
        try {
            assertThatThrownBy(() -> MavenDefaultRepository.ofEnvironment(new Environment(settings)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Unresolved repository reference: @corp.test.unset");
        } finally {
            settings.remove("maven.uri");
            settings.remove("maven.local");
        }
    }

    @Test
    public void factory_fails_on_circular_reference() {
        settings.put("maven.uri", "@corp.test.left");
        settings.put("corp.test.left", "@corp.test.right");
        settings.put("corp.test.right", "@corp.test.left");
        settings.put("maven.local", local.toString());
        try {
            assertThatThrownBy(() -> MavenDefaultRepository.ofEnvironment(new Environment(settings)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Circular repository reference: @corp.test.left");
        } finally {
            settings.remove("maven.uri");
            settings.remove("corp.test.left");
            settings.remove("corp.test.right");
            settings.remove("maven.local");
        }
    }

    @Test
    public void filtered_repository_hides_metadata_of_non_matching_groups() throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve("group/artifact"))
                .resolve("maven-metadata.xml"), "meta");
        assertThat(new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)
                .filter(groupId -> groupId.equals("other"))
                .fetchMetadata(Runnable::run, "group", "artifact", null)).isEmpty();
        try (InputStream stream = new MavenDefaultRepository(repository.toUri(), null, Map.of(), null)
                .filter(groupId -> groupId.equals("group"))
                .fetchMetadata(Runnable::run, "group", "artifact", null).orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes())).isEqualTo("meta");
        }
    }
}
