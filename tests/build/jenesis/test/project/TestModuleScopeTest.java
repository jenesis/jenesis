package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;
import build.jenesis.project.TestModule;

import static org.assertj.core.api.Assertions.assertThat;

public class TestModuleScopeTest {

    private static boolean covers(String executed, String requested) {
        return new TestModule.Scope(null, executed).covers(new TestModule.Scope(null, requested));
    }

    private static boolean coversFilter(String executed, String requested) {
        return new TestModule.Scope(executed, null).covers(new TestModule.Scope(requested, null));
    }

    @Test
    public void an_untagged_run_covers_itself() {
        assertThat(covers(null, null)).isTrue();
    }

    @Test
    public void an_identical_expression_covers_itself() {
        assertThat(covers("!(npm|pypi)", "!(npm|pypi)")).isTrue();
        assertThat(covers("slow", "slow")).isTrue();
        assertThat(covers("!(a&b)", "!(a&b)")).isTrue();
    }

    @Test
    public void an_expression_is_normalized_before_it_is_compared() {
        assertThat(covers(" slow , flaky ", "flaky,slow")).isTrue();
        assertThat(covers("slow,,", "slow")).isTrue();
        assertThat(covers("", null)).isTrue();
    }

    @Test
    public void an_untagged_run_covers_every_tagged_request() {
        assertThat(covers(null, "slow")).isTrue();
        assertThat(covers(null, "!(npm|pypi)")).isTrue();
        assertThat(covers(null, "a&b|!(c)")).isTrue();
    }

    @Test
    public void a_tagged_run_does_not_cover_an_untagged_request() {
        assertThat(covers("!(npm)", null)).isFalse();
        assertThat(covers("slow", null)).isFalse();
    }

    @Test
    public void an_exclusion_covers_an_exclusion_that_excludes_at_least_as_much() {
        assertThat(covers("!(npm)", "!(npm|pypi)")).isTrue();
        assertThat(covers("!(npm)", "!(npm|pypi|go)")).isTrue();
        assertThat(covers("!npm", "!(npm|pypi)")).isTrue();
        assertThat(covers("!(npm|pypi)", "!(pypi|npm)")).isTrue();
        assertThat(covers("!( npm | pypi )", "!(npm|pypi|go)")).isTrue();
    }

    @Test
    public void an_exclusion_does_not_cover_an_exclusion_that_excludes_less() {
        assertThat(covers("!(npm|pypi)", "!(npm)")).isFalse();
        assertThat(covers("!(npm|pypi)", "!(go)")).isFalse();
        assertThat(covers("!npm", "!pypi")).isFalse();
    }

    @Test
    public void an_undecidable_expression_is_not_covered() {
        assertThat(covers("npm|pypi", "npm")).isFalse();
        assertThat(covers("!(npm&pypi)", "!(npm)")).isFalse();
        assertThat(covers("!(npm)", "!(npm&pypi)")).isFalse();
        assertThat(covers("!(npm|(pypi&go))", "!(npm)")).isFalse();
        assertThat(covers("!((npm)|(pypi))", "!(npm)")).isFalse();
        assertThat(covers("!(npm|!pypi)", "!(npm)")).isFalse();
        assertThat(covers("!(any())", "!(npm)")).isFalse();
        assertThat(covers("!(none)", "!(none|npm)")).isFalse();
        assertThat(covers("!(npm),!(pypi)", "!(npm)")).isFalse();
        assertThat(covers("!(npm)", "!(npm),!(pypi)")).isFalse();
    }

    @Test
    public void a_negation_binding_tighter_than_a_disjunction_is_not_read_as_an_exclusion_list() {
        assertThat(covers("!npm|pypi", "!(npm|pypi)")).isFalse();
        assertThat(covers("!(npm)|pypi", "!(npm|pypi)")).isFalse();
    }

    @Test
    public void a_filter_is_only_covered_by_the_identical_filter() {
        assertThat(coversFilter(null, null)).isTrue();
        assertThat(coversFilter(".*FooTest", ".*FooTest")).isTrue();
        assertThat(coversFilter(" .*FooTest , .*BarTest ", ".*FooTest,.*BarTest")).isTrue();
        assertThat(coversFilter(null, ".*FooTest")).isFalse();
        assertThat(coversFilter(".*FooTest", null)).isFalse();
        assertThat(coversFilter(".*FooTest", ".*FooTest#one")).isFalse();
        assertThat(coversFilter(".*Test", ".*FooTest")).isFalse();
    }

    @Test
    public void a_filter_ordering_is_significant_because_the_first_matching_pattern_wins() {
        assertThat(coversFilter(".*Test,.*Test#one", ".*Test#one,.*Test")).isFalse();
    }

    @Test
    public void a_filter_and_a_tag_must_both_be_covered() {
        assertThat(new TestModule.Scope(".*FooTest", null).covers(new TestModule.Scope(".*FooTest", "slow"))).isTrue();
        assertThat(new TestModule.Scope(".*FooTest", "!(a)").covers(new TestModule.Scope(".*FooTest", "!(a|b)"))).isTrue();
        assertThat(new TestModule.Scope(".*FooTest", null).covers(new TestModule.Scope(".*BarTest", "slow"))).isFalse();
        assertThat(new TestModule.Scope(".*FooTest", "!(a|b)").covers(new TestModule.Scope(".*FooTest", "!(a)"))).isFalse();
    }

    @Test
    public void a_scope_round_trips_through_a_file(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("testscope.properties");
        new TestModule.Scope(".*FooTest", "!(a)").store(file);
        assertThat(SequencedProperties.ofFiles(file))
                .containsExactly(Map.entry("filter", ".*FooTest"), Map.entry("tag", "!(a)"));
        assertThat(TestModule.Scope.ofFile(file)).isEqualTo(new TestModule.Scope(".*FooTest", "!(a)"));
    }

    @Test
    public void a_blank_scope_records_nothing_and_covers_everything(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("testscope.properties");
        new TestModule.Scope("", " ").store(file);
        assertThat(SequencedProperties.ofFiles(file)).isEmpty();
        assertThat(TestModule.Scope.ofFile(file)).isEqualTo(new TestModule.Scope(null, null));
        assertThat(TestModule.Scope.ofFile(file).covers(new TestModule.Scope(".*FooTest", "slow"))).isFalse();
        assertThat(TestModule.Scope.ofFile(file).covers(new TestModule.Scope(null, "slow"))).isTrue();
    }
}
