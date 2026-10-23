package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenRepository;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenRepositoryTest {

    @TempDir
    private Path root;

    @Test
    public void keeps_serving_the_metadata_a_reader_holds_open_when_it_is_fetched_again() throws IOException {
        Path served = Files.writeString(root.resolve("maven-metadata.xml"), "<metadata/>");
        MavenRepository repository = new MavenRepository() {
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
                return Optional.of(RepositoryItem.ofFile(served));
            }
        }.materialized(root.resolve("cache"));
        Path cached = repository.fetchMetadata(Runnable::run, "g", "a", null).orElseThrow().file().orElseThrow();
        try (InputStream held = new FileInputStream(cached.toFile())) {
            assertThat(repository.fetchMetadata(Runnable::run, "g", "a", null).orElseThrow().file())
                    .as("a second fetch while another reader holds the metadata open, which Windows refuses to replace,"
                            + " serves the metadata already there")
                    .contains(cached);
            assertThat(held.readAllBytes()).isEqualTo("<metadata/>".getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<Path> files = Files.list(cached.getParent())) {
            assertThat(files.toList())
                    .as("no temporary file is left behind")
                    .containsExactly(cached);
        }
    }
}
