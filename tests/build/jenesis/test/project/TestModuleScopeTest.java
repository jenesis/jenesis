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
    public void reads_alternatives_of_tags_and_negated_tags() {
        TestTags tags = TestTags.parse("foo,-qux+bar,-baz,,");
        assertThat(tags.alternatives()).containsExactly("-baz", "bar+-qux", "foo");
        assertThat(tags).hasToString("-baz,bar+-qux,foo");
        assertThat(TestTags.literals("bar+-qux")).containsExactly("bar", "-qux");
        assertThat(TestTags.parse("slow-tests+-slow-io").alternatives())
                .as("a hyphen inside a tag is part of its name")
                .containsExactly("slow-tests+-slow-io");
        assertThat(TestTags.parse(null)).isEqualTo(TestTags.ALL);
        assertThat(TestTags.parse(" ")).isEqualTo(TestTags.ALL);
    }

    @Test
    public void refuses_anything_a_command_line_would_have_to_quote() {
        for (String expression : List.of("!(npm|pypi)", "foo|bar", "foo&bar", "!foo", "foo, bar", "foo+", "+foo",
                "foo++bar", "--foo", "-", "any()")) {
            assertThatThrownBy(() -> TestTags.parse(expression))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("comma-separated list of alternatives");
        }
    }

    @Test
    public void refuses_an_alternative_that_selects_no_test() {
        assertThatThrownBy(() -> TestTags.parse("bar,foo+-foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foo+-foo")
                .hasMessageContaining("selects none");
    }

    @Test
    public void nothing_that_ran_covers_nothing() {
        assertThat(covered(null)).isFalse();
        assertThat(covered("foo")).isFalse();
    }

    @Test
    public void a_run_of_every_test_covers_every_request() {
        assertThat(covered(null, "")).isTrue();
        assertThat(covered("foo+-qux", "")).isTrue();
        assertThat(covered("-qux", "")).isTrue();
    }

    @Test
    public void a_run_of_more_alternatives_covers_a_run_of_fewer() {
        assertThat(covered("foo", "foo,bar")).isTrue();
        assertThat(covered("foo,bar", "foo")).as("bar never ran").isFalse();
        assertThat(covered("foo,bar", "foo", "bar")).as("each alternative ran in a run of its own").isTrue();
        assertThat(covered(null, "foo", "bar")).as("tests carrying neither tag never ran").isFalse();
    }

    @Test
    public void a_run_of_fewer_conditions_covers_an_alternative_with_more() {
        assertThat(covered("foo+bar", "foo")).isTrue();
        assertThat(covered("foo+bar", "bar,baz")).isTrue();
        assertThat(covered("foo+bar+baz", "bar+foo")).isTrue();
        assertThat(covered("foo", "foo+bar")).as("the tests tagged foo but not bar never ran").isFalse();
        assertThat(covered("foo+bar", "foo+baz")).isFalse();
    }

    @Test
    public void a_run_that_left_tests_out_covers_only_an_alternative_that_leaves_them_out_too() {
        assertThat(covered("foo+-qux", "foo+-qux")).isTrue();
        assertThat(covered("foo+-qux+-quux", "foo+-qux")).isTrue();
        assertThat(covered("foo", "foo+-qux")).as("the tests tagged foo and qux never ran").isFalse();
        assertThat(covered(null, "-qux")).isFalse();
        assertThat(covered("-qux+-quux", "-qux")).isTrue();
        assertThat(covered("-container", "-container,-soak"))
                .as("not both of container and soak includes every test that is not container")
                .isTrue();
        assertThat(covered("-container,-soak", "-container+-soak"))
                .as("neither of them never ran the tests carrying exactly one")
                .isFalse();
    }

    @Test
    public void junit_runs_what_was_requested_and_did_not_run_before(@TempDir Path root) {
        assertThat(new JUnitPlatform().tags(TestTags.parse("foo,bar+-qux"), List.of(TestTags.parse("foo"))))
                .as("foo, or bar but not qux, and outside what ran as foo")
                .contains("--include-tag=((bar & !qux) | foo) & !(foo)");
        assertThat(new JUnitPlatform().tags(TestTags.ALL, List.of(TestTags.parse("foo+-slow"))))
                .contains("--include-tag=!((foo & !slow))");
    }

    @Test
    public void junit_expresses_every_selection_of_the_grammar(@TempDir Path root) {
        assertThat(new JUnitPlatform().tags(TestTags.parse("-container+-network,release+-soak"), List.of()))
                .contains("--include-tag=((!container & !network) | (release & !soak))");
        assertThat(new JUnitPlatform().tags(TestTags.parse("-container,-soak"), List.of()))
                .as("not both")
                .contains("--include-tag=(!container | !soak)");
        assertThat(new JUnitPlatform().tags(TestTags.parse("release,-container"), List.of()))
                .contains("--include-tag=(!container | release)");
    }

    @Test
    public void testng_refuses_what_its_groups_cannot_express(@TempDir Path root) {
        for (String expression : List.of("foo+bar", "foo+-qux,bar", "release,-container")) {
            assertThatThrownBy(() -> new TestNG().tags(TestTags.parse(expression), List.of()))
                    .as(expression)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TestNG");
        }
        assertThat(new TestNG().tags(TestTags.parse("baz"), List.of(TestTags.parse("foo+bar"))))
                .as("an earlier conjunction cannot be left out by groups, so the request runs whole")
                .doesNotContain("-excludegroups");
    }

    @Test
    public void testng_runs_groups_that_leave_out_the_same_groups(@TempDir Path root) {
        assertThat(new TestNG().tags(TestTags.parse("foo+-qux,bar+-qux"), List.of(TestTags.parse("foo"))))
                .containsSubsequence("-groups", "bar,foo", "-excludegroups", "foo,qux");
        assertThat(new TestNG().tags(TestTags.parse("-qux"), List.of()))
                .containsSubsequence("-excludegroups", "qux")
                .doesNotContain("-groups");
        assertThat(new TestNG().tags(TestTags.parse("foo,bar"), List.of(TestTags.parse("foo+-slow"))))
                .as("an earlier exclusion cannot be undone by groups, so the request runs whole")
                .containsSubsequence("-groups", "bar,foo")
                .doesNotContain("-excludegroups");
    }

    @Test
    public void a_memory_in_an_earlier_notation_is_forgotten(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("testscope.properties");
        Files.writeString(file, "covered.0=foo,\\!qux\n");
        assertThat(TestModule.Scope.ofFile(file).covered()).isEmpty();
    }

    @Test
    public void a_scope_round_trips_through_a_file(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("testscope.properties");
        TestModule.Scope scope = new TestModule.Scope(".*FooTest", List.of(TestTags.parse("foo"), TestTags.parse("bar+-qux")));
        scope.store(file);
        assertThat(SequencedProperties.ofFiles(file)).containsExactly(
                Map.entry("filter", ".*FooTest"),
                Map.entry("covered.0", "foo"),
                Map.entry("covered.1", "bar+-qux"));
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
