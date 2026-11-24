package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.HashDigestFunction;
import build.jenesis.Platform;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.PinPom;
import build.jenesis.step.Inventory;

import static org.assertj.core.api.Assertions.assertThat;

public class PinPomTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, persistent, input;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        persistent = Files.createDirectory(root.resolve("persistent"));
        input = Files.createDirectory(root.resolve("input"));
    }

    private SequencedProperties loadInventory() throws IOException {
        Path file = input.resolve(Inventory.INVENTORY);
        return Files.isRegularFile(file) ? SequencedProperties.ofFiles(file) : new SequencedProperties();
    }

    private static int count(SequencedProperties properties, String prefix) {
        return (int) properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith(prefix))
                .filter(name -> name.indexOf('.', prefix.length()) < 0)
                .count();
    }

    private void writeResolved(Map<String, String> entries) throws IOException {
        writeResolved("compile", entries);
    }

    private void writeResolved(String scope, Map<String, String> entries) throws IOException {
        String group = scope.equals("compile") || scope.equals("runtime") ? "main" : scope;
        SequencedProperties properties = loadInventory();
        int index = count(properties, "module.dependency.");
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String value = entry.getValue();
            int space = value.indexOf(' ');
            String version = space < 0 ? value : value.substring(0, space);
            String checksum = space < 0 ? "" : value.substring(space + 1).trim();
            String coordinate = entry.getKey() + "/" + version;
            String jar = "resolved/" + coordinate.replace('/', '-') + ".jar";
            properties.setProperty("module.dependency." + index,
                    coordinate + " " + jar + (checksum.isEmpty() ? "" : " " + checksum));
            properties.setProperty("module.dependency." + index + ".scope", scope);
            properties.setProperty("module.dependency." + index + ".group", group);
            index++;
        }
        properties.store(input.resolve(Inventory.INVENTORY));
    }

    private void writeIdentity(Map<String, String> entries) throws IOException {
        SequencedProperties properties = loadInventory();
        int index = count(properties, "module.identity.");
        for (String coordinate : entries.keySet()) {
            properties.setProperty("module.identity." + index++, coordinate);
        }
        properties.store(input.resolve(Inventory.INVENTORY));
    }

    private static String sha256(String content) {
        try {
            return "SHA-256/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private String run(Path pomFile) throws IOException {
        return run(pomFile, Platform.of("linux,x86_64"));
    }

    private String run(Path pomFile, Platform platform) throws IOException {
        new PinPom("", List.of(pomFile), new HashDigestFunction("SHA-256"))
                .platform(platform)
                .apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement, persistent),
                        new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                                input,
                                Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        return Files.readString(pomFile);
    }

    @Test
    public void pins_past_a_predecessor_that_vanished_since_the_previous_run() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    org.foo/bar 2.0.0 SHA-256/stale
                    -->
                </project>
                """);
        writeResolved(Map.of("maven/org.foo/bar", "3.0.0 SHA-256/fresh"));
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        arguments.put("input", new BuildStepArgument(
                input,
                Map.of(Path.of(Inventory.INVENTORY), Checksum.of(ChecksumStatus.ADDED))));
        arguments.put("vanished", new BuildStepArgument(null, Checksum.removed(Set.of(Path.of(Inventory.INVENTORY)))));
        new PinPom("", List.of(pom), new HashDigestFunction("SHA-256"))
                .platform(Platform.of("linux,x86_64"))
                .apply(Runnable::run, new BuildStepContext(previous, next, supplement, persistent), arguments)
                .toCompletableFuture()
                .join();
        assertThat(Files.readString(pom))
                .as("a predecessor that no longer exists is skipped rather than read")
                .contains("org.foo/bar 3.0.0 SHA-256/fresh")
                .doesNotContain("SHA-256/stale");
    }

    @Test
    public void pin_rewrite_preserves_attach_comments() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.mockito/mockito-core
                    io.opentelemetry.javaagent/opentelemetry-javaagent otel.option=value
                    -->
                </project>
                """);
        writeResolved(Map.of("maven/org.mockito/mockito-core", "5.11.0 SHA-256/cafebabe"));
        String result = run(pom);
        assertThat(result).contains("""
                <!--jenesis.attach
                    org.mockito/mockito-core
                    io.opentelemetry.javaagent/opentelemetry-javaagent otel.option=value
                    -->""");
    }

    @Test
    public void updates_matched_guarded_comment_line_and_preserves_others() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    kotlin/maven/org.jetbrains/something 1.0.0 SHA-256/aaa (windows)
                    kotlin/maven/org.jetbrains/something 1.0.0 SHA-256/bbb
                    plugin/maven/org.example/other 0.9
                    -->
                </project>
                """);
        writeResolved("kotlin", Map.of("maven/org.jetbrains/something", "1.2.3 SHA-256/fresh"));
        writeResolved("plugin", Map.of("maven/org.example/other", "4.5.6 SHA-256/cafebabe"));
        String result = run(pom, Platform.of("windows,x86_64"));
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.2.3 SHA-256/fresh (windows)");
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.0.0 SHA-256/bbb");
        assertThat(result).doesNotContain("SHA-256/aaa");
        assertThat(result).contains("plugin/maven/org.example/other 4.5.6 SHA-256/cafebabe");
        assertThat(result).doesNotContain("org.example/other 0.9");
    }

    @Test
    public void refreshes_short_form_guarded_main_pin_without_duplicating() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    org.foo/bar 1.0.0 SHA-256/aaa (legacy)
                    org.foo/bar 2.0.0 SHA-256/bbb
                    -->
                </project>
                """);
        writeResolved(Map.of("maven/org.foo/bar", "3.0.0 SHA-256/fresh"));
        String result = run(pom, Platform.of("linux,x86_64,legacy"));
        assertThat(result).contains("org.foo/bar 3.0.0 SHA-256/fresh (legacy)");
        assertThat(result).contains("org.foo/bar 2.0.0 SHA-256/bbb");
        assertThat(result).doesNotContain("SHA-256/aaa");
        assertThat(result).as("the guarded short-form main pin is not migrated into dependencyManagement")
                .doesNotContain("<dependencyManagement>");
        assertThat(result).as("the short form is not duplicated as a full main/maven line")
                .doesNotContain("main/maven/org.foo/bar");
    }

    @Test
    public void updates_only_the_matched_guard_among_several_and_retains_the_rest() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    kotlin/maven/org.x/y 1.0 SHA-256/win (windows)
                    kotlin/maven/org.x/y 2.0 SHA-256/leg (legacy)
                    kotlin/maven/org.x/y 3.0 SHA-256/fb
                    -->
                </project>
                """);
        writeResolved("kotlin", Map.of("maven/org.x/y", "9.9 SHA-256/fresh"));
        String result = run(pom, Platform.of("linux,x86_64,legacy"));
        assertThat(result).as("the matched (legacy) line is refreshed")
                .contains("kotlin/maven/org.x/y 9.9 SHA-256/fresh (legacy)");
        assertThat(result).as("the non-matched (windows) line is retained verbatim")
                .contains("kotlin/maven/org.x/y 1.0 SHA-256/win (windows)");
        assertThat(result).as("the fallback line is retained verbatim")
                .contains("kotlin/maven/org.x/y 3.0 SHA-256/fb");
        assertThat(result).as("the stale (legacy) value is gone").doesNotContain("SHA-256/leg");
    }

    @Test
    public void updates_matched_fallback_comment_line_and_preserves_guard() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    kotlin/maven/org.jetbrains/something 1.0.0 SHA-256/aaa (windows)
                    kotlin/maven/org.jetbrains/something 1.0.0 SHA-256/bbb
                    -->
                </project>
                """);
        writeResolved("kotlin", Map.of("maven/org.jetbrains/something", "1.2.3 SHA-256/fresh"));
        String result = run(pom, Platform.of("linux,x86_64"));
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.0.0 SHA-256/aaa (windows)");
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.2.3 SHA-256/fresh");
        assertThat(result).doesNotContain("SHA-256/bbb");
    }

    @Test
    public void guarded_main_pin_stays_in_block_and_out_of_dependency_management() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    main/maven/io.smallrye.reactive/mutiny-zero 1.0.0 SHA-256/aaa (windows)
                    main/maven/io.smallrye.reactive/mutiny-zero 0.9.0 SHA-256/bbb
                    -->
                </project>
                """);
        writeResolved(Map.of("maven/io.smallrye.reactive/mutiny-zero", "1.1.1 SHA-256/fresh"));
        String result = run(pom, Platform.of("linux,x86_64"));
        assertThat(result).contains("main/maven/io.smallrye.reactive/mutiny-zero 1.0.0 SHA-256/aaa (windows)");
        assertThat(result).contains("main/maven/io.smallrye.reactive/mutiny-zero 1.1.1 SHA-256/fresh");
        assertThat(result).doesNotContain("SHA-256/bbb");
        assertThat(result).doesNotContain("<dependencyManagement>");
    }

    @Test
    public void preserves_guarded_comment_key_without_local_resolution() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    kotlin/maven/org.jetbrains/something 1.0.0 (windows)
                    -->
                </project>
                """);
        writeResolved("plugin", Map.of("maven/org.example/other", "4.5.6"));
        String result = run(pom, Platform.of("linux,x86_64"));
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.0.0 (windows)");
        assertThat(result).contains("plugin/maven/org.example/other 4.5.6");
    }

    @Test
    public void writes_qualified_dependencies_to_a_comment_block() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        writeResolved("kotlin", Map.of("maven/org.jetbrains/something", "1.2.3"));
        writeResolved("plugin", Map.of("maven/org.example/other", "4.5.6"));
        String result = run(pom);
        assertThat(result).contains("<!--jenesis.pin");
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.2.3");
        assertThat(result).contains("plugin/maven/org.example/other 4.5.6");
        assertThat(result).doesNotContain("<dependencyManagement>");
    }

    @Test
    public void merges_every_pin_comment_into_one_block_so_none_shadows_it() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin javac/maven/a/b 1-->
                    <!--jenesis.pin javac/maven/c/d 2 (windows)-->
                    <!--jenesis.pin javac/maven/e/f 3-->
                </project>
                """);
        writeResolved("javac", Map.of("maven/a/b", "1 SHA-256/ab", "maven/e/f", "3 SHA-256/ef"));
        String result = run(pom);
        assertThat(result.split("<!--jenesis.pin", -1))
                .as("a later pin comment would win over the block pin writes")
                .hasSize(2);
        assertThat(result)
                .contains("javac/maven/a/b 1 SHA-256/ab")
                .contains("javac/maven/c/d 2 (windows)")
                .contains("javac/maven/e/f 3 SHA-256/ef")
                .doesNotContain("javac/maven/e/f 3-->");
    }

    @Test
    public void encodes_double_hyphen_in_a_comment_block() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        writeResolved("kotlin", Map.of("maven/org.jetbrains/something", "1.2--3"));
        String result = run(pom);
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.2&#45;&#45;3");
        int blockStart = result.indexOf("<!--jenesis.pin");
        int blockEnd = result.indexOf("-->", blockStart);
        assertThat(result.substring(blockStart + "<!--".length(), blockEnd)).doesNotContain("--");
    }

    @Test
    public void keeps_an_imported_bom_when_nothing_is_left_to_pin() throws IOException {
        Path pom = root.resolve("pom.xml");
        String content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>bom</artifactId>
                                <version>1.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """;
        Files.writeString(pom, content);
        writeResolved(Map.of());
        assertThat(run(pom)).isEqualTo(content);
    }

    @Test
    public void second_run_with_same_input_is_a_noop() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        writeResolved(Map.of("maven/org.example/dep", "1.0 SHA-256/cafebabe"));
        String afterFirst = run(pom);
        String afterSecond = run(pom);
        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    @Test
    public void computes_qualified_checksum_from_jar() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Path resolved = Files.createDirectory(input.resolve("resolved"));
        Path jar = resolved.resolve("maven-org.jetbrains-something-1.2.3.jar");
        byte[] payload = "qualified-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(jar, payload);
        writeResolved("kotlin", Map.of("maven/org.jetbrains/something", "1.2.3"));
        String result = run(pom);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        String expected = HexFormat.of().formatHex(digest.digest(payload));
        assertThat(result).contains("kotlin/maven/org.jetbrains/something 1.2.3 SHA-256/" + expected);
    }

    @Test
    public void writes_every_pin_into_the_pin_comment_and_none_into_dependency_management() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        writeResolved(Map.of(
                "maven/org.example/plain", "1.0 SHA-256/aa",
                "maven/org.example/sources/jar/sources", "4.0 SHA-256/cafebabe",
                "maven/org.example/zipped/zip", "5.0",
                "maven/org.example/snapshot", "2.0-SNAPSHOT SHA-256/feedface",
                "module/org.example.module", "1.0"));
        assertThat(run(pom))
                .as("a pin changes nothing Maven resolves, so it lives in the comment alone")
                .contains("<!--jenesis.pin",
                        "org.example/plain 1.0 SHA-256/aa",
                        "maven/org.example/sources/jar/sources 4.0 SHA-256/cafebabe",
                        "maven/org.example/zipped/zip 5.0",
                        "main/module/org.example.module 1.0")
                .as("a snapshot is published again under its version, so no checksum can hold for it")
                .contains("org.example/snapshot 2.0-SNAPSHOT\n")
                .doesNotContain("feedface", "<dependencyManagement>", "Checksum/");
    }

    @Test
    public void writes_a_line_for_every_pom_the_resolution_read_at_each_of_its_versions() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        writeResolved(Map.of("maven/org.example/library", "1.0 SHA-256/aa"));
        SequencedProperties properties = loadInventory();
        int index = count(properties, "module.pom.");
        Path resolved = Files.createDirectories(input.resolve("resolved"));
        for (Map.Entry<String, String> read : Map.of(
                "maven/org.example/library/pom/1.0", "<project>library</project>",
                "maven/org.example/parent/pom/64", "<project>64</project>",
                "maven/org.example/parent/pom/69", "<project>69</project>").entrySet()) {
            String file = read.getKey().replace('/', '-') + ".pom";
            Files.writeString(resolved.resolve(file), read.getValue());
            properties.setProperty("module.pom." + index, read.getKey() + " resolved/" + file);
            properties.setProperty("module.pom." + index + ".group", "main");
            index++;
        }
        properties.store(input.resolve(Inventory.INVENTORY));
        String result = run(pom);
        assertThat(result)
                .as("a parent read at two versions is two files, each pinned by a line of its own")
                .contains("maven/org.example/library/pom 1.0 " + sha256("<project>library</project>"),
                        "maven/org.example/parent/pom 64 " + sha256("<project>64</project>"),
                        "maven/org.example/parent/pom 69 " + sha256("<project>69</project>"));
        assertThat(run(pom)).as("the lines read back as what pin writes").isEqualTo(result);
    }

    @Test
    public void computes_the_checksum_of_a_jar_that_is_present() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(Files.createDirectory(input.resolve("resolved")).resolve("maven-org.example-dep-1.0.jar"), "jar-bytes");
        writeResolved(Map.of("maven/org.example/dep", "1.0 SHA-256/stale"));
        assertThat(run(pom))
                .contains("org.example/dep 1.0 " + sha256("jar-bytes"))
                .doesNotContain("SHA-256/stale");
    }

    @Test
    public void pins_a_coordinate_at_the_version_the_pom_declares_inline_rather_than_at_another_halfs() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>2.0.19</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        writeResolved(Map.of("maven/org.slf4j/slf4j-api", "1.7.36 SHA-256/main"));
        writeResolved(Map.of("maven/org.slf4j/slf4j-api", "2.0.19 SHA-256/test"));
        SequencedProperties module = new SequencedProperties();
        module.setProperty("path", "");
        module.store(input.resolve(BuildStep.MODULE));
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.slf4j/slf4j-api/2.0.19", "");
        requires.setProperty("main/runtime/maven/org.slf4j/slf4j-api/2.0.19", "");
        requires.store(input.resolve(BuildStep.REQUIRES));
        assertThat(run(pom))
                .as("the version the pom declares itself is the one its resolution keeps")
                .contains("org.slf4j/slf4j-api 2.0.19 SHA-256/test")
                .doesNotContain("1.7.36");
    }

    @Test
    public void skips_internal_coordinates_from_identity() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        writeResolved(new LinkedHashMap<>(Map.of(
                "maven/com.example/internal", "0-SNAPSHOT",
                "maven/com.example/external", "1.2.3 SHA-256/cafebabe")));
        writeIdentity(Map.of("maven/com.example/internal/0-SNAPSHOT", ""));
        assertThat(run(pom))
                .contains("com.example/external 1.2.3 SHA-256/cafebabe")
                .doesNotContain("com.example/internal");
    }

    @Test
    public void keeps_the_pins_of_a_group_the_run_resolved_nothing_in() throws IOException {
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.pin
                    launcher/maven/build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/kept
                    org.example/stale 1.0 SHA-256/stale
                    -->
                </project>
                """);
        writeResolved(Map.of("maven/org.example/fresh", "2.0 SHA-256/fresh"));
        assertThat(run(pom))
                .as("a run without the profile that resolves the launcher leaves its pin for the run that does")
                .contains("launcher/maven/build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/kept",
                        "org.example/fresh 2.0 SHA-256/fresh")
                .as("a pin of a group the run resolved, which it no longer reaches, is dropped")
                .doesNotContain("SHA-256/stale");
    }
}
