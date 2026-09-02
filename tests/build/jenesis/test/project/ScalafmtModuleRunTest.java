package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
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
import build.jenesis.project.ScalafmtModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ScalafmtModuleRunTest {

    private static final String PINS = """
            scalafmt/maven/com.geirsson/metaconfig-core_2.13 0.12.0 SHA-256/2c91199ae5b206afdd52538f8c4da67c1794bcce0b5b06cf25679db00cf32c19
            scalafmt/maven/com.geirsson/metaconfig-pprint_2.13 0.12.0 SHA-256/6d8b0b4279116c11d4f29443bd2a9411bedb3d86ccaf964599a9420f530ed062
            scalafmt/maven/com.geirsson/metaconfig-typesafe-config_2.13 0.12.0 SHA-256/b4c5dbf863dadde363d8bd24333ce1091fec94fc5b88efd04607a26f3eab61b8
            scalafmt/maven/com.github.scopt/scopt_2.13 4.1.0 SHA-256/2e5037bda974630b046794274e344273919abf4727acfcd86352617dce68f82b
            scalafmt/maven/com.googlecode.java-diff-utils/diffutils 1.3.0 SHA-256/61ba4dc49adca95243beaa0569adc2a23aedb5292ae78aa01186fa782ebdc5c2
            scalafmt/maven/com.lihaoyi/fansi_2.13 0.4.0 SHA-256/0eb11a2a905d608033ec1642b0a9f0d8444daa4ad465f684b50bdc7e7a41bf53
            scalafmt/maven/com.lihaoyi/sourcecode_2.13 0.3.0 SHA-256/6e5b2d55e942b450a222bfd3ebc23e99ca03716e42da25af1b2c8cde038100f5
            scalafmt/maven/com.martiansoftware/nailgun-server 0.9.1 SHA-256/4518faa6bf4bd26fccdc4d85e1625dc679381a08d56872d8ad12151dda9cef25
            scalafmt/maven/com.typesafe/config 1.4.3 SHA-256/8ada4c185ce72416712d63e0b5afdc5f009c0cdf405e5f26efecdf156aa5dfb6
            scalafmt/maven/io.get-coursier/interface 0.0.17 SHA-256/b3987e8c02441e82d88ab8727acd64eabf3a35217ffedba904b125e06a722a77
            scalafmt/maven/io.github.java-diff-utils/java-diff-utils 4.12 SHA-256/9990a2039778f6b4cc94790141c2868864eacee0620c6c459451121a901cd5b5
            scalafmt/maven/net.java.dev.jna/jna 5.13.0 SHA-256/66d4f819a062a51a1d5627bffc23fac55d1677f0e0a1feba144aabdd670a64bb
            scalafmt/maven/org.jline/jline 3.22.0 SHA-256/7c3ec8d2c5815188bbaefa4c7c42bc9b8ec172382ca026a4b4f3d113c0b5c3e3
            scalafmt/maven/org.scala-lang.modules/scala-collection-compat_2.13 2.11.0 SHA-256/0c1108883b7b97851750e8932f9584346ccb23f1260c197f97295ac2e6c56cec
            scalafmt/maven/org.scala-lang.modules/scala-parallel-collections_2.13 1.0.4 SHA-256/68f266c4fa37cb20a76e905ad940e241190ce288b7e4a9877f1dd1261cd1a9a7
            scalafmt/maven/org.scala-lang/scala-compiler 2.13.11 SHA-256/c5a14770370e73a69367b131da1533890200b1e2aa70643b73f9ff31ef2e69ec
            scalafmt/maven/org.scala-lang/scala-library 2.13.13 SHA-256/b58b223f9b59f88d04c9dc9ec26f4b463e93e6f296de5663dbd710a7dfce2743
            scalafmt/maven/org.scala-lang/scala-reflect 2.13.13 SHA-256/8e7fa6fd2f9682035bfeae45160afa38ca67aec7dfc614146a758efdeb2ffafc
            scalafmt/maven/org.scala-lang/scalap 2.13.11 SHA-256/ac358699f40002fb4f32ad77531765fce23425d0e83c51854d1635118ab285ea
            scalafmt/maven/org.scalameta/common_2.13 4.9.9 SHA-256/be66ba789863c65abfc9c1e448339ce915f2bc778daf348d884a967e8eb473ee
            scalafmt/maven/org.scalameta/mdoc-parser_2.13 2.5.4 SHA-256/a36fc6125666047b653f8acb1aad16db4aefaaaffdc3f53d2b9eeec83dc580bf
            scalafmt/maven/org.scalameta/parsers_2.13 4.9.9 SHA-256/ab4198d993b4214d9b98277f96c4ac76a72b87a1fea8df96e9be8e3e98176d7a
            scalafmt/maven/org.scalameta/scalafmt-cli_2.13 3.8.3 SHA-256/1d1862d8dfb1d65c788cb366b542ac9934d147b55d0516bf81dd58806b055af8
            scalafmt/maven/org.scalameta/scalafmt-config_2.13 3.8.3 SHA-256/175c7e345baccb75e0f79aa9d8c821834b4b232d3917039c44ca2f0265f2110a
            scalafmt/maven/org.scalameta/scalafmt-core_2.13 3.8.3 SHA-256/c214d16a746ceab8ac47b97c18d2817f726174dd58da75d43472d045ddc25009
            scalafmt/maven/org.scalameta/scalafmt-dynamic_2.13 3.8.3 SHA-256/a3b79331a8f8d57ba57820de3ef0dd0f5608f3d0547403ed724bc09e1a17ea2a
            scalafmt/maven/org.scalameta/scalafmt-interfaces 3.8.3 SHA-256/37510a20442b73f68e6112c2bee6dcd8d60760e0a1ed5758c9949e587e61e954
            scalafmt/maven/org.scalameta/scalafmt-sysops_2.13 3.8.3 SHA-256/981b5455b956ece0e7f2c0825241c6f99b2d70cc2352700a2fcffa5c01ed6633
            scalafmt/maven/org.scalameta/scalameta_2.13 4.9.9 SHA-256/01a3c1130202400dbcf4ea0f42374c8e392b9199716ddf605217f4bf1f61cb1d
            scalafmt/maven/org.scalameta/svm-subs 101.0.0 SHA-256/b31eb8ef90bec4c22a8ec858f5bd007bd46ce80c3dcef9dce238c6f9dd15c1a4
            scalafmt/maven/org.scalameta/trees_2.13 4.9.9 SHA-256/d016cde916b19d6c814ac296544a1882b96664ac03e5ef27019a518482c3db49
            scalafmt/maven/org.typelevel/paiges-core_2.13 0.4.3 SHA-256/4daa8b180b466634b66be040e1097c107981c0ba0b7c605e2f7c0b66ae1b99b5
            """;

    private static final String VERSION = "3.8.3";

    private static final String CONFIG = """
            version = "3.8.3"
            runner.dialect = scala213
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve(".scalafmt.conf"), CONFIG);
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.scala"), "package sample\nclass Sample {   def  f( ) :Int=42 }\n");
    }

    @Test
    public void report_only_runs_the_pinned_scalafmt_and_flags_the_misformatted_file() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "scalafmt",
                new ScalafmtModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("scalafmt").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned scalafmt version resolves")
                    .anyMatch(name -> name.contains("scalafmt-cli") && name.contains(VERSION));
        }
        Path supplement = root.resolve("scalafmt").resolve("check").resolve("supplement");
        String captured = Files.readString(supplement.resolve("output")) + Files.readString(supplement.resolve("error"));
        assertThat(captured)
                .as("scalafmt --test reports the misformatted file")
                .contains("Sample.scala");
    }

    @Test
    public void strict_mode_fails_the_build_on_a_misformatted_file() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "scalafmt",
                new ScalafmtModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT)
                        .strict(true),
                "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("Unexpected exit code");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
