package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;
import build.jenesis.project.JUnitPlatform;
import build.jenesis.project.TestModule;
import build.jenesis.project.TestNG;
import build.jenesis.project.TestTags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestModuleScopeTest {

    private static boolean covered(String requested, String... ran) {
        return TestTags.parse(requested).coveredBy(Stream.of(ran).map(TestTags::parse).toList());
    }

    @Test
    public void reads_tag_names_to_include_and_to_leave_out() {
        TestTags tags = TestTags.parse(" foo , bar ,! qux,,");
        assertThat(tags.included()).containsExactly("bar", "foo");
        assertThat(tags.excluded()).containsExactly("qux");
        assertThat(tags).hasToString("bar,foo,!qux");
        assertThat(TestTags.parse(null)).isEqualTo(TestTags.ALL);
        assertThat(TestTags.parse(" ")).isEqualTo(TestTags.ALL);
    }

    @Test
    public void reads_tags_a_test_carries_all_of() {
        TestTags tags = TestTags.parse("foo & bar,baz,!qux");
        assertThat(tags.included()).containsExactly("bar&foo", "baz");
        assertThat(tags).hasToString("bar&foo,baz,!qux");
    }

    @Test
    public void refuses_the_syntax_of_a_test_framework() {
        for (String expression : List.of("!(npm|pypi)", "foo|bar", "any()", "!", "!foo&bar", "foo&", "foo&&bar")) {
            assertThatThrownBy(() -> TestTags.parse(expression))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comma-separated list of tag names");
        }
    }

    @Test
    public void nothing_that_ran_covers_nothing() {
        assertThat(covered(null)).isFalse();
        assertThat(covered("foo")).isFalse();
    }

    @Test
    public void a_run_of_every_test_covers_every_request() {
        assertThat(covered(null, "")).isTrue();
        assertThat(covered("foo,!qux", "")).isTrue();
        assertThat(covered("!qux", "")).isTrue();
    }

    @Test
    public void a_run_of_more_tags_covers_a_run_of_fewer() {
        assertThat(covered("foo", "foo,bar")).isTrue();
        assertThat(covered("foo,bar", "foo")).as("bar never ran").isFalse();
        assertThat(covered("foo,bar", "foo", "bar")).as("each tag ran in a run of its own").isTrue();
        assertThat(covered(null, "foo", "bar")).as("tests carrying neither tag never ran").isFalse();
    }

    @Test
    public void a_run_of_any_of_the_tags_covers_a_request_for_all_of_them() {
        assertThat(covered("foo&bar", "foo")).isTrue();
        assertThat(covered("foo&bar", "bar,baz")).isTrue();
        assertThat(covered("foo&bar&baz", "bar&foo")).isTrue();
        assertThat(covered("foo", "foo&bar")).as("the tests tagged foo but not bar never ran").isFalse();
        assertThat(covered("foo&bar", "foo&baz")).isFalse();
    }

    @Test
    public void a_run_that_left_tests_out_covers_only_a_request_that_leaves_them_out_too() {
        assertThat(covered("foo,!qux", "foo,!qux")).isTrue();
        assertThat(covered("foo,!qux,!quux", "foo,!qux")).isTrue();
        assertThat(covered("foo", "foo,!qux")).as("the tests tagged foo and qux never ran").isFalse();
        assertThat(covered(null, "!qux")).isFalse();
        assertThat(covered("!qux", "!qux")).isTrue();
    }

    @Test
    public void junit_runs_what_was_requested_and_did_not_run_before(@TempDir Path root) {
        assertThat(new JUnitPlatform().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.parse("foo,bar,!qux"), List.of(TestTags.parse("foo")), false, false))
                .as("tagged foo or bar, not qux, and outside what ran as foo")
                .contains("--include-tag=(bar | foo) & !(qux) & (!(foo))");
        assertThat(new JUnitPlatform().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.ALL, List.of(TestTags.parse("foo,!slow")), false, false))
                .contains("--include-tag=(!(foo) | (slow))");
    }

    @Test
    public void junit_requires_every_tag_of_a_conjunction(@TempDir Path root) {
        assertThat(new JUnitPlatform().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.parse("foo&bar,baz"), List.of(TestTags.parse("bar&qux")), false, false))
                .contains("--include-tag=((bar & foo) | baz) & (!((bar & qux)))");
    }

    @Test
    public void testng_refuses_a_conjunction_its_groups_cannot_express(@TempDir Path root) {
        assertThatThrownBy(() -> new TestNG().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.parse("foo&bar"), List.of(), false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bar&foo");
        assertThat(new TestNG().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.parse("baz"), List.of(TestTags.parse("foo&bar")), false, false))
                .as("an earlier conjunction cannot be left out by groups, so the request runs whole")
                .doesNotContain("-excludegroups");
    }

    @Test
    public void testng_leaves_out_what_ran_before_where_its_groups_can_say_so(@TempDir Path root) {
        assertThat(new TestNG().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.parse("foo,bar,!qux"), List.of(TestTags.parse("foo")), false, false))
                .containsSubsequence("-groups", "bar,foo", "-excludegroups", "qux,foo");
        assertThat(new TestNG().arguments(root, root, Collections.emptyNavigableSet(), Collections.emptyNavigableMap(),
                TestTags.parse("foo,bar"), List.of(TestTags.parse("foo,!slow")), false, false))
                .as("an earlier exclusion cannot be undone by groups, so the request runs whole")
                .containsSubsequence("-groups", "bar,foo")
                .doesNotContain("-excludegroups");
    }

    @Test
    public void a_scope_round_trips_through_a_file(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("testscope.properties");
        TestModule.Scope scope = new TestModule.Scope(".*FooTest", List.of(TestTags.parse("foo"), TestTags.parse("bar,!qux")));
        scope.store(file);
        assertThat(SequencedProperties.ofFiles(file)).containsExactly(
                Map.entry("filter", ".*FooTest"),
                Map.entry("covered.0", "foo"),
                Map.entry("covered.1", "bar,!qux"));
        assertThat(TestModule.Scope.ofFile(file)).isEqualTo(scope);
    }

    @Test
    public void a_filter_is_only_matched_by_the_identical_filter() {
        assertThat(new TestModule.Scope(null, List.of()).filters(null)).isTrue();
        assertThat(new TestModule.Scope(" .*FooTest , .*BarTest ", List.of()).filters(".*FooTest,.*BarTest")).isTrue();
        assertThat(new TestModule.Scope(".*Test,.*Test#one", List.of()).filters(".*Test#one,.*Test"))
                .as("the first matching pattern wins, so the order is significant")
                .isFalse();
        assertThat(new TestModule.Scope(null, List.of()).filters(".*FooTest")).isFalse();
    }
}
