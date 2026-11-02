package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Jdk;
import build.jenesis.Json;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeImageMetadataTest {

    private static final Path METADATA = Path.of("sources/META-INF/native-image/build.jenesis/build.jenesis");

    @Test
    public void registers_every_serializable_class_of_the_engine_for_serialization() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(METADATA), "runs from a source checkout");
        Set<String> registered = new TreeSet<>();
        for (Object entry : (List<?>) ((Map<?, ?>) Json.parse(Files.readString(
                METADATA.resolve("reachability-metadata.json")))).get("reflection")) {
            Map<?, ?> type = (Map<?, ?>) entry;
            if (Boolean.TRUE.equals(type.get("serializable")) && type.get("type") instanceof String name) {
                registered.add(name);
            }
        }
        assertThat(classes(type -> Serializable.class.isAssignableFrom(type) && !type.isInterface()))
                .as("a native image deserialises no class its metadata does not name, and every step is serialised"
                        + " to key its cache entry")
                .isSubsetOf(registered);
    }

    @Test
    public void registers_every_class_declaring_a_serializable_lambda() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(METADATA), "runs from a source checkout");
        Set<String> registered = new TreeSet<>();
        for (Object entry : (List<?>) ((Map<?, ?>) Json.parse(Files.readString(
                METADATA.resolve("serialization-config.json")))).get("lambdaCapturingTypes")) {
            registered.add((String) ((Map<?, ?>) entry).get("name"));
        }
        assertThat(classes(type -> Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("$deserializeLambda$"))))
                .as("a native image serialises no lambda of a class its metadata does not name")
                .isSubsetOf(registered);
    }

    private static SequencedSet<String> classes(Predicate<Class<?>> filter) throws Exception {
        Path location = Path.of(Jdk.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        SequencedSet<String> classes = new TreeSet<>();
        int found = 0;
        try (FileSystem jar = Files.isDirectory(location) ? null : FileSystems.newFileSystem(location)) {
            Path root = jar == null ? location : jar.getPath("/");
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(file -> file.toString().endsWith(".class")).toList()) {
                    String name = root.relativize(file).toString();
                    if (name.equals("module-info.class")) {
                        continue;
                    }
                    name = name.substring(0, name.length() - ".class".length())
                            .replace(file.getFileSystem().getSeparator(), ".");
                    found++;
                    Class<?> type = Class.forName(name, false, Jdk.class.getClassLoader());
                    if (filter.test(type)) {
                        classes.add(name);
                    }
                }
            }
        }
        assertThat(found).as("the classes of the engine are found at " + location).isPositive();
        return classes;
    }
}
