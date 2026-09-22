package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Output;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.JApiCmpModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static build.jenesis.SequencedProperties.SYSTEM;

public class JApiCmpModuleRunTest {

    private static final String PINS = """
            japicmp/maven/com.github.siom79.japicmp/japicmp 0.26.2 SHA-256/317c4d11ef25469203d6de9c5b804c62392f52e3edc9e42e42161a192335bc3a
            japicmp/maven/com.google.errorprone/error_prone_annotations 2.36.0 SHA-256/77440e270b0bc9a249903c5a076c36a722c4886ca4f42675f2903a1c53ed61a5
            japicmp/maven/com.google.guava/failureaccess 1.0.3 SHA-256/cbfc3906b19b8f55dd7cfd6dfe0aa4532e834250d7f080bd8d211a3e246b59cb
            japicmp/maven/com.google.guava/guava 33.4.8-jre SHA-256/f3d7f57f67fd622f4d468dfdd692b3a5e3909246c28017ac3263405f0fe617ed
            japicmp/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
            japicmp/maven/com.google.j2objc/j2objc-annotations 3.0.0 SHA-256/88241573467ddca44ffd4d74aa04c2bbfd11bf7c17e0c342c94c9de7a70a7c64
            japicmp/maven/com.sun.istack/istack-commons-runtime 3.0.8 SHA-256/4ffabb06be454a05e4398e20c77fa2b6308d4b88dfbef7ca30a76b5b7d5505ef
            japicmp/maven/com.sun.xml.fastinfoset/FastInfoset 1.2.16 SHA-256/056f3a1e144409f21ed16afc26805f58e9a21f3fce1543c42d400719d250c511
            japicmp/maven/jakarta.activation/jakarta.activation-api 1.2.1 SHA-256/8b0a0f52fa8b05c5431921a063ed866efaa41dadf2e3a7ee3e1961f2b0d9645b
            japicmp/maven/jakarta.xml.bind/jakarta.xml.bind-api 2.3.2 SHA-256/69156304079bdeed9fc0ae3b39389f19b3cc4ba4443bc80508995394ead742ea
            japicmp/maven/org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
            japicmp/maven/org.glassfish.jaxb/jaxb-runtime 2.3.2 SHA-256/e6e0a1e89fb6ff786279e6a0082d5cef52dc2ebe67053d041800737652b4fd1b
            japicmp/maven/org.glassfish.jaxb/txw2 2.3.2 SHA-256/4a6a9f483388d461b81aa9a28c685b8b74c0597993bf1884b04eddbca95f48fe
            japicmp/maven/org.javassist/javassist 3.30.2-GA SHA-256/eba37290994b5e4868f3af98ff113f6244a6b099385d9ad46881307d3cb01aaf
            japicmp/maven/org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
            japicmp/maven/org.jvnet.staxex/stax-ex 1.8.1 SHA-256/20522549056e9e50aa35ef0b445a2e47a53d06be0b0a9467d704e2483ffb049a
            """;
    private static final String BASELINE = "org.apiguardian/apiguardian-api/1.1.2";
    private static final String COMPATIBLE = """
            package org.apiguardian.api;
            public @interface API {
                Status status();
                String since() default "";
                String[] consumers() default "*";
                enum Status {
                    INTERNAL, DEPRECATED, EXPERIMENTAL, MAINTAINED, STABLE;
                }
            }
            """;

    private static final String BREAKING = """
            package org.apiguardian.api;
            public @interface API {
                Status status();
                String since() default "";
                enum Status {
                    INTERNAL, DEPRECATED, EXPERIMENTAL, MAINTAINED, STABLE;
                }
            }
            """;

    @TempDir
    private Path root, project, sources;

    @Test
    public void downloads_the_pinned_japicmp_and_reports_on_the_released_baseline() throws IOException {
        writeProject(COMPATIBLE);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("japicmp", module(new SequencedProperties()), "project");
        executor.execute();

        Path report = root.resolve("japicmp").resolve("compare").resolve("output")
                .resolve(BuildStep.REPORTS).resolve("japicmp").resolve("japicmp-report.xml");
        assertThat(report).isNotEmptyFile();
        assertThat(report).content()
                .as("the released baseline is what the built jar is compared against")
                .contains("oldVersion=\"1.1.2\"")
                .contains("org.apiguardian.api.API");
    }

    @Test
    public void a_removed_method_is_reported_without_failing_the_build() throws IOException {
        writeProject(BREAKING);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("japicmp", module(new SequencedProperties()), "project");
        executor.execute();

        Path report = root.resolve("japicmp").resolve("compare").resolve("output")
                .resolve(BuildStep.REPORTS).resolve("japicmp").resolve("japicmp-report.xml");
        assertThat(report).content()
                .as("report-only run records the removal and leaves the build green")
                .contains("consumers");
    }

    @Test
    public void the_binary_incompatibility_gate_fails_the_build_on_a_removed_method() throws IOException {
        writeProject(BREAKING);
        SequencedProperties config = new SequencedProperties();
        config.setProperty("error-on-binary-incompatibility", "true");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("japicmp", module(config), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("Unexpected exit code");
    }

    private JApiCmpModule module(SequencedProperties config) {
        config.setProperty("baseline", BASELINE);
        return new JApiCmpModule(
                Map.of("maven", MavenDefaultRepository.ofKeys(SYSTEM, new Output())),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM)))
                .pinning(Pinning.STRICT)
                .config(config);
    }

    private void writeProject(String annotation) throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));

        Path source = Files.createDirectories(sources.resolve("org").resolve("apiguardian").resolve("api"))
                .resolve("API.java");
        Files.writeString(source, annotation);
        Path classes = Files.createDirectories(sources.resolve("classes"));
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classes.toString(), "--release", "17", source.toString());
        assertThat(rc).as("the vendored annotation compiles").isZero();

        Path artifact = Files.createDirectories(project.resolve(BuildStep.ARTIFACTS)).resolve("classes.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifact))) {
            Path folder = classes.resolve("org").resolve("apiguardian").resolve("api");
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file : files.sorted().toList()) {
                    jar.putNextEntry(new JarEntry("org/apiguardian/api/" + file.getFileName()));
                    Files.copy(file, jar);
                    jar.closeEntry();
                }
            }
        }
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
