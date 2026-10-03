package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenRepository;
import build.jenesis.maven.MavenVersionNegotiator;
import build.jenesis.step.JarSigner;
import build.jenesis.step.OsvDownload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildStepHashFunctionTest {

    @Test
    public void hashes_serializable_step_by_class_and_fields() throws IOException {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("MD5");
        byte[] first = hash.hash(new ConfigurableStep("foo"));
        byte[] second = hash.hash(new ConfigurableStep("foo"));
        byte[] different = hash.hash(new ConfigurableStep("bar"));
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    public void hashes_path_fields_independent_of_separator() throws IOException {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("MD5");
        byte[] forwardSlash = hash.hash(new PathStep(Path.of("nested", "file")));
        byte[] backSlash = hash.hash(new PathStep(Path.of("nested\\file")));
        assertThat(forwardSlash).isEqualTo(backSlash);
    }

    @Test
    public void a_setting_that_shapes_the_output_shapes_the_key_and_one_that_does_not_leaves_it_alone()
            throws IOException {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("MD5");
        assertThat(hash.hash(JarSigner.ofEnvironment(new Environment(Map.of("jarsigner.alias", "one")))))
                .as("the key a jar is signed with decides what the step produces, so a step signed with"
                        + " another one is not the cached step")
                .isNotEqualTo(hash.hash(JarSigner.ofEnvironment(new Environment(Map.of("jarsigner.alias", "two")))));
        assertThat(hash.hash(OsvDownload.ofEnvironment(new Environment(Map.of("repository.insecure", "true")))))
                .as("allowing an insecure scheme changes what the step may reach, never what it produces,"
                        + " so it stays out of the key as every print setting does")
                .isEqualTo(hash.hash(OsvDownload.ofEnvironment(Environment.NONE)));
    }

    @Test
    public void a_negotiator_a_resolver_was_given_decides_the_key() throws IOException {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("MD5");
        assertThat(hash.hash(new ResolverStep(resolver(new FixedVersion("1.0")))))
                .as("which version a negotiator answers with decides what a resolution produces,"
                        + " so a step given another one is not the cached step")
                .isNotEqualTo(hash.hash(new ResolverStep(resolver(new FixedVersion("2.0")))));
    }

    private static MavenPomResolver resolver(MavenVersionNegotiator negotiator) {
        return new MavenPomResolver((Supplier<MavenVersionNegotiator> & Serializable) () -> negotiator);
    }

    @Test
    public void a_step_is_keyed_by_the_code_that_defines_it_as_well_as_by_its_fields(@TempDir Path root)
            throws Exception {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("MD5");
        Path first = probe(root.resolve("first"), "true"), second = probe(root.resolve("second"), "false");
        assertThat(hash.hash(load(first)))
                .as("a step whose code changed but whose name, serialVersionUID and fields did not is another step,"
                        + " as a step of an upgraded engine or plugin is")
                .isNotEqualTo(hash.hash(load(second)));
        assertThat(hash.hash(load(first))).isEqualTo(hash.hash(load(first)));
    }

    private static Path probe(Path folder, String next) throws Exception {
        Path source = Files.createDirectories(folder.resolve("source")).resolve("Probe.java");
        Files.writeString(source, """
                package probe;

                public class Probe implements build.jenesis.BuildStep {

                    private static final long serialVersionUID = 1L;

                    private final String value = "unchanged";

                    @Override
                    public java.util.concurrent.CompletionStage<build.jenesis.BuildStepResult> apply(
                            java.util.concurrent.Executor executor,
                            build.jenesis.BuildStepContext context,
                            java.util.SequencedMap<String, build.jenesis.BuildStepArgument> arguments) {
                        return java.util.concurrent.CompletableFuture.completedStage(new build.jenesis.BuildStepResult(%s));
                    }
                }
                """.formatted(next));
        Path classes = Files.createDirectories(folder.resolve("classes"));
        String engine = Path.of(BuildStep.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        assertThat(javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classes.toString(), "-cp", engine, source.toString())).isZero();
        return classes;
    }

    private static BuildStep load(Path classes) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, BuildStep.class.getClassLoader());
        return (BuildStep) loader.loadClass("probe.Probe").getConstructor().newInstance();
    }

    @Test
    public void throws_for_non_serializable_step() {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("MD5");
        BuildStep step = new NonSerializableStep();
        assertThatThrownBy(() -> hash.hash(step)).isInstanceOf(NotSerializableException.class);
    }

    private record ConfigurableStep(String value) implements BuildStep {
        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record PathStep(Path path) implements BuildStep {
        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record FixedVersion(String version) implements MavenVersionNegotiator {
        @Override
        public String resolve(Executor executor,
                              MavenRepository repository,
                              String groupId,
                              String artifactId,
                              String type,
                              String classifier,
                              String version) {
            return this.version;
        }
    }

    private record ResolverStep(MavenPomResolver resolver) implements BuildStep {
        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private class NonSerializableStep implements BuildStep {
        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
