package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Output;
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
        assertThat(hash.hash(JarSigner.ofKeys(Map.of("jarsigner.alias", "one")::get, new Output())))
                .as("the key a jar is signed with decides what the step produces, so a step signed with"
                        + " another one is not the cached step")
                .isNotEqualTo(hash.hash(JarSigner.ofKeys(Map.of("jarsigner.alias", "two")::get, new Output())));
        assertThat(hash.hash(OsvDownload.ofKeys(Map.of("repository.insecure", "true")::get, new Output())))
                .as("allowing an insecure scheme changes what the step may reach, never what it produces,"
                        + " so it stays out of the key as every print setting does")
                .isEqualTo(hash.hash(OsvDownload.ofKeys(SequencedProperties.NONE, new Output())));
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
