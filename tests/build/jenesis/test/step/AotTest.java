package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Aot;
import build.jenesis.step.JLink;
import build.jenesis.step.JPackage;
import build.jenesis.step.Layers;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AotTest {

    @TempDir
    private Path root;
    private Path bundle, image;

    @BeforeEach
    public void setUp() throws Exception {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"), """
                package sample;
                public class Sample {
                    public static void main(String[] args) throws Exception {
                        System.out.println("Hello " + String.join(" ", args));
                        if (args.length > 0 && args[0].equals("serve")) {
                            Thread.sleep(Long.MAX_VALUE);
                        } else if (args.length > 0 && args[0].equals("fail")) {
                            System.exit(3);
                        }
                    }
                }
                """);
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out,
                System.err,
                "-d",
                classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString())).isZero();
        bundle = Files.createDirectory(root.resolve("bundle"));
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out,
                System.err,
                "--create",
                "--file",
                Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar").toString(),
                "-C",
                classes.toString(),
                ".")).isZero();
        SequencedProperties linking = new SequencedProperties();
        linking.setProperty("--add-modules", "sample");
        ProcessBuildStep.store(bundle, ProcessBuildStep.PROCESS, "jlink", linking);
        SequencedProperties packaging = new SequencedProperties();
        packaging.setProperty("--name", "Sample");
        packaging.setProperty("--module", "sample/sample.Sample");
        ProcessBuildStep.store(bundle, ProcessBuildStep.PROCESS, "jpackage", packaging);
        image = Files.createDirectory(root.resolve("image"));
        assertThat(new JLink(ProcessHandler.Factory.TOOL).apply(Runnable::run,
                context("linking"),
                arguments(bundle)).toCompletableFuture().join().next()).isTrue();
        Files.move(root.resolve("linking").resolve("next").resolve(JLink.RUNTIME), image.resolve(JLink.RUNTIME));
    }

    @Test
    public void trains_a_cache_the_linked_image_loads_the_main_class_from() throws Exception {
        Path next = root.resolve("training").resolve("next");
        assertThat(new Aot().arguments(List.of("Ada")).apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join().next()).isTrue();
        assertThat(next.resolve(Aot.AOT)).isDirectoryContaining("glob:**/sample.aot");
        assertThat(next.resolve(JLink.RUNTIME))
                .as("only an image that is staged carries a copy of the cache")
                .doesNotExist();
        assertThat(root.resolve("training").resolve("supplement").resolve("output")).content().contains("Hello Ada");
        assertThat(launched(image.resolve(JLink.RUNTIME), next.resolve(Aot.AOT).resolve("sample.aot")))
                .contains("sample.Sample source: shared objects file");
    }

    @Test
    public void hands_a_staged_image_the_cache_beside_its_modules() throws Exception {
        Path next = root.resolve("training").resolve("next");
        new Aot().image(true).apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join();
        assertThat(next.resolve(JLink.RUNTIME).resolve("lib").resolve("sample.aot")).isRegularFile();
        assertThat(launched(next.resolve(JLink.RUNTIME), next.resolve(JLink.RUNTIME).resolve("lib").resolve("sample.aot")))
                .as("the copy of the image is the image the cache was recorded on")
                .contains("sample.Sample source: shared objects file");
    }

    @Test
    public void runs_the_main_class_it_is_named_in_place_of_the_declared_one() throws Exception {
        assertThatThrownBy(() -> new Aot().main("sample.Missing").apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join())
                .rootCause()
                .hasMessageContaining("The training run of sample/sample.Missing ended with exit code 1");
    }

    @Test
    public void fails_on_a_training_run_that_ends_with_an_error_although_the_jvm_wrote_a_cache() throws Exception {
        assertThatThrownBy(() -> new Aot().arguments(List.of("fail")).apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ended with exit code 3")
                .hasMessageContaining("To reproduce, execute:");
    }

    @Test
    public void ends_a_run_that_does_not_return_after_its_timeout_and_keeps_the_cache() throws Exception {
        Path next = root.resolve("training").resolve("next");
        assertThat(new Aot().arguments(List.of("serve")).timeout(Duration.ofSeconds(3)).apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join().next()).isTrue();
        assertThat(next.resolve(Aot.AOT))
                .as("the temporary configuration of the recording is no part of the output")
                .isDirectoryNotContaining("glob:**/*.config");
        assertThat(launched(image.resolve(JLink.RUNTIME), next.resolve(Aot.AOT).resolve("sample.aot")))
                .contains("sample.Sample source: shared objects file");
    }

    @Test
    public void a_run_that_returns_before_its_timeout_is_judged_by_its_exit_code() throws Exception {
        assertThatThrownBy(() -> new Aot().arguments(List.of("fail")).timeout(Duration.ofMinutes(1)).apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join())
                .rootCause()
                .hasMessageContaining("ended with exit code 3");
    }

    @Test
    public void refuses_a_main_class_that_is_launched_from_the_class_path() throws Exception {
        SequencedProperties packaging = new SequencedProperties();
        packaging.setProperty("--main-jar", "sample.jar");
        packaging.setProperty("--main-class", "sample.Sample");
        ProcessBuildStep.store(bundle, ProcessBuildStep.PROCESS, "jpackage", packaging);
        assertThatThrownBy(() -> new Aot().apply(Runnable::run, context("training"), arguments(image, bundle)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sample.Sample is launched from the class path")
                .hasMessageContaining("module-info.java");
    }

    @Test
    public void refuses_an_application_of_several_module_layers() throws Exception {
        Files.writeString(bundle.resolve(Layers.MEMBERSHIP), "modulepath.render=render.jar\n");
        assertThatThrownBy(() -> new Aot().apply(Runnable::run, context("training"), arguments(image, bundle)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares further layers")
                .hasMessageContaining("-Djenesis.package.aot=false");
    }

    @Test
    public void trains_nothing_for_a_module_without_a_main_class() throws Exception {
        ProcessBuildStep.store(bundle, ProcessBuildStep.PROCESS, "jpackage", new SequencedProperties());
        assertThat(new Aot().apply(Runnable::run,
                context("training"),
                arguments(image, bundle)).toCompletableFuture().join().next()).isTrue();
        assertThat(root.resolve("training").resolve("next")).isEmptyDirectory();
    }

    @Test
    public void a_packaged_application_launches_from_the_cache() throws Exception {
        new Aot().apply(Runnable::run, context("training"), arguments(image, bundle)).toCompletableFuture().join();
        SequencedProperties options = new SequencedProperties();
        options.setProperty("-Dsample.greeting=hello", "");
        ProcessBuildStep.store(bundle, ProcessBuildStep.PROCESS, "java", options);
        assertThat(new JPackage(ProcessHandler.Factory.TOOL).type("app-image").apply(Runnable::run,
                context("packaging"),
                arguments(image, root.resolve("training").resolve("next"), bundle)).toCompletableFuture().join().next()).isTrue();
        assertThat(root.resolve("packaging").resolve("supplement").resolve("command")).content()
                .as("the launcher names the cache where the image holds it, keeps a cache the JVM refuses quiet"
                        + " and starts with the options the training run was handed")
                .contains("--input")
                .contains("-XX:AOTCache=$APPDIR" + File.separator + "sample.aot")
                .contains("--java-options -Xlog:aot=off")
                .contains("--java-options -Dsample.greeting=hello");
        Path launcher;
        try (Stream<Path> files = Files.walk(root.resolve("packaging").resolve("next").resolve(JPackage.PACKAGES))) {
            launcher = files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().equals("Sample")
                            || file.getFileName().toString().equals("Sample.exe"))
                    .findFirst()
                    .orElseThrow();
        }
        ProcessBuilder builder = new ProcessBuilder(launcher.toString()).redirectErrorStream(true);
        builder.environment().put("JAVA_TOOL_OPTIONS", "-Xlog:class+load");
        Process process = builder.start();
        String printed = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isZero();
        assertThat(printed).contains("sample.Sample source: shared objects file");
    }

    private BuildStepContext context(String name) throws IOException {
        Path folder = Files.createDirectory(root.resolve(name));
        return new BuildStepContext(null,
                Files.createDirectory(folder.resolve("next")),
                Files.createDirectory(folder.resolve("supplement")),
                Files.createDirectory(folder.resolve("persistent")));
    }

    private static SequencedMap<String, BuildStepArgument> arguments(Path... folders) {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        for (Path folder : folders) {
            arguments.put(folder.getFileName().toString(), new BuildStepArgument(
                    folder,
                    Map.of(Path.of("changed"), Checksum.of(ChecksumStatus.ADDED))));
        }
        return arguments;
    }

    private static String launched(Path runtime, Path cache) throws Exception {
        Path java = runtime.resolve("bin").resolve("java.exe");
        ProcessBuilder builder = new ProcessBuilder((Files.isRegularFile(java) ? java : java.resolveSibling("java")).toString(),
                "-XX:AOTCache=" + cache,
                "-XX:AOTMode=on",
                "-Xlog:class+load",
                "-m",
                "sample/sample.Sample").redirectErrorStream(true);
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        Process process = builder.start();
        String printed = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(printed).isZero();
        return printed;
    }
}
