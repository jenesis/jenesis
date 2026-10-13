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
import build.jenesis.project.RevapiModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RevapiModuleRunTest {

    private static final String PINS = """
            revapi/maven/com.fasterxml.jackson.core/jackson-annotations 2.10.4 SHA-256/9d07ea7ce579a678e7aea61249fa82c46469af9d02c5b5f13e84beb7b0827dbc
            revapi/maven/com.fasterxml.jackson.core/jackson-core 2.13.3 SHA-256/ab119a8ea3cc69472ebc0e870b849bfbbe536ad57d613dc38453ccd592ca6a3d
            revapi/maven/com.fasterxml.jackson.core/jackson-databind 2.10.4 SHA-256/55312662a420c71508e6159c86aa41c1694c52e89a1b90dc94bcf4358134005e
            revapi/maven/com.networknt/json-schema-validator 1.0.43 SHA-256/7c10b089a973fe15d13fbe3205c30eb751d6770354ffe77643c9d6133073a7bf
            revapi/maven/org.antlr/antlr4-runtime 4.7.2 SHA-256/4c518b87d4bdff8b44cd8cbc1af816e944b62a3fe5b80b781501cf1f4759bbc4
            revapi/maven/org.apache.commons/commons-lang3 3.5 SHA-256/8ac96fc686512d777fca85e144f196cd7cfe0c0aec23127229497d1a38ff651c
            revapi/maven/org.apache.logging.log4j/log4j-api 2.17.1 SHA-256/b0d8a4c8ab4fb8b1888d0095822703b0e6d4793c419550203da9e69196161de4
            revapi/maven/org.apache.logging.log4j/log4j-core 2.17.1 SHA-256/c967f223487980b9364e94a7c7f9a8a01fd3ee7c19bdbf0b0f9f8cb8511f3d41
            revapi/maven/org.jboss/jboss-dmr 1.5.0.Final SHA-256/cbbe302464ff99bc0656be2343958f3eb7a4ffc575e03bb7399fccbb327be6c5
            revapi/maven/org.jruby.jcodings/jcodings 1.0.46 SHA-256/13e7fa99093b729fe736f1bf6886e6230065b36bd3dc9117ee14942883e3e515
            revapi/maven/org.jruby.joni/joni 2.1.31 SHA-256/96c8d65e99d16a2ee48046fa352a972feadbc15420ed576a3c13d8339cdbe1f0
            revapi/maven/org.revapi.classif/classif 0.1.1 SHA-256/56b3a356500b2a0acfa964b960d593361acd6c6324bcd13ffd26802b38b44412
            revapi/maven/org.revapi.classif/classif-dsl 0.1.1 SHA-256/eb62873779b25e8b997172b0e88900ac0835489611d7b24d0521861e6ffee081
            revapi/maven/org.revapi/revapi 0.15.1 SHA-256/c41a393d7216e56f98d66a57ca964ffceb658849b42ef488b71f89a600beae72
            revapi/maven/org.revapi/revapi-basic-features 0.13.1 SHA-256/9eade0d8c45f2d99b5b39b42b2f63cb15904db6f3583c89c370b78e8825f76e2
            revapi/maven/org.revapi/revapi-java 0.28.4 SHA-256/95f9edd7082703037fa229a415bc7aaf14b60a85319c893d1a9cd0e665e3a825
            revapi/maven/org.revapi/revapi-java-spi 0.25.1 SHA-256/8fd32703b52df0e206ff55c4e3b97525cc4cd790693972e1746cdd4230d3a42a
            revapi/maven/org.revapi/revapi-reporter-file-base 0.5.1 SHA-256/eb8e10d0d8c9e3c9945cdb2315fbc351ee0bb884cb86e1498c10179ae4f00495
            revapi/maven/org.revapi/revapi-reporter-json 0.5.1 SHA-256/2b385bb37d92dd48ea0ecb85269194485dfb4fb5757afe278d8a43e6ce169b62
            revapi/maven/org.slf4j/slf4j-api 1.7.5 SHA-256/fe30825245d2336c859dc38d60c0fc5f3668dbf29cd586828d2b5667ec355b91
            revapi/maven/javax.inject/javax.inject 1 SHA-256/91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff
            """;

    @TempDir
    private Path root, project, sources, classes;

    @Test
    public void reports_the_types_a_jar_removed_from_its_baseline() throws IOException {
        build();

        newExecutor(module()).execute();

        Path report = root.resolve("revapi").resolve("compare").resolve("output")
                .resolve("reports").resolve("revapi").resolve("revapi.json");
        assertThat(report)
                .content()
                .contains("\"code\" : \"java.class.removed\"")
                .contains("interface javax.inject.Provider<T>");
    }

    @Test
    public void ignores_a_difference_that_revapi_s_own_configuration_ignores() throws IOException {
        build();

        newExecutor(module().analysis("""
                [{"extension": "revapi.differences", "configuration": {"ignore": true, "differences": [
                    {"code": "java.class.removed", "justification": "The demo removes them."}]}}]
                """)).execute();

        assertThat(root.resolve("revapi").resolve("compare").resolve("output")
                .resolve("reports").resolve("revapi").resolve("revapi.json"))
                .content()
                .doesNotContain("java.class.removed");
    }

    @Test
    public void fails_the_build_on_a_finding_when_strict() throws IOException {
        build();

        assertThatThrownBy(newExecutor(module().strict(true))::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revapi found")
                .hasMessageContaining("jenesis.artifact.revapi=strict");
    }

    private RevapiModule module() {
        return new RevapiModule(Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE)))
                .pinning(Pinning.STRICT)
                .baseline("javax.inject/javax.inject/1");
    }

    private void build() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Path source = Files.createDirectories(sources.resolve("javax").resolve("inject")).resolve("Named.java");
        Files.writeString(source, """
                package javax.inject;

                @Qualifier
                @java.lang.annotation.Documented
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                public @interface Named {
                    String value() default "";
                }
                """);
        Path qualifier = sources.resolve("javax").resolve("inject").resolve("Qualifier.java");
        Files.writeString(qualifier, """
                package javax.inject;

                @java.lang.annotation.Target(java.lang.annotation.ElementType.ANNOTATION_TYPE)
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                @java.lang.annotation.Documented
                public @interface Qualifier {
                }
                """);
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classes.toString(), "--release", "8", source.toString(), qualifier.toString());
        assertThat(rc).as("the sample compiles").isZero();
        Path artifacts = Files.createDirectories(project.resolve(BuildStep.ARTIFACTS));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(artifacts.resolve("javax.inject.jar")));
             Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }

    private BuildExecutor newExecutor(RevapiModule module) throws IOException {
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
        executor.addModule("revapi", module, "project");
        return executor;
    }
}
