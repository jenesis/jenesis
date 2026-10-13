package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.step.FormatBuildStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FormatBuildStepTest {

    private static final List<List<String>> VERIFIED = new CopyOnWriteArrayList<>();

    @TempDir
    private Path root;
    private Path tool, sources;

    @BeforeEach
    public void setUp() throws IOException {
        VERIFIED.clear();
        tool = Files.createDirectory(root.resolve("tool"));
        Files.writeString(tool.resolve("formatter.jar"), "1.0");
        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("formatter/runtime/maven/org.example/formatter/1.0", "formatter.jar");
        dependencies.store(tool.resolve(BuildStep.DEPENDENCIES));
        sources = Files.createDirectories(root.resolve("input").resolve(BuildStep.SOURCES));
        Files.writeString(sources.resolve("A.java"), "class A { }");
        Files.writeString(sources.resolve("B.java"), "class B { }");
    }

    @Test
    public void a_verifying_formatter_is_skipped_when_none_of_its_inputs_changed() throws IOException {
        assertThat(new Formatter(true).shouldRun(arguments(ChecksumStatus.RETAINED)))
                .as("a check of unchanged sources has nothing new to report")
                .isFalse();
    }

    @Test
    public void a_verifying_formatter_runs_when_a_source_changed() throws IOException {
        assertThat(new Formatter(true).shouldRun(arguments(ChecksumStatus.ALTERED))).isTrue();
    }

    @Test
    public void a_rewriting_formatter_runs_on_every_build() throws IOException {
        assertThat(new Formatter(false).shouldRun(arguments(ChecksumStatus.RETAINED))).isTrue();
    }

    @Test
    public void verifies_only_the_sources_changed_since_the_last_passing_run() throws IOException {
        verify();
        Files.writeString(sources.resolve("A.java"), "class A { int a; }");
        verify();

        assertThat(VERIFIED)
                .as("a source verified unchanged since the last passing run is not verified again")
                .containsExactly(List.of("A.java", "B.java"), List.of("A.java"));
    }

    @Test
    public void an_unformatted_edit_keeps_failing_while_another_source_changes() throws IOException {
        verify();
        Files.writeString(sources.resolve("A.java"), "class A { unformatted }");
        assertThatThrownBy(this::verify).isInstanceOf(RuntimeException.class);
        Files.writeString(sources.resolve("B.java"), "class B { int b; }");
        assertThatThrownBy(this::verify)
                .as("a failed run carries no pass forward for the source it failed on")
                .isInstanceOf(RuntimeException.class);
        Files.writeString(sources.resolve("A.java"), "class A { int a; }");
        verify();

        assertThat(VERIFIED).containsExactly(List.of("A.java", "B.java"),
                List.of("A.java"),
                List.of("A.java", "B.java"),
                List.of("A.java", "B.java"));
    }

    @Test
    public void a_badly_formatted_source_found_by_the_first_run_keeps_failing_when_left_untouched() throws IOException {
        Files.writeString(sources.resolve("A.java"), "class A { unformatted }");
        assertThatThrownBy(this::verify).isInstanceOf(RuntimeException.class);
        Files.writeString(sources.resolve("B.java"), "class B { int b; }");
        assertThatThrownBy(this::verify).isInstanceOf(RuntimeException.class);

        assertThat(VERIFIED).containsExactly(List.of("A.java", "B.java"), List.of("A.java", "B.java"));
    }

    @Test
    public void verifies_every_source_again_when_the_formatter_changed() throws IOException {
        verify();
        Files.writeString(tool.resolve("formatter.jar"), "2.0");
        Files.writeString(sources.resolve("A.java"), "class A { int a; }");
        verify();

        assertThat(VERIFIED)
                .as("another formatter version may judge an unchanged source differently")
                .containsExactly(List.of("A.java", "B.java"), List.of("A.java", "B.java"));
    }

    @Test
    public void verifies_every_source_again_when_the_configuration_changed() throws IOException {
        Files.writeString(tool.resolve(".editorconfig"), "indent_size = 4");
        verify();
        Files.writeString(tool.resolve(".editorconfig"), "indent_size = 2");
        verify();

        assertThat(VERIFIED).containsExactly(List.of("A.java", "B.java"), List.of("A.java", "B.java"));
    }

    private void verify() throws IOException {
        BuildExecutor buildExecutor = BuildExecutor.of(root.resolve("target"),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        buildExecutor.addSource("tool", tool);
        buildExecutor.addSource("input", sources.getParent());
        buildExecutor.addStep("format", new Formatter(true), "tool", "input");
        buildExecutor.execute();
    }

    private SequencedMap<String, BuildStepArgument> arguments(ChecksumStatus status) {
        return new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(root,
                Map.of(Path.of("sources/Sample.java"), Checksum.of(status)))));
    }

    private static class Formatter extends FormatBuildStep {

        private Formatter(boolean verify) {
            super("formatter", "formatter", verify);
        }

        @Override
        protected boolean isFormattable(Path file) {
            return file.toString().endsWith(".java");
        }

        @Override
        protected List<String> command(BuildStepContext context,
                                       List<String> jars,
                                       Path config,
                                       List<String> files,
                                       boolean verify) {
            VERIFIED.add(files.stream().map(file -> Path.of(file).getFileName().toString()).toList());
            boolean unformatted = files.stream().anyMatch(file -> {
                try {
                    return Files.readString(Path.of(file)).contains("unformatted");
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return List.of(unformatted ? "-XX:+UnrecognizedOptionOfTheFormatter" : "-version");
        }
    }
}
