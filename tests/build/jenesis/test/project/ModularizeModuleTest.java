package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.ModularizeModule;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ModularizeModuleTest {

    private static final String NAMED = "main/compile/maven/demo/named/1.0",
            AUTOMATIC = "main/compile/maven/demo/automatic/1.0",
            PLAIN = "main/compile/maven/demo/plain/1.0";

    @TempDir
    private Path root;
    private Path closure, resolved;

    @BeforeEach
    public void setUp() throws Exception {
        closure = Files.createDirectory(root.resolve("closure"));
        resolved = Files.createDirectory(closure.resolve(Dependencies.RESOLVED));
    }

    @Test
    public void reads_its_mode_from_a_properties_file() throws IOException {
        assertThat(ModularizeModule.configured(null)).isNull();
        assertThat(ModularizeModule.configured(mode(""))).isFalse();
        assertThat(ModularizeModule.configured(mode("mode=declared\n"))).isFalse();
        assertThat(ModularizeModule.configured(mode("mode=synthetic\n"))).isTrue();
        assertThat(ModularizeModule.configured(mode("mode=none\n"))).isNull();
        assertThatThrownBy(() -> ModularizeModule.configured(mode("mode=other\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other");
    }

    @Test
    public void copies_a_named_module_byte_for_byte() throws IOException {
        Path source = named();
        automatic();
        Path modularized = modularize(false);
        Path target = modularized.resolve(Dependencies.RESOLVED + "demo.named.jar");
        assertThat(target).hasBinaryContent(Files.readAllBytes(source));
        SequencedProperties index = SequencedProperties.ofFiles(modularized.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.getProperty(NAMED))
                .isEqualTo(Dependencies.RESOLVED + "demo.named.jar SHA-256/named");
    }

    @Test
    public void injects_a_descriptor_into_a_candidate() throws IOException {
        named();
        automatic();
        Path modularized = modularize(false);
        ModuleDescriptor descriptor = describe(modularized.resolve(Dependencies.RESOLVED + "demo.automatic.jar"));
        assertThat(descriptor.name()).isEqualTo("demo.automatic");
        assertThat(descriptor.isOpen()).isTrue();
        assertThat(descriptor.exports().stream().map(ModuleDescriptor.Exports::source))
                .containsExactlyInAnyOrder("demo.automatic");
        assertThat(descriptor.packages()).containsExactlyInAnyOrder("demo.automatic");
        assertThat(descriptor.requires().stream().map(ModuleDescriptor.Requires::name))
                .contains("java.base", "demo.named", "java.xml");
        assertThat(descriptor.uses()).containsExactly("demo.named.Service");
        assertThat(descriptor.provides()).singleElement().satisfies(provides -> {
            assertThat(provides.service()).isEqualTo("demo.named.Service");
            assertThat(provides.providers()).containsExactly("demo.automatic.Provider");
        });
        SequencedProperties index = SequencedProperties.ofFiles(modularized.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.getProperty(AUTOMATIC)).isEqualTo(Dependencies.RESOLVED + "demo.automatic.jar");
    }

    @Test
    public void rejects_an_undeclared_module_name() throws IOException {
        named();
        automatic();
        plain();
        assertThatThrownBy(() -> modularize(false))
                .hasStackTraceContaining(PLAIN)
                .hasStackTraceContaining(ModularizeModule.PSEUDO);
    }

    @Test
    public void derives_a_stable_pseudonym_from_the_jar() throws IOException {
        named();
        automatic();
        Path source = plain();
        String digest = HexFormat.of().formatHex(new HashDigestFunction("SHA-256").hash(source));
        Path modularized = modularize(true);
        SequencedProperties index = SequencedProperties.ofFiles(modularized.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.getProperty(PLAIN)).isEqualTo(
                Dependencies.RESOLVED + ModularizeModule.PSEUDO + digest.substring(0, 32) + ".jar");
        ModuleDescriptor descriptor = describe(modularized.resolve(index.getProperty(PLAIN)));
        assertThat(descriptor.name()).isEqualTo(ModularizeModule.PSEUDO + digest.substring(0, 32));
        assertThat(descriptor.requires().stream().map(ModuleDescriptor.Requires::name)).contains("demo.automatic");
        assertThat(descriptor.exports().stream().map(ModuleDescriptor.Exports::source)).containsExactly("demo.plain");
    }

    @Test
    public void strips_a_signature_from_a_rewritten_jar() throws IOException {
        named();
        Path source = automatic();
        try (FileSystem jar = FileSystems.newFileSystem(source)) {
            Files.writeString(jar.getPath("/META-INF/DEMO.SF"), "Signature-Version: 1.0\n");
            Files.writeString(jar.getPath("/META-INF/DEMO.RSA"), "binary");
            Path manifest = jar.getPath("/META-INF/MANIFEST.MF");
            Files.writeString(manifest, Files.readString(manifest)
                    + "\nName: demo/automatic/Provider.class\nSHA-256-Digest: aGVsbG8=\n\n");
        }
        Path modularized = modularize(false);
        try (JarFile file = new JarFile(
                modularized.resolve(Dependencies.RESOLVED + "demo.automatic.jar").toFile(), false)) {
            assertThat(file.stream().map(JarEntry::getName)).doesNotContain("META-INF/DEMO.SF", "META-INF/DEMO.RSA");
            assertThat(file.getManifest().getEntries()).isEmpty();
            assertThat(file.getManifest().getMainAttributes().getValue("Automatic-Module-Name"))
                    .isEqualTo("demo.automatic");
        }
    }

    @Test
    public void links_a_runtime_image_from_modularized_jars() throws IOException {
        named();
        automatic();
        plain();
        Path modularized = modularize(true);
        SequencedProperties index = SequencedProperties.ofFiles(modularized.resolve(BuildStep.DEPENDENCIES));
        Path image = root.resolve("image");
        int linked = ToolProvider.findFirst("jlink").orElseThrow().run(System.out, System.err,
                "--module-path", modularized.resolve(Dependencies.RESOLVED).toString(),
                "--add-modules", describe(modularized.resolve(index.getProperty(PLAIN))).name(),
                "--output", image.toString());
        assertThat(linked).isZero();
        assertThat(image.resolve("release")).isRegularFile();
    }

    private Path modularize(boolean synthetic) throws IOException {
        BuildExecutor buildExecutor = BuildExecutor.of(Files.createDirectory(root.resolve("build")),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false);
        buildExecutor.addSource("closure", closure);
        buildExecutor.addModule("modules",
                new ModularizeModule(ProcessHandler.Factory.TOOL, synthetic),
                "closure");
        SequencedMap<String, Path> steps = buildExecutor.execute();
        return steps.get("modules");
    }

    private Path mode(String content) throws IOException {
        Path properties = Files.createTempFile(root, "modules", ".properties");
        Files.writeString(properties, content);
        return properties;
    }

    private static ModuleDescriptor describe(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile(), false)) {
            try (InputStream input = file.getInputStream(file.getJarEntry("module-info.class"))) {
                return ModuleDescriptor.read(input);
            }
        }
    }

    private Path named() throws IOException {
        Path sources = Files.createDirectories(root.resolve("named/demo/named"));
        Files.writeString(root.resolve("named/module-info.java"),
                "module demo.named { exports demo.named; }\n");
        Files.writeString(sources.resolve("Service.java"), "package demo.named; public interface Service { }\n");
        return archive(compile("named", null), "demo/named/1.0", NAMED, "SHA-256/named", null);
    }

    private Path automatic() throws IOException {
        Path sources = Files.createDirectories(root.resolve("automatic/demo/automatic"));
        Files.writeString(sources.resolve("Provider.java"), """
                package demo.automatic;
                import java.util.ServiceLoader;
                public class Provider implements demo.named.Service {
                    public org.w3c.dom.Document document;
                    public static void discover() { ServiceLoader.load(demo.named.Service.class); }
                }
                """);
        Path classes = compile("automatic", root.resolve("named-classes").toString());
        Files.writeString(Files.createDirectories(classes.resolve("META-INF/services"))
                .resolve("demo.named.Service"), "demo.automatic.Provider\n");
        Path manifest = root.resolve("automatic.mf");
        Files.writeString(manifest, "Automatic-Module-Name: demo.automatic\n");
        return archive(classes, "demo/automatic/1.0", AUTOMATIC, "", manifest);
    }

    private Path plain() throws IOException {
        Path sources = Files.createDirectories(root.resolve("plain/demo/plain"));
        Files.writeString(sources.resolve("Use.java"),
                "package demo.plain; public class Use { public demo.automatic.Provider provider; }\n");
        Path classes = compile("plain", root.resolve("automatic-classes")
                + File.pathSeparator
                + root.resolve("named-classes"));
        return archive(classes, "demo/plain/1.0", PLAIN, "", null);
    }

    private Path compile(String name, String classPath) throws IOException {
        Path classes = Files.createDirectory(root.resolve(name + "-classes"));
        List<String> commands = new ArrayList<>(List.of("-d", classes.toString()));
        if (classPath != null) {
            commands.add("--class-path");
            commands.add(classPath);
        }
        try (Stream<Path> walked = Files.walk(root.resolve(name))) {
            walked.filter(file -> file.toString().endsWith(".java")).map(Path::toString).forEach(commands::add);
        }
        assertThat(ToolProvider.findFirst("javac")
                .orElseThrow()
                .run(System.out, System.err, commands.toArray(String[]::new))).isZero();
        return classes;
    }

    private Path archive(Path classes, String coordinate, String key, String checksum, Path manifest)
            throws IOException {
        Path jar = resolved.resolve(coordinate.replace('/', '%') + ".jar");
        List<String> commands = new ArrayList<>(List.of("--create", "--file", jar.toString()));
        if (manifest != null) {
            commands.add("--manifest");
            commands.add(manifest.toString());
        }
        commands.addAll(List.of("-C", classes.toString(), "."));
        assertThat(ToolProvider.findFirst("jar")
                .orElseThrow()
                .run(System.out, System.err, commands.toArray(String[]::new))).isZero();
        Path index = closure.resolve(BuildStep.DEPENDENCIES);
        SequencedProperties properties = Files.exists(index)
                ? SequencedProperties.ofFiles(index)
                : new SequencedProperties();
        String value = Dependencies.RESOLVED + jar.getFileName();
        properties.setProperty(key, checksum.isEmpty() ? value : value + " " + checksum);
        properties.store(index);
        return jar;
    }
}
