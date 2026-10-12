package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Project;
import build.jenesis.project.AssemblyDescriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProjectReproductionTest {

    private final List<String> printed = new CopyOnWriteArrayList<>();

    @TempDir
    private Path root;

    @Test
    public void reads_the_folder_of_the_second_build_from_its_setting_and_checks_nothing_without_it() {
        assertThat(Project.ofEnvironment(new Environment(Map.of("project.reproduce", "again")), root).reproduce())
                .isEqualTo(Path.of("again"));
        assertThat(Project.ofEnvironment(Environment.NONE, root).reproduce()).isNull();
    }

    @Test
    public void two_builds_of_a_deterministic_project_compare_equal_and_agree_on_the_fingerprint() throws IOException {
        project(new Stamp(false), "target").build();
        project(new Stamp(false), "elsewhere").build();
        List<String> reproduced = printed.stream().filter(line -> line.startsWith("The build reproduced")).toList();
        assertThat(reproduced).hasSize(2).allSatisfy(line -> assertThat(line).contains("3 steps wrote the same output"));
        assertThat(reproduced.getFirst().substring(reproduced.getFirst().indexOf("fingerprint")))
                .as("a build into another folder agrees on the fingerprint, as another machine would")
                .matches("fingerprint SHA-256:[0-9a-f]{64}")
                .isEqualTo(reproduced.getLast().substring(reproduced.getLast().indexOf("fingerprint")));
        assertThat(root.resolve("reproduced")).as("a clean comparison leaves no second tree behind").doesNotExist();
    }

    @Test
    public void reports_a_step_writing_a_random_value_as_the_cause_and_the_steps_after_it_as_consequences_without_failing() throws IOException {
        project(new Stamp(true), "target").build();
        String message = printed.stream().filter(line -> line.startsWith("The build did not reproduce")).findFirst().orElseThrow();
        assertThat(message)
                .containsPattern("3 of \\d+ steps wrote a different output")
                .containsPattern("fingerprint SHA-256:[0-9a-f]{64}\\n")
                .containsPattern("Caused by [^\\n]*\\n  [^\\n]*/stamp: value\\.txt\\n");
        String consequences = message.substring(message.indexOf("handed a different input"));
        assertThat(consequences).containsPattern("\\n  [^\\n]*/copy(\\n|$)").containsPattern("\\n  [^\\n]*/archive(\\n|$)");
        assertThat(message.indexOf("/stamp")).isLessThan(message.indexOf("handed a different input"));
        assertThat(root.resolve("reproduced")).as("a difference keeps the second tree to inspect").isDirectory();
    }

    @Test
    public void prints_the_summary_alone_when_asked_not_to_name_the_steps() throws IOException {
        project(new Environment(Map.of("print.reproduce", "false")), new Stamp(true), "target").build();
        assertThat(printed)
                .anyMatch(line -> line.startsWith("The build did not reproduce") && !line.contains("\n"));
    }

    @Test
    public void refuses_a_reproduce_folder_that_is_not_empty_before_building() throws IOException {
        Files.writeString(Files.createDirectories(root.resolve("reproduced")).resolve("left.txt"), "left");
        Project project = project(new Stamp(false), "target");
        assertThatThrownBy(project::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reproduced")
                .hasMessageContaining("not an empty folder");
        assertThat(root.resolve("target")).doesNotExist();
    }

    @Test
    public void consults_no_build_cache_in_the_second_build() throws IOException {
        List<Path> touched = new CopyOnWriteArrayList<>();
        Recording cache = new Recording(touched);
        project(new Stamp(false), "target")
                .cache(cache)
                .configurator(() -> new BuildExecutor.Configuration().cache(cache))
                .build();
        assertThat(touched).as("the first build consults and fills both caches").isNotEmpty();
        Path target = root.resolve("target").toAbsolutePath().normalize();
        assertThat(touched).as("the second build consults and fills neither")
                .allMatch(path -> path.toAbsolutePath().normalize().startsWith(target));
        assertThat(printed).anyMatch(line -> line.startsWith("The build reproduced"));
    }

    private Project project(BuildStep stamp, String target) throws IOException {
        return project(Environment.NONE, stamp, target);
    }

    private Project project(Environment environment, BuildStep stamp, String target) throws IOException {
        Files.writeString(Files.createDirectories(root.resolve("sources")).resolve("module-info.java"), "module sample { }\n");
        return Project.ofEnvironment(environment.out(printed::add), root)
                .target(root.resolve(target))
                .reproduce(root.resolve("reproduced"))
                .layout(Project.Layout.MODULAR)
                .assembler((_, _, _) -> new AssemblyDescriptor((executor, _) -> {
                    executor.addStep("stamp", stamp);
                    executor.addStep("copy", new Copy(""), "stamp");
                    executor.addStep("archive", new Copy(BuildStep.ARTIFACTS), "copy");
                }));
    }

    record Stamp(boolean random) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Files.writeString(context.next().resolve("value.txt"), random ? UUID.randomUUID().toString() : "value");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    record Copy(String folder) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Path next = Files.createDirectories(context.next().resolve(folder));
            for (BuildStepArgument argument : arguments.values()) {
                try (Stream<Path> files = Files.walk(argument.folder())) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Files.copy(file, next.resolve(file.getFileName()));
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    record Recording(List<Path> touched) implements BuildExecutorCache {

        @Override
        public Optional<BuildStepResult> fetch(Executor executor,
                                               String identity,
                                               byte[] step,
                                               SequencedMap<String, Map<Path, byte[]>> inputs,
                                               boolean remote,
                                               Path target) {
            touched.add(target);
            return Optional.empty();
        }

        @Override
        public void store(Executor executor,
                          String identity,
                          byte[] step,
                          SequencedMap<String, Map<Path, byte[]>> inputs,
                          boolean remote,
                          Path output,
                          String digest,
                          Map<Path, byte[]> checksums) {
            touched.add(output);
        }

        @Override
        public boolean stores() {
            return true;
        }
    }
}
