package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SafeSegment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SafeSegmentTest {

    private final SafeSegment safeSegment = new SafeSegment();

    @Test
    public void accepts_valid_coordinate_segments() {
        for (String value : List.of("1.0", "1.0.5", "1.0-SNAPSHOT", "com.example", "foo", "sources", "windows-x86_64", "a_b+c")) {
            assertThatCode(() -> safeSegment.accept("value", value)).as(value).doesNotThrowAnyException();
        }
    }

    @Test
    public void rejects_a_blank_value() {
        assertThatThrownBy(() -> safeSegment.accept("version", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blank version");
    }

    @Test
    public void rejects_a_path_separator_as_traversal() {
        assertThatThrownBy(() -> safeSegment.accept("module name", "widget/../../secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traversal");
    }

    @Test
    public void rejects_disallowed_characters() {
        for (String value : List.of("a b", "a:b", "a\\b", "a%2f", "a\tb")) {
            assertThatThrownBy(() -> safeSegment.accept("value", value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not permitted");
        }
    }

    @Test
    public void rejects_dot_segment_traversal() {
        for (String value : List.of("..", ".", "a..b", ".hidden", "trailing.")) {
            assertThatThrownBy(() -> safeSegment.accept("value", value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("traversal");
        }
    }

    @Test
    public void reports_the_role_in_the_message() {
        assertThatThrownBy(() -> safeSegment.accept("groupId", "bad/.."))
                .hasMessageContaining("groupId");
    }
}
