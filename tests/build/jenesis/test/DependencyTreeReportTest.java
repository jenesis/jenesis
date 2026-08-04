package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.DependencyTreeReport;
import build.jenesis.License;
import build.jenesis.Resolver;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyTreeReportTest {

    private ByteArrayOutputStream bytes;
    private DependencyTreeReport report;

    @BeforeEach
    public void setUp() {
        bytes = new ByteArrayOutputStream();
        report = new DependencyTreeReport(new PrintStream(bytes, true, StandardCharsets.UTF_8));
    }

    private String output() {
        return bytes.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
    }

    private static Resolver.Resolution resolution(List<Resolver.Edge> edges,
                                                  SequencedMap<String, Resolver.Vertex> vertices) {
        return new Resolver.Resolution(new LinkedHashMap<>(), edges, vertices);
    }

    @Test
    public void renders_followed_tree_with_connectors_versions_and_scope() {
        report.render(resolution(List.of(
                new Resolver.Edge(null, "maven/g/a/1.0", "1.0", "compile", true),
                new Resolver.Edge("maven/g/a/1.0", "maven/g/b/2.0", "2.0", "compile", true),
                new Resolver.Edge("maven/g/a/1.0", "maven/g/d/4.0", "4.0", "compile", true),
                new Resolver.Edge("maven/g/b/2.0", "maven/g/c/3.0", "3.0", "runtime", true)),
                new LinkedHashMap<>()));
        String text = output();
        assertThat(text).contains("Dependency tree:");
        assertThat(text).contains("maven/g/a 1.0 [compile]");
        assertThat(text).contains("├─ maven/g/b 2.0 [compile]");
        assertThat(text).contains("│  └─ maven/g/c 3.0 [runtime]");
        assertThat(text).contains("└─ maven/g/d 4.0 [compile]");
    }

    @Test
    public void marks_not_followed_duplicates_and_does_not_expand_them() {
        report.render(resolution(List.of(
                new Resolver.Edge(null, "maven/g/a/1.0", "1.0", "compile", true),
                new Resolver.Edge("maven/g/a/1.0", "maven/g/b/2.0", "2.0", "compile", true),
                new Resolver.Edge("maven/g/b/2.0", "maven/g/c/3.0", "3.0", "compile", true),
                new Resolver.Edge("maven/g/a/1.0", "maven/g/c/3.0", "3.0", "compile", false)),
                new LinkedHashMap<>()));
        String text = output();
        assertThat(text).contains("maven/g/c 3.0 [compile] (*)");
        assertThat(text.split("\\(\\*\\)", -1).length - 1).isEqualTo(1);
    }

    @Test
    public void annotates_the_negotiated_version_when_it_differs_from_the_requested_one() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("maven/g/a", new Resolver.Vertex("2", null, false, false, List.of()));
        report.render(resolution(List.of(
                new Resolver.Edge(null, "maven/g/a/[1,2]", "[1,2]", "compile", true)),
                vertices));
        assertThat(output()).contains("maven/g/a [1,2] -> 2");
    }

    @Test
    public void renders_module_metadata() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("module/org.foo", new Resolver.Vertex("1.0", "org.foo", true, false, List.of()));
        report.render(resolution(List.of(
                new Resolver.Edge(null, "module/org.foo/1.0", "1.0", null, true)),
                vertices));
        assertThat(output()).contains("(module org.foo, automatic)");
    }

    @Test
    public void marks_internal_modules_as_local() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("module/foo", new Resolver.Vertex("1.0", "foo", false, true, List.of()));
        report.render(resolution(List.of(
                new Resolver.Edge(null, "module/foo/1.0", "1.0", "compile", true)),
                vertices));
        assertThat(output()).contains("(module foo, local)");
    }

    @Test
    public void compact_shows_only_internal_modules_and_summarizes_external_by_count() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("module/foo", new Resolver.Vertex("1.0", "foo", false, true, List.of()));
        vertices.put("module/bar", new Resolver.Vertex("1.0", "bar", false, true, List.of()));
        vertices.put("maven/g/a", new Resolver.Vertex("1.0", null, false, false, List.of()));
        vertices.put("maven/g/b", new Resolver.Vertex("1.0", null, false, false, List.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyTreeReport compact = new DependencyTreeReport(new PrintStream(out, true, StandardCharsets.UTF_8), true);
        compact.render(resolution(List.of(
                new Resolver.Edge(null, "module/foo/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/foo/1.0", "module/bar/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/foo/1.0", "maven/g/a/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/bar/1.0", "maven/g/b/1.0", "1.0", "compile", true)),
                vertices));
        String text = out.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
        assertThat(text).contains("module/foo");
        assertThat(text).contains("module/bar");
        assertThat(text).doesNotContain("maven/g/a");
        assertThat(text).doesNotContain("maven/g/b");
        assertThat(text).contains("1 external dependency");
        assertThat(text).contains("2 external dependencies");
    }

    @Test
    public void compact_renders_a_shared_internal_module_only_once() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("module/foo", new Resolver.Vertex("1.0", "foo", false, true, List.of()));
        vertices.put("module/bar", new Resolver.Vertex("1.0", "bar", false, true, List.of()));
        vertices.put("module/qux", new Resolver.Vertex("1.0", "qux", false, true, List.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyTreeReport compact = new DependencyTreeReport(new PrintStream(out, true, StandardCharsets.UTF_8), true);
        compact.render(resolution(List.of(
                new Resolver.Edge(null, "module/foo/1.0", "1.0", "compile", true),
                new Resolver.Edge(null, "module/bar/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/foo/1.0", "module/qux/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/bar/1.0", "module/qux/1.0", "1.0", "compile", false)),
                vertices));
        String text = out.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
        String tree = text.substring(0, text.indexOf("Resolved dependencies:"));
        assertThat(tree.split("module/qux", -1).length - 1).isEqualTo(1);
    }

    @Test
    public void compact_expands_a_shared_module_in_the_biggest_tree() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("module/core", new Resolver.Vertex("1.0", "core", false, true, List.of()));
        vertices.put("module/web", new Resolver.Vertex("1.0", "web", false, true, List.of()));
        vertices.put("module/service", new Resolver.Vertex("1.0", "service", false, true, List.of()));
        vertices.put("module/util", new Resolver.Vertex("1.0", "util", false, true, List.of()));
        vertices.put("module/log", new Resolver.Vertex("1.0", "log", false, true, List.of()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyTreeReport compact = new DependencyTreeReport(new PrintStream(out, true, StandardCharsets.UTF_8), true);
        compact.render(resolution(List.of(
                new Resolver.Edge(null, "module/core/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/core/1.0", "module/util/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/util/1.0", "module/log/1.0", "1.0", "compile", true),
                new Resolver.Edge(null, "module/web/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/web/1.0", "module/service/1.0", "1.0", "compile", true),
                new Resolver.Edge("module/service/1.0", "module/util/1.0", "1.0", "compile", false)),
                vertices));
        String text = out.toString(StandardCharsets.UTF_8).replaceAll("\033\\[[0-9;]*m", "");
        String tree = text.substring(0, text.indexOf("Resolved dependencies:"));
        assertThat(tree.split("module/util", -1).length - 1).isEqualTo(1);
        assertThat(tree.indexOf("module/service")).isLessThan(tree.indexOf("module/util"));
        assertThat(tree.indexOf("module/util")).isLessThan(tree.indexOf("module/core"));
    }

    @Test
    public void lists_resolved_dependencies_below_the_tree() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("maven/g/a", new Resolver.Vertex("1.0", null, false, false, List.of()));
        report.render(resolution(List.of(
                new Resolver.Edge(null, "maven/g/a/1.0", "1.0", "compile", true)),
                vertices));
        String text = output();
        assertThat(text).contains("Resolved dependencies:");
        assertThat(text).contains("maven/g/a -> 1.0");
    }

    @Test
    public void prints_nothing_when_no_dependencies_were_observed() {
        report.render(resolution(List.of(), new LinkedHashMap<>()));
        assertThat(bytes.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    public void summary_aggregates_licenses_permissiveness_and_module_kinds() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("maven/g/a", new Resolver.Vertex("1.0", "g.a", false,
                false, List.of(new License("Apache-2.0", "permissive", "Apache License 2.0", null))));
        vertices.put("maven/g/b", new Resolver.Vertex("1.0", "g.b", true,
                false, List.of(new License("Apache-2.0", "permissive", "Apache License 2.0", null))));
        vertices.put("maven/g/c", new Resolver.Vertex("1.0", null, false,
                false, List.of(new License("GPL-3.0-only", "strong-copyleft", "GNU GPL v3", null))));
        vertices.put("maven/g/d", new Resolver.Vertex("1.0", null, false, false, List.of()));
        report.summary(vertices);
        String text = output();
        assertThat(text).contains("Licenses:");
        assertThat(text).contains("Apache-2.0");
        assertThat(text).contains("GPL-3.0-only");
        assertThat(text).contains("unknown");
        assertThat(text).contains("Permissiveness:");
        assertThat(text).contains("permissive");
        assertThat(text).contains("strong-copyleft");
        assertThat(text).contains("Modules:");
        assertThat(text).contains("named");
        assertThat(text).contains("automatic");
        assertThat(text).contains("non-modular");
        assertThat(text).contains("2 ( 50%)");
        assertThat(text).contains("2 licenses implied");
    }

    @Test
    public void summary_picks_the_most_permissive_license_and_counts_multi_license_dependencies() {
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        vertices.put("maven/g/a", new Resolver.Vertex("1.0", null, false, false, List.of(
                new License("GPL-3.0-only", "strong-copyleft", "GNU GPL v3", null),
                new License("Apache-2.0", "permissive", "Apache License 2.0", null))));
        vertices.put("maven/g/b", new Resolver.Vertex("1.0", null, false, false, List.of(
                new License("MIT", "permissive", "MIT License", null))));
        report.summary(vertices);
        String text = output();
        assertThat(text).contains("Apache-2.0");
        assertThat(text).doesNotContain("GPL-3.0-only");
        assertThat(text).contains("3 licenses implied");
        assertThat(text).contains("1 dependency offers multiple");
    }

    @Test
    public void summary_prints_nothing_without_dependencies() {
        report.summary(new LinkedHashMap<>());
        assertThat(bytes.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
