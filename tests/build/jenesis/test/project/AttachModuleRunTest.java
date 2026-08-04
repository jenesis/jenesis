package build.jenesis.test.project;

import module java.base;
import module java.compiler;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.TestModule;
import build.jenesis.step.Javac;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AttachModuleRunTest {

    @TempDir
    private Path root, dependencies, classes, repository, work;

    @Test
    public void dual_use_agent_is_attached_and_on_the_class_path() throws Exception {
        Path marker = work.resolve("marker file.txt");
        writeProbe(true);
        writeInputs(marker, true);
        writeTest("""
                package attach;
                public class AttachedTest {
                    @org.junit.jupiter.api.Test
                    public void attached() throws Exception {
                        Class.forName("probe.Probe");
                        if (!java.nio.file.Files.exists(java.nio.file.Path.of("%s"))) {
                            throw new AssertionError("premain did not run");
                        }
                    }
                }
                """.formatted(marker.toString().replace('\\', '/')));

        newExecutor().execute();

        assertThat(marker).as("premain wrote its marker through the argument with a space").hasContent("attached");
        SequencedProperties index = SequencedProperties.ofFiles(root.resolve("test")
                .resolve("dependencies")
                .resolve("output")
                .resolve(BuildStep.DEPENDENCIES));
        String agent = index.getProperty("main/agent/probes/probe/probe/1");
        String runtime = index.getProperty("main/runtime/probes/probe/probe/1");
        assertThat(agent).isNotNull();
        assertThat(runtime).isNotNull();
        String agentPath = agent.split(" ")[0];
        assertThat(agentPath)
                .as("agent and runtime scope resolve to the identical artifact")
                .isEqualTo(runtime.split(" ")[0]);
        String command = Files.readString(root.resolve("test").resolve("executed").resolve("supplement").resolve("command"));
        assertThat(command).contains("-javaagent:");
        assertThat(occurrences(command, agentPath.substring(agentPath.lastIndexOf('/') + 1)))
                .as("the probe jar is attached once and on the class path once")
                .isEqualTo(2);
    }

    @Test
    public void agent_only_attachment_stays_off_the_class_path() throws Exception {
        Path marker = work.resolve("marker file.txt");
        writeProbe(true);
        writeInputs(marker, false);
        writeTest("""
                package attach;
                public class AttachedTest {
                    @org.junit.jupiter.api.Test
                    public void attached() {
                        if (!java.nio.file.Files.exists(java.nio.file.Path.of("%s"))) {
                            throw new AssertionError("premain did not run");
                        }
                    }
                }
                """.formatted(marker.toString().replace('\\', '/')));

        newExecutor().execute();

        assertThat(marker).hasContent("attached");
        SequencedProperties index = SequencedProperties.ofFiles(root.resolve("test")
                .resolve("dependencies")
                .resolve("output")
                .resolve(BuildStep.DEPENDENCIES));
        String agent = index.getProperty("main/agent/probes/probe/probe/1");
        assertThat(agent).isNotNull();
        String agentPath = agent.split(" ")[0];
        String command = Files.readString(root.resolve("test").resolve("executed").resolve("supplement").resolve("command"));
        assertThat(command).contains("-javaagent:");
        assertThat(occurrences(command, agentPath.substring(agentPath.lastIndexOf('/') + 1)))
                .as("the probe jar is attached but joins no class or module path")
                .isEqualTo(1);
    }

    @Test
    public void missing_agent_resolution_fails_naming_the_coordinate() throws Exception {
        Path marker = work.resolve("marker.txt");
        writeProbe(true);
        writeInputs(marker, false);
        SequencedProperties requires = new SequencedProperties();
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        writeTest("""
                package attach;
                public class AttachedTest {
                    @org.junit.jupiter.api.Test
                    public void attached() {
                    }
                }
                """);

        assertThatThrownBy(newExecutor()::execute)
                .hasStackTraceContaining("No resolved artifact for attached agent main/agent/probes/probe/probe");
    }

    @Test
    public void agent_without_premain_class_fails() throws Exception {
        Path marker = work.resolve("marker.txt");
        writeProbe(false);
        writeInputs(marker, false);
        writeTest("""
                package attach;
                public class AttachedTest {
                    @org.junit.jupiter.api.Test
                    public void attached() {
                    }
                }
                """);

        assertThatThrownBy(newExecutor()::execute)
                .hasStackTraceContaining("Attached agent main/agent/probes/probe/probe does not declare Premain-Class");
    }

    private BuildExecutor newExecutor() throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false);
        executor.addSource("dependencies", dependencies);
        executor.addSource("classes", classes);
        Map<String, Repository> repositories = Map.of(
                "maven", MavenDefaultRepository.of(),
                "probes", new MavenDefaultRepository(repository.toUri(), null, Map.of(), _ -> {}));
        Map<String, Resolver> resolvers = Map.of(
                "maven", new MavenPomResolver(),
                "probes", new MavenPomResolver());
        executor.addModule(
                "test",
                new TestModule(repositories, resolvers)
                        .isTest(candidate -> candidate.endsWith("AttachedTest"))
                        .jarsOnly(false),
                "dependencies", "classes");
        return executor;
    }

    private void writeInputs(Path marker, boolean runtime) throws IOException {
        Path artifacts = Files.createDirectory(dependencies.resolve(BuildStep.ARTIFACTS));
        for (Path path : bootModuleJars()) {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith("_rt.jar") || fileName.endsWith("-rt.jar")) {
                continue;
            }
            Files.copy(path, artifacts.resolve(fileName + "-" + UUID.randomUUID() + ".jar"));
        }
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/agent/probes/probe/probe/1", "");
        if (runtime) {
            requires.setProperty("main/runtime/probes/probe/probe/1", "");
        }
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties attachments = new SequencedProperties();
        attachments.setProperty("main/agent/probes/probe/probe", marker.toString());
        attachments.store(dependencies.resolve(BuildStep.ATTACHMENTS));
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.junit.platform/junit-platform-console",
                "1.11.4 SHA-256/a9c3309cdfded3542200de85da6cb274864439d6b02ba80bb45ecc8e0bdf1be7");
        versions.setProperty("main/maven/org.junit.platform/junit-platform-reporting",
                "1.11.4 SHA-256/df6896109bfaef4de8d2fa9e3371a6176936d1a45a6c0e7fd8f7e6dd6f4c5597");
        versions.setProperty("main/maven/org.junit.platform/junit-platform-launcher",
                "1.11.4 SHA-256/d7430bd029e7fcced53ee445e4d2d1a8a1e043ea4c4df43b6335a857f79761ae");
        versions.setProperty("main/maven/org.junit.platform/junit-platform-engine",
                "1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da");
        versions.setProperty("main/maven/org.junit.platform/junit-platform-commons",
                "1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c");
        versions.setProperty("main/maven/org.opentest4j/opentest4j",
                "1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b");
        versions.setProperty("main/maven/org.apiguardian/apiguardian-api",
                "1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38");
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
    }

    private void writeProbe(boolean premain) throws IOException {
        Path probeClasses = Files.createDirectory(work.resolve("probe-classes"));
        compile(probeClasses, List.of(), "probe", Map.of("Probe", """
                package probe;
                import java.lang.instrument.Instrumentation;
                public class Probe {
                    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(arguments), "attached");
                    }
                }
                """));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        if (premain) {
            manifest.getMainAttributes().putValue("Premain-Class", "probe.Probe");
        }
        Path folder = Files.createDirectories(repository.resolve("probe/probe/1"));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(folder.resolve("probe-1.jar")), manifest)) {
            jar.putNextEntry(new JarEntry("probe/Probe.class"));
            jar.write(Files.readAllBytes(probeClasses.resolve("probe").resolve("Probe.class")));
            jar.closeEntry();
        }
        Files.writeString(folder.resolve("probe-1.pom"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>probe</groupId>
                    <artifactId>probe</artifactId>
                    <version>1</version>
                </project>
                """);
    }

    private void writeTest(String source) throws IOException {
        compile(classes.resolve(Javac.CLASSES), bootModuleJars(), "attach", Map.of("AttachedTest", source));
    }

    private static int occurrences(String text, String token) {
        int count = 0, index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static void compile(Path outputRoot, List<Path> classpath, String packageName, Map<String, String> units)
            throws IOException {
        Files.createDirectories(outputRoot);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputRoot));
            fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
            List<JavaFileObject> sources = new ArrayList<>();
            for (Map.Entry<String, String> unit : units.entrySet()) {
                String body = unit.getValue();
                sources.add(new SimpleJavaFileObject(
                        URI.create("string:///" + packageName + "/" + unit.getKey() + ".java"),
                        JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return body;
                    }
                });
            }
            if (!compiler.getTask(null, fileManager, null, null, null, sources).call()) {
                throw new IllegalStateException("Failed to compile sources");
            }
        }
    }

    private static List<Path> bootModuleJars() throws IOException {
        Set<Path> jars = new LinkedHashSet<>();
        for (ResolvedModule resolved : ModuleLayer.boot().configuration().modules()) {
            String name = resolved.name();
            if (name.startsWith("java.") || name.startsWith("jdk.")) {
                continue;
            }
            URI location = resolved.reference().location().orElse(null);
            if (location == null) {
                continue;
            }
            Path path = Path.of(location);
            if (Files.isRegularFile(path)) {
                jars.add(path);
            }
        }
        Enumeration<URL> manifests = ClassLoader.getSystemClassLoader().getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements()) {
            String url = manifests.nextElement().toString();
            if (!url.startsWith("jar:file:")) {
                continue;
            }
            int bang = url.indexOf("!/");
            if (bang < 0) {
                continue;
            }
            Path path = Path.of(URI.create(url.substring("jar:".length(), bang)));
            if (Files.isRegularFile(path)) {
                jars.add(path);
            }
        }
        return new ArrayList<>(jars);
    }
}
