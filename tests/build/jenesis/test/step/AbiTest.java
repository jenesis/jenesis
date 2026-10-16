package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Abi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbiTest {

    @TempDir
    private Path root;
    private Path sources, input, persistent;
    private int run;

    @BeforeEach
    public void setUp() throws IOException {
        sources = Files.createDirectory(root.resolve("sources"));
        input = Files.createDirectory(root.resolve("input"));
        persistent = Files.createDirectory(root.resolve("persistent"));
    }

    @Test
    public void hashes_the_public_classes_of_the_packages_a_module_exports() throws IOException {
        source("module-info", "module sample { exports sample.api; }");
        source("sample.api.Api", "package sample.api; public class Api { }");
        source("sample.api.Helper", "package sample.api; class Helper { }");
        source("sample.internal.Internal", "package sample.internal; public class Internal { }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null).keySet())
                .containsExactlyInAnyOrder("module-info.class", "sample/api/Api.class");
    }

    @Test
    public void hashes_every_package_with_its_package_private_classes_on_the_class_path() throws IOException {
        source("module-info", "module sample { exports sample.api; }");
        source("sample.api.Api", "package sample.api; public class Api { }");
        source("sample.api.Helper", "package sample.api; class Helper { }");
        source("sample.internal.Internal", "package sample.internal; public class Internal { }");
        assertThat(abi(Abi.Mode.CLASSPATH, ChecksumStatus.ADDED, null).keySet())
                .containsExactlyInAnyOrder("module-info.class",
                        "sample/api/Api.class",
                        "sample/api/Helper.class",
                        "sample/internal/Internal.class");
    }

    @Test
    public void reads_every_package_as_exported_without_a_module_descriptor() throws IOException {
        source("sample.api.Api", "package sample.api; public class Api { }");
        source("sample.internal.Internal", "package sample.internal; public class Internal { }");
        source("sample.internal.Helper", "package sample.internal; class Helper { }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null).keySet())
                .containsExactlyInAnyOrder("sample/api/Api.class", "sample/internal/Internal.class");
    }

    @Test
    public void hashes_a_supertype_whose_members_an_exported_class_inherits() throws IOException {
        source("module-info", "module sample { exports sample.api; }");
        source("sample.api.Api", "package sample.api; public class Api extends sample.internal.Base { }");
        source("sample.internal.Base", "package sample.internal; public class Base { public void inherited() { } }");
        source("sample.api.Shared", "package sample.api; abstract class Shared { protected void hook() { } }");
        source("sample.api.Extended", "package sample.api; public class Extended extends Shared { }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null).keySet())
                .containsExactlyInAnyOrder("module-info.class",
                        "sample/api/Api.class",
                        "sample/internal/Base.class",
                        "sample/api/Extended.class",
                        "sample/api/Shared.class");
    }

    @Test
    public void leaves_out_private_local_and_anonymous_classes() throws IOException {
        source("sample.Api", """
                package sample;
                public class Api {
                    public static class Nested { }
                    private static class Hidden { }
                    public Runnable anonymous() { return new Runnable() { public void run() { } }; }
                    public Object local() { class Local { } return new Local(); }
                }
                """);
        assertThat(abi(Abi.Mode.CLASSPATH, ChecksumStatus.ADDED, null).keySet())
                .containsExactlyInAnyOrder("sample/Api.class", "sample/Api$Nested.class");
    }

    @Test
    public void leaves_out_a_public_class_nested_in_a_package_private_one() throws IOException {
        source("sample.Outer", "package sample; class Outer { public static class Nested { } }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null)).isEmpty();
    }

    @Test
    public void keeps_the_hash_when_only_bodies_and_private_members_change() throws IOException {
        source("sample.Api", "package sample; public class Api { public int value() { return 1; } }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", """
                package sample;
                public class Api {
                    private int cached;
                    public int value() { return helper() + 1; }
                    private int helper() { return cached; }
                }
                """);
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null)).isEqualTo(before);
    }

    @Test
    public void changes_the_hash_when_a_public_member_changes() throws IOException {
        source("sample.Api", "package sample; public class Api { public int value() { return 1; } }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; public class Api { public long value() { return 1; } }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null).get("sample/Api.class"))
                .isNotEqualTo(before.get("sample/Api.class"));
    }

    @Test
    public void changes_the_hash_when_a_constant_changes() throws IOException {
        source("sample.Api", "package sample; public class Api { public static final int LIMIT = 1; }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; public class Api { public static final int LIMIT = 2; }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null).get("sample/Api.class"))
                .as("a caller compiles the constant into its own class file")
                .isNotEqualTo(before.get("sample/Api.class"));
    }

    @Test
    public void changes_the_hash_when_an_exception_or_annotation_changes() throws IOException {
        source("sample.Api", "package sample; public class Api { public void run() { } }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; public class Api { public void run() throws java.io.IOException { } }");
        Map<String, String> thrown = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api",
                "package sample; public class Api { @Deprecated public void run() throws java.io.IOException { } }");
        Map<String, String> deprecated = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        assertThat(Set.of(before.get("sample/Api.class"),
                thrown.get("sample/Api.class"),
                deprecated.get("sample/Api.class")))
                .hasSize(3);
    }

    @Test
    public void hashes_package_private_members_only_on_the_class_path() throws IOException {
        source("sample.Api", "package sample; public class Api { void internal() { } }");
        Map<String, String> modular = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null),
                classpath = abi(Abi.Mode.CLASSPATH, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; public class Api { void internal(int value) { } }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null)).isEqualTo(modular);
        assertThat(abi(Abi.Mode.CLASSPATH, ChecksumStatus.ADDED, null)).isNotEqualTo(classpath);
    }

    @Test
    public void changes_the_hash_of_the_module_descriptor_when_an_export_changes() throws IOException {
        source("module-info", "module sample { exports sample.api; }");
        source("sample.api.Api", "package sample.api; public class Api { }");
        source("sample.internal.Internal", "package sample.internal; public class Internal { }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("module-info", "module sample { exports sample.api; exports sample.internal; }");
        Map<String, String> after = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        assertThat(after.get("module-info.class")).isNotEqualTo(before.get("module-info.class"));
        assertThat(after).containsKey("sample/internal/Internal.class");
    }

    @Test
    public void reuses_the_recorded_hash_of_a_class_file_that_did_not_change() throws IOException {
        source("sample.Api", "package sample; public class Api { public int value() { return 1; } }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; public class Api { public long value() { return 1; } }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.RETAINED, root.resolve("previous")))
                .as("a retained class file is not read again")
                .isEqualTo(before);
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ALTERED, root.resolve("previous")).get("sample/Api.class"))
                .isNotEqualTo(before.get("sample/Api.class"));
    }

    @Test
    public void reads_every_class_file_again_without_a_previous_run() throws IOException {
        source("sample.Api", "package sample; public class Api { public int value() { return 1; } }");
        Map<String, String> before = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; public class Api { public long value() { return 1; } }");
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.RETAINED, null)).isNotEqualTo(before);
    }

    @Test
    public void writes_the_same_from_a_recorded_state_of_another_mode_as_from_none() throws IOException {
        source("sample.Api", "package sample; public class Api { void internal() { } }");
        source("sample.Helper", "package sample; class Helper { }");
        abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        Map<String, String> recorded = abi(Abi.Mode.CLASSPATH, ChecksumStatus.RETAINED, root.resolve("previous"));
        Files.walkFileTree(persistent, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
        assertThat(recorded).isEqualTo(abi(Abi.Mode.CLASSPATH, ChecksumStatus.ADDED, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes_that_reach_a_caller")
    public void changes_the_hash_for_a_change_that_reaches_a_caller(String section, String before, String after)
            throws IOException {
        source("sample.Api", "package sample; " + before);
        Map<String, String> hashes = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; " + after);
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null)).as(section).isNotEqualTo(hashes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changes_without_effect_on_a_caller")
    public void keeps_the_hash_for_a_change_without_effect_on_a_caller(String section, String before, String after)
            throws IOException {
        source("sample.Api", "package sample; " + before);
        Map<String, String> hashes = abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null);
        source("sample.Api", "package sample; " + after);
        assertThat(abi(Abi.Mode.MODULAR, ChecksumStatus.ADDED, null)).as(section).isEqualTo(hashes);
    }

    static Stream<Arguments> changes_that_reach_a_caller() {
        return Stream.of(
                Arguments.of("13.4.1 abstract class", "public class Api { }", "public abstract class Api { }"),
                Arguments.of("13.4.2.1 sealed class",
                        "public class Api { public static final class Sub extends Api { } }",
                        "public sealed class Api permits Api.Sub { public static final class Sub extends Api { } }"),
                Arguments.of("13.4.2.3 final class", "public class Api { }", "public final class Api { }"),
                Arguments.of("13.4.3 public class", "public class Api { }", "class Api { }"),
                Arguments.of("13.4.4 superclass", "public class Api { }", "public class Api extends Exception { }"),
                Arguments.of("13.4.4 superinterface",
                        "public abstract class Api { }",
                        "public abstract class Api implements Runnable { }"),
                Arguments.of("13.4.5 class type parameter bound",
                        "public class Api<T> { }",
                        "public class Api<T extends Number> { }"),
                Arguments.of("13.4.6 member added",
                        "public class Api { }",
                        "public class Api { public void added() { } }"),
                Arguments.of("13.4.7 member access",
                        "public class Api { public void m() { } }",
                        "public class Api { protected void m() { } }"),
                Arguments.of("13.4.8 field deleted", "public class Api { public int f; }", "public class Api { }"),
                Arguments.of("13.4.9 final field",
                        "public class Api { public static int F; }",
                        "public class Api { public static final int F = 0; }"),
                Arguments.of("13.4.9 constant value",
                        "public class Api { public static final String F = \"a\"; }",
                        "public class Api { public static final String F = \"b\"; }"),
                Arguments.of("13.4.10 static field",
                        "public class Api { public int f; }",
                        "public class Api { public static int f; }"),
                Arguments.of("13.4.12 method deleted",
                        "public class Api { public void m() { } }",
                        "public class Api { }"),
                Arguments.of("13.4.12 constructor access",
                        "public class Api { public Api() { } }",
                        "public class Api { protected Api() { } }"),
                Arguments.of("13.4.13 method type parameter bound",
                        "public class Api { public <T> void m(T t) { } }",
                        "public class Api { public <T extends Number> void m(T t) { } }"),
                Arguments.of("13.4.14 parameter type",
                        "public class Api { public void m(int a) { } }",
                        "public class Api { public void m(long a) { } }"),
                Arguments.of("13.4.14 variable arity",
                        "public class Api { public void m(int[] a) { } }",
                        "public class Api { public void m(int... a) { } }"),
                Arguments.of("13.4.15 result type",
                        "public class Api { public int m() { return 0; } }",
                        "public class Api { public long m() { return 0; } }"),
                Arguments.of("13.4.16 abstract method",
                        "public abstract class Api { public void m() { } }",
                        "public abstract class Api { public abstract void m(); }"),
                Arguments.of("13.4.17 final method",
                        "public class Api { public void m() { } }",
                        "public class Api { public final void m() { } }"),
                Arguments.of("13.4.19 static method",
                        "public class Api { public void m() { } }",
                        "public class Api { public static void m() { } }"),
                Arguments.of("13.4.21 throws clause",
                        "public class Api { public void m() { } }",
                        "public class Api { public void m() throws Exception { } }"),
                Arguments.of("13.4.26 enum constant added", "public enum Api { A }", "public enum Api { A, B }"),
                Arguments.of("13.4.27 record component",
                        "public record Api(int a) { }",
                        "public record Api(long a) { }"),
                Arguments.of("13.4.27 record components reordered",
                        "public record Api(int a, long b) { }",
                        "public record Api(long b, int a) { }"),
                Arguments.of("13.5.7 default method",
                        "public interface Api { void m(); }",
                        "public interface Api { default void m() { } }"),
                Arguments.of("13.5.8 annotation element default",
                        "public @interface Api { int value(); }",
                        "public @interface Api { int value() default 1; }"),
                Arguments.of("13.5.8 annotation retention",
                        "public @interface Api { }",
                        "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Api { }"));
    }

    static Stream<Arguments> changes_without_effect_on_a_caller() {
        return Stream.of(
                Arguments.of("13.4.6 private member added",
                        "public class Api { }",
                        "public class Api { private void added() { } }"),
                Arguments.of("13.4.9 initializer of a field that is no constant",
                        "public class Api { public static int f = Integer.parseInt(\"1\"); }",
                        "public class Api { public static int f = Integer.parseInt(\"2\"); }"),
                Arguments.of("13.4.11 transient field",
                        "public class Api { public int f; }",
                        "public class Api { public transient int f; }"),
                Arguments.of("13.4.14 parameter renamed",
                        "public class Api { public void m(int a) { } }",
                        "public class Api { public void m(int b) { } }"),
                Arguments.of("13.4.18 native method",
                        "public class Api { public void m() { } }",
                        "public class Api { public native void m(); }"),
                Arguments.of("13.4.20 synchronized method",
                        "public class Api { public void m() { } }",
                        "public class Api { public synchronized void m() { } }"),
                Arguments.of("13.4.22 method body",
                        "public class Api { public int m() { return 1; } }",
                        "public class Api { public int m() { return 2; } }"),
                Arguments.of("13.4.25 static initializer",
                        "public class Api { }",
                        "public class Api { static { System.gc(); } }"),
                Arguments.of("13.4.26 enum constants reordered",
                        "public enum Api { A, B }",
                        "public enum Api { B, A }"));
    }

    @Test
    public void refuses_a_mode_it_does_not_know() {
        assertThatThrownBy(() -> Abi.Mode.of("full"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full")
                .hasMessageContaining("modular")
                .hasMessageContaining("classpath");
    }

    private void source(String type, String content) throws IOException {
        Path file = sources.resolve(type.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private Map<String, String> abi(Abi.Mode mode, ChecksumStatus status, Path previous) throws IOException {
        Path classes = input.resolve(BuildStep.CLASSES);
        if (Files.exists(classes)) {
            try (Stream<Path> files = Files.walk(classes)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        List<String> arguments = new ArrayList<>(List.of("-d", classes.toString()));
        try (Stream<Path> files = Files.walk(sources)) {
            files.filter(file -> file.toString().endsWith(".java")).map(Path::toString).forEach(arguments::add);
        }
        assertThat(javax.tools.ToolProvider.getSystemJavaCompiler().run(null,
                null,
                null,
                arguments.toArray(String[]::new))).isZero();
        Map<Path, Checksum> checksums = new HashMap<>();
        try (Stream<Path> files = Files.walk(classes)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> checksums.put(input.relativize(file), Checksum.of(status)));
        }
        Path next = Files.createDirectory(root.resolve("next-" + run)),
                supplement = Files.createDirectory(root.resolve("supplement-" + run++));
        new Abi(mode).apply(Runnable::run,
                new BuildStepContext(previous, next, supplement, persistent),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(input, checksums))))
                .toCompletableFuture()
                .join();
        SequencedProperties properties = SequencedProperties.ofFiles(next.resolve(Abi.ABI));
        Map<String, String> hashes = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            hashes.put(key, properties.getProperty(key));
        }
        return hashes;
    }
}
