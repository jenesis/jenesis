package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import java.util.jar.Attributes;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.DependencyScope;
import build.jenesis.HashDigestFunction;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;

public class DependenciesReuseTest {

    private static final AtomicInteger RESOLUTIONS = new AtomicInteger();

    @TempDir
    private Path root;
    private Path dependencies, artifacts, produced;
    private final AtomicBoolean internal = new AtomicBoolean(true);
    private final SequencedSet<String> ran = new LinkedHashSet<>();

    @BeforeEach
    public void setUp() throws IOException {
        RESOLUTIONS.set(0);
        dependencies = Files.createDirectory(root.resolve("dependencies"));
        artifacts = Files.createDirectory(root.resolve("artifacts"));
        produced = artifacts.resolve("produced.jar");
        produce("first", "sample.produced");
        Files.writeString(artifacts.resolve("library.jar"), "library");
    }

    private void produce(String body, String module) throws IOException {
        produce(body, module, "Apache-2.0");
    }

    private void produce(String body, String module, String license) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", module);
        manifest.getMainAttributes().putValue("Sbom-Location", "META-INF/sbom/produced.cdx.json");
        Path temporary = artifacts.resolve("produced.tmp");
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(temporary), manifest)) {
            outputStream.putNextEntry(new JarEntry("sample/Produced.class"));
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
            outputStream.putNextEntry(new JarEntry("META-INF/sbom/produced.cdx.json"));
            outputStream.write("""
                    {"metadata": {"component": {"licenses": [{"license": {"id": "%s"}}]}},
                     "components": [{"name": "sibling", "hashes": [{"content": "%s"}]}]}
                    """.formatted(license, body).getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
        Files.move(temporary, produced, StandardCopyOption.REPLACE_EXISTING);
        require("");
    }

    private void require(String extra) throws IOException {
        Files.writeString(dependencies.resolve(BuildStep.REQUIRES),
                "main/compile/foo/produced=" + checksum(produced) + "\n"
                        + "main/compile/foo/library=\n"
                        + extra);
    }

    private static String checksum(Path file) throws IOException {
        try {
            return "MD5/" + HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Dependencies module() {
        Repository repository = (_, coordinate, _) -> coordinate.equals("produced")
                ? Optional.of(RepositoryItem.ofFile(produced, internal.get()))
                : Optional.of(RepositoryItem.ofFile(artifacts.resolve(coordinate + ".jar")));
        return new Dependencies(Map.of("foo", repository), Map.of("foo", new CountingResolver()));
    }

    private Path execute(Dependencies module, String build, Path upstream) throws IOException {
        ran.clear();
        BuildExecutor executor = BuildExecutor.of(Files.createDirectories(root.resolve(build)),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                (identity, _) -> (step, _) -> {
                    if (identity != null && Boolean.TRUE.equals(step)) {
                        ran.add(identity);
                    }
                },
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("dependencies", dependencies);
        if (upstream == null) {
            executor.addModule("resolved", module, "dependencies");
        } else {
            executor.addSource("upstream", upstream);
            executor.addModule("resolved", module, "dependencies", "upstream");
        }
        return executor.execute().get("resolved");
    }

    private Path execute(Dependencies module) throws IOException {
        return execute(module, "build", null);
    }

    private static SequencedMap<String, String> contents(Path folder) throws IOException {
        SequencedMap<String, String> contents = new TreeMap<>();
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                contents.put(folder.relativize(file).toString(),
                        new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
            }
        }
        return contents;
    }

    @Test
    public void hands_on_the_new_bytes_of_an_internal_artifact_without_resolving_again() throws IOException {
        execute(module());
        produce("second", "sample.produced");
        Path output = execute(module());

        assertThat(RESOLUTIONS.get())
                .as("only classes and a bill of materials' hashes changed, which no resolution reads")
                .isEqualTo(1);
        assertThat(ran).contains("resolved/resolve").doesNotContain("resolved/pinned");
        String entry = SequencedProperties.ofFiles(output.resolve(BuildStep.DEPENDENCIES))
                .getProperty("main/compile/foo/produced");
        assertThat(entry)
                .as("an artifact the build produces is recorded without its checksum, so the index stays when it changes")
                .doesNotContain(" ");
        assertThat(output.resolve(entry))
                .as("what runs against the resolution sees the artifact as the build produced it last")
                .hasSameBinaryContentAs(produced);
    }

    @Test
    public void a_reused_resolution_writes_what_a_full_resolution_writes() throws IOException {
        execute(module());
        produce("second", "sample.produced");
        Path reused = execute(module());
        Path resolved = execute(module(), "fresh", null);

        assertThat(RESOLUTIONS.get()).isEqualTo(2);
        assertThat(contents(reused)).isEqualTo(contents(resolved));
    }

    @Test
    public void resolves_again_when_the_module_an_internal_artifact_declares_changed() throws IOException {
        execute(module());
        produce("second", "sample.renamed");
        Path output = execute(module());

        assertThat(RESOLUTIONS.get())
                .as("a module name is read by the resolution, so it is part of what decides a reuse")
                .isEqualTo(2);
        String entry = SequencedProperties.ofFiles(output.resolve(BuildStep.DEPENDENCIES))
                .getProperty("main/compile/foo/produced");
        assertThat(output.resolve(entry)).hasSameBinaryContentAs(produced);
    }

    @Test
    public void resolves_again_when_the_license_an_internal_artifact_declares_changed() throws IOException {
        execute(module());
        produce("second", "sample.produced", "MIT");
        execute(module());

        assertThat(RESOLUTIONS.get())
                .as("the license a bill of materials in the artifact declares is read by the resolution")
                .isEqualTo(2);
    }

    @Test
    public void resolves_again_when_an_artifact_is_no_longer_produced_by_the_build() throws IOException {
        execute(module());
        internal.set(false);
        produce("second", "sample.produced");
        execute(module());

        assertThat(RESOLUTIONS.get()).isEqualTo(2);
    }

    @Test
    public void resolves_again_when_the_pinning_changed() throws IOException {
        execute(module());
        produce("second", "sample.produced");
        execute(module().pinning(Pinning.VERSIONS));

        assertThat(RESOLUTIONS.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            BuildStep.REQUIRES + ", main/compile/foo/other=",
            BuildStep.VERSIONS + ", main/foo/library=1.0",
            BuildStep.BOMS + ", entry/main/foo/library=1.0",
            BuildStep.EXCLUSIONS + ", main/compile/foo/library=other",
            BuildStep.MANAGED + ", main/foo/library=other",
            BuildStep.ALIASES + ", main/foo/sample.other=foo/library"
    })
    public void resolves_again_when_an_input_other_than_an_internal_artifact_changed(String file, String line)
            throws IOException {
        Files.writeString(artifacts.resolve("other.jar"), "other");
        execute(module());
        produce("second", "sample.produced");
        if (file.equals(BuildStep.REQUIRES)) {
            require(line + "\n");
        } else {
            Files.writeString(dependencies.resolve(file), line + "\n");
        }
        Path output = execute(module());

        assertThat(RESOLUTIONS.get())
                .as("a change to %s is a change to what is resolved", file)
                .isGreaterThan(1);
        String entry = SequencedProperties.ofFiles(output.resolve(BuildStep.DEPENDENCIES))
                .getProperty("main/compile/foo/produced");
        assertThat(output.resolve(entry)).hasSameBinaryContentAs(produced);
    }

    @Test
    public void reuses_the_resolution_when_only_an_upstream_index_changed() throws IOException {
        Path upstream = Files.createDirectory(root.resolve("upstream"));
        Files.writeString(upstream.resolve(BuildStep.DEPENDENCIES), "main/compile/foo/other/1.0=resolved/other.jar\n");
        execute(module(), "build", upstream);
        Files.writeString(upstream.resolve(BuildStep.DEPENDENCIES), "main/compile/foo/other/2.0=resolved/other.jar\n");
        execute(module(), "build", upstream);

        assertThat(ran).contains("resolved/resolve");
        assertThat(RESOLUTIONS.get())
                .as("an upstream index is no input of a resolution, which reads its own declarations alone")
                .isEqualTo(1);
    }

    private record CountingResolver() implements Resolver {

        @Override
        public Resolver.Resolution dependencies(Executor executor,
                                                String prefix,
                                                Map<String, Repository> repositories,
                                                SequencedMap<String, SequencedSet<String>> coordinates,
                                                SequencedMap<String, String> versions,
                                                SequencedMap<String, SequencedSet<String>> managedExclusions,
                                                DependencyScope scope) throws IOException {
            RESOLUTIONS.incrementAndGet();
            return Resolver.identity().dependencies(executor, prefix, repositories, coordinates, versions, managedExclusions, scope);
        }
    }
}
