package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.step.Legal;

import static org.assertj.core.api.Assertions.assertThat;

public class LegalTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, module, dependencies;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        module = Files.createDirectories(root.resolve("module").resolve(BuildStep.ARTIFACTS));
        dependencies = Files.createDirectories(root.resolve("dependencies").resolve("resolved"));
        Files.writeString(dependencies.resolveSibling(BuildStep.DEPENDENCIES), "main/runtime/module/org.dep/1.0=resolved/org.dep-1.0.jar\n");
    }

    @Test
    public void takes_the_notices_of_the_module_at_the_root_and_of_each_dependency_in_a_folder_of_its_own() throws IOException {
        jar(module.resolve("classes.jar"), "META-INF/LICENSE", "own licence");
        jar(dependencies.resolve("org.dep-1.0.jar"), "META-INF/LICENSE.txt", "dependency licence");

        apply(Environment.NONE);

        assertThat(next.resolve(Legal.LEGAL + "LICENSE")).hasContent("own licence");
        assertThat(next.resolve(Legal.LEGAL + "org.dep-1.0/LICENSE.txt"))
                .as("META-INF/LICENSE also matches with an extension")
                .hasContent("dependency licence");
    }

    @Test
    public void takes_notices_of_any_case_whole_legal_folders_and_an_about_page() throws IOException {
        jar(module.resolve("classes.jar"), "about.html", "<html/>");
        jar(dependencies.resolve("org.dep-1.0.jar"),
                "META-INF/notice.txt", "lower case notice",
                "META-INF/license/LICENSE.bundled.txt", "bundled licence");

        apply(Environment.NONE);

        assertThat(next.resolve(Legal.LEGAL + "about.html")).exists();
        assertThat(next.resolve(Legal.LEGAL + "org.dep-1.0/notice.txt")).exists();
        assertThat(next.resolve(Legal.LEGAL + "org.dep-1.0/LICENSE.bundled.txt"))
                .as("META-INF/license/ takes the folder below it")
                .exists();
    }

    @Test
    public void takes_what_is_there_when_a_jar_carries_no_notices() throws IOException {
        jar(module.resolve("classes.jar"), "META-INF/LICENSE", "own licence");
        jar(dependencies.resolve("org.dep-1.0.jar"), "org/dep/Dep.class", "");

        apply(Environment.NONE);

        assertThat(next.resolve(Legal.LEGAL + "LICENSE")).exists();
        assertThat(next.resolve(Legal.LEGAL + "org.dep-1.0")).doesNotExist();
    }

    private void apply(Environment environment) throws IOException {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        arguments.put("module", new BuildStepArgument(module.getParent(), Map.of(Path.of("artifacts/classes.jar"), Checksum.of(ChecksumStatus.ADDED))));
        arguments.put("dependencies", new BuildStepArgument(dependencies.getParent(), Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))));
        Legal.ofEnvironment(environment).apply(Runnable::run, new BuildStepContext(previous, next, supplement), arguments)
                .toCompletableFuture()
                .join();
    }

    private static void jar(Path file, String... entries) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            for (int index = 0; index < entries.length; index += 2) {
                out.putNextEntry(new JarEntry(entries[index]));
                out.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}
