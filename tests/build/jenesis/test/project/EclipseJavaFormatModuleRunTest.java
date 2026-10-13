package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Pinning;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.EclipseJavaFormatModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EclipseJavaFormatModuleRunTest {

    private static final String PINS = """
            eclipse-java-format/maven/net.java.dev.jna/jna 5.19.1 SHA-256/4fb141dd8ef6b0585ffceea4bc49602fbc6312fa977e2c488794ea3e6aafecae
            eclipse-java-format/maven/net.java.dev.jna/jna-platform 5.19.1 SHA-256/3b3864f5b449e9c3c24b16861524b622b086563f44e0cd8384c8efc5a6052f82
            eclipse-java-format/maven/org.eclipse.jdt/ecj 3.46.100 SHA-256/74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229
            eclipse-java-format/maven/org.eclipse.jdt/org.eclipse.jdt.core 3.47.0 SHA-256/723e30be1e841516c388ebb32017cb8b36662808f46be5265b78cc06bd4fe71c
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.commands 3.13.0 SHA-256/fe916979793d364fcd0a71c15c3fa803d60a8566cff8c64b0eef04918b5b6a65
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.contenttype 3.9.900 SHA-256/6c57132740523b5c5de125c0041ca43c1606da6563214c1bae3999fa21ead893
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.expressions 3.9.700 SHA-256/06a18236ae181659e1e5499d912d68de527720686f1f51dab632d3e298e32b78
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.filesystem 1.11.500 SHA-256/bf9ad56f6bee0044829bcbbe0ac5b9df28bb354beb0a4bdf0fe9a489ded12d36
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.jobs 3.15.900 SHA-256/941b3c5feeba4ec0a949a8a94ff10283b2505813fa736c9e6345f146ae1b7e8a
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.resources 3.24.100 SHA-256/946851cf5902cd3b002920063a30b169e5710088b6c1ad7dd05bdd3be4fbfff4
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.core.runtime 3.35.0 SHA-256/5b0c2794e9fe1785360dec920cc802f3388a0eb72ee25b89054b79ff3e2f07c9
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.equinox.app 1.7.600 SHA-256/fd353398835107608f554779a6e91891725efb94e0f57559d0ab3cea4b6156c7
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.equinox.common 3.21.0 SHA-256/2545ed27d408f19882695703097e5998bf25652bd0dac76a8e6d1391ec228ab7
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.equinox.preferences 3.12.100 SHA-256/fd04e81a28ab32b6bb073286eb293c44b760176fcf1e35ef15614d62df820f86
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.equinox.registry 3.12.600 SHA-256/4f8cd72681446b598390a8c24ee9b9996f6e4c5fabed022b50bd26f6f5a0bf6c
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.osgi 3.24.300 SHA-256/4a770aa4a78df653af871073c8b4ddd56884895f5fc71d10c40b4289ac7c8b9f
            eclipse-java-format/maven/org.eclipse.platform/org.eclipse.text 3.14.800 SHA-256/14814c1f1a737fde0a2d56c47a2f02cf4c8c5dee748756c4763e674cae214250
            eclipse-java-format/maven/org.osgi/org.osgi.service.prefs 1.1.2 SHA-256/43c7c870710e363405d422da653cce0d798a4537f76e4930f79bceadd3a55345
            eclipse-java-format/maven/org.osgi/osgi.annotation 8.0.1 SHA-256/a0e8a4c362bd3600812f37b0ea45fba966c7bc049d01fed56a09ecc74082759e
            """;
    private static final String PROFILE = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <profiles version="23">
            <profile kind="CodeFormatterProfile" name="Sample" version="23">
            <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="%s"/>
            <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="3"/>
            <setting id="org.eclipse.jdt.core.formatter.indentation.size" value="3"/>
            <setting id="org.eclipse.jdt.core.compiler.source" value="1.8"/>
            </profile>
            </profiles>
            """;

    @TempDir
    private Path root, project, configuration;

    @Test
    public void formats_in_place_with_the_profile_then_skips_an_unchanged_file_on_a_second_run() throws IOException {
        Path sample = sample("package sample;record Sample(int a){int f(Object o){return switch(o){case String s->1;default->2;};}}\n");
        Path profile = Files.writeString(configuration.resolve("formatter.xml"), PROFILE.formatted("space"));

        newExecutor(false, profile).execute();

        String formatted = Files.readString(sample);
        assertThat(formatted)
                .as("the source is formatted in place, indented as the profile says, and a record and a pattern switch"
                        + " parse although the profile names Java 8")
                .contains("record Sample(int a) {")
                .contains("\n   int f(Object o) {")
                .contains("case String s -> 1;");
        Path command = root.resolve("eclipse-java-format").resolve("format").resolve("supplement").resolve("command");
        assertThat(command).as("the first run forks the formatter").exists();

        newExecutor(false, profile).execute();

        assertThat(Files.readString(sample)).as("the already-formatted file is left byte-identical").isEqualTo(formatted);
        assertThat(command).as("the formatter is not forked again when nothing changed").doesNotExist();
    }

    @Test
    public void verify_mode_fails_naming_the_file_and_verifies_again_when_the_profile_changes() throws IOException {
        Path sample = sample("package sample;\n\nclass Sample {\n   int f() {\n      return 42;\n   }\n}\n");
        Path profile = Files.writeString(configuration.resolve("formatter.xml"), PROFILE.formatted("space"));

        newExecutor(true, profile).execute();

        Files.writeString(profile, PROFILE.formatted("tab"));
        assertThatThrownBy(newExecutor(true, profile)::execute)
                .as("the unchanged source is verified again once the profile changes")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected exit code")
                .hasMessageContaining("Sample.java is not formatted as the profile says");
        assertThat(sample).as("verify mode never rewrites the source")
                .content()
                .contains("\n   int f() {");
    }

    private Path sample(String source) throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java");
        Files.writeString(sample, source);
        return sample;
    }

    private BuildExecutor newExecutor(boolean verify, Path profile) throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("project", project);
        executor.addModule(
                "eclipse-java-format",
                new EclipseJavaFormatModule(Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)), Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE)))
                        .pinning(Pinning.STRICT)
                        .verify(verify)
                        .profile(profile),
                "project");
        return executor;
    }
}
