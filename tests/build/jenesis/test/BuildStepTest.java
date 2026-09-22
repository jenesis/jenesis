package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static build.jenesis.SequencedProperties.SYSTEM;

public class BuildStepTest {

    @TempDir
    private Path root;

    @AfterEach
    public void tearDown() {
        System.clearProperty("jenesis.archive.timestamp");
    }

    @Test
    public void archive_timestamp_defaults_to_a_date_no_time_zone_reads_as_1979() {
        assertThat(BuildStep.timestamp(SYSTEM)).isEqualTo(OffsetDateTime.parse("1980-02-01T00:00:00Z"));
    }

    @Test
    public void archive_timestamp_is_read_from_its_property_as_utc() {
        assertThat(BuildStep.timestamp(Map.of("archive.timestamp", "2026-09-17T09:30:00+02:00")::get)).isEqualTo(OffsetDateTime.parse("2026-09-17T07:30:00Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1980-01-01T00:00:00Z", "2100-01-01T00:00:00Z"})
    public void archive_timestamp_outside_what_an_entry_records_without_a_zone_is_rejected(String value) {
        assertThatThrownBy(() -> BuildStep.timestamp(Map.of("archive.timestamp", value)::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1980-01-01T00:00:02Z and 2099-12-31T23:59:59Z")
                .hasMessageEndingWith(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    public void archive_timestamp_set_empty_turns_the_fixed_time_off(String value) {
        assertThat(BuildStep.timestamp(Map.of("archive.timestamp", value)::get)).isNull();
    }

    @Test
    public void archive_timestamp_without_an_offset_is_rejected() {
        assertThatThrownBy(() -> BuildStep.timestamp(Map.of("archive.timestamp", "2026-09-17T09:30:00")::get))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601 date-time with an offset")
                .hasMessageEndingWith("2026-09-17T09:30:00");
    }

    @Test
    public void recognises_the_meta_inf_versions_overlay() {
        assertThat(BuildStep.underMetaInfVersions(Path.of("META-INF/versions/25/sample/Platform.class"))).isTrue();
        assertThat(BuildStep.underMetaInfVersions(Path.of("META-INF/versions/25/overlay.json"))).isTrue();
        assertThat(BuildStep.underMetaInfVersions(Path.of("META-INF/versions"))).isTrue();
    }

    @Test
    public void other_meta_inf_content_is_not_the_versions_overlay() {
        assertThat(BuildStep.underMetaInfVersions(Path.of("META-INF/native-image/app/reachability-metadata.json"))).isFalse();
        assertThat(BuildStep.underMetaInfVersions(Path.of("META-INF/services/java.sql.Driver"))).isFalse();
        assertThat(BuildStep.underMetaInfVersions(Path.of("META-INF/MANIFEST.MF"))).isFalse();
        assertThat(BuildStep.underMetaInfVersions(Path.of("sample/Sample.class"))).isFalse();
        assertThat(BuildStep.underMetaInfVersions(Path.of("versions/META-INF/overlay.json"))).isFalse();
    }

    @Test
    public void recognises_the_build_jenesis_folder() {
        assertThat(BuildStep.underBuildJenesis(Path.of("META-INF/build.jenesis/checkstyle.xml"))).isTrue();
        assertThat(BuildStep.underBuildJenesis(Path.of("META-INF/build.jenesis"))).isTrue();
        assertThat(BuildStep.underBuildJenesis(Path.of("META-INF/versions/25/sample/Sample.class"))).isFalse();
        assertThat(BuildStep.underBuildJenesis(Path.of("build.jenesis/checkstyle.xml"))).isFalse();
        assertThat(BuildStep.underBuildJenesis(Path.of("META-INF/MANIFEST.MF"))).isFalse();
    }

    @Test
    public void locate_returns_the_file_from_the_first_folder_that_contains_it() throws IOException {
        Path first = Files.createDirectory(root.resolve("first"));
        Path second = Files.createDirectory(root.resolve("second"));
        Files.writeString(second.resolve("checkstyle.xml"), "second");
        assertThat(BuildStep.locate(new LinkedHashSet<>(List.of(first, second)), "checkstyle.xml"))
                .isEqualTo(second.resolve("checkstyle.xml"));
        Files.writeString(first.resolve("checkstyle.xml"), "first");
        assertThat(BuildStep.locate(new LinkedHashSet<>(List.of(first, second)), "checkstyle.xml"))
                .isEqualTo(first.resolve("checkstyle.xml"));
    }

    @Test
    public void locate_returns_null_when_no_folder_contains_the_file() throws IOException {
        Path folder = Files.createDirectory(root.resolve("empty"));
        assertThat(BuildStep.locate(new LinkedHashSet<>(List.of(folder)), "absent.xml")).isNull();
    }
}
