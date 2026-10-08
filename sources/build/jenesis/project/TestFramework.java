package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.PathPlacement;
import build.jenesis.step.Dependencies;

public interface TestFramework extends Serializable {

    String runnerModule();

    String runnerClass();

    boolean isMarkedBy(ModuleDescriptor module);

    Set<String> reflectingModules();

    default SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
        return Collections.emptyNavigableMap();
    }

    default boolean holdsTests(ClassModel type) {
        return true;
    }

    default Map<String, String> systemProperties() {
        return Map.of();
    }

    default TestFramework runningOn(List<Path> jars) throws IOException {
        return this;
    }

    List<String> arguments(Path supplement,
                           Path output,
                           SequencedSet<String> classes,
                           SequencedMap<String, SequencedSet<String>> methods,
                           boolean parallel,
                           boolean reporting);

    default List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
        if (!requested.all()) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " cannot select tests by tag, so it cannot run "
                    + requested);
        }
        return arguments;
    }

    default List<String> engines(List<String> arguments, String engines) {
        if (engines != null) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " runs on no JUnit Platform engine, so"
                    + " jenesis.test.engines cannot select " + engines + " - leave it unset for these tests");
        }
        return arguments;
    }

    static TestFramework named(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "junit-platform" -> new JUnitPlatform();
            case "junit4" -> new JUnit4();
            case "testng" -> new TestNG();
            default -> throw new IllegalArgumentException("Unknown test framework: " + name
                    + " (expected junit-platform, junit4, or testng)");
        };
    }

    static Optional<TestFramework> detect(List<ModuleDescriptor> modules) {
        return Stream.<TestFramework>of(new JUnitPlatform(), new JUnit4(), new TestNG())
                .filter(framework -> modules.stream().anyMatch(framework::isMarkedBy))
                .findFirst();
    }

    static Optional<TestFramework> detect(Iterable<Path> folders) throws IOException {
        return detect(modules(folders));
    }

    static List<ModuleDescriptor> modules(Iterable<Path> folders) throws IOException {
        List<ModuleDescriptor> modules = new ArrayList<>();
        for (Path file : jars(folders)) {
            ModuleDescriptor module = PathPlacement.moduleDescriptor(file);
            if (module != null) {
                modules.add(module);
            }
        }
        modules.sort(Comparator.comparing(ModuleDescriptor::name));
        return modules;
    }

    static List<Path> jars(Iterable<Path> folders) throws IOException {
        List<Path> jars = new ArrayList<>();
        for (Path folder : folders) {
            Path artifacts = folder.resolve(BuildStep.ARTIFACTS);
            if (Files.exists(artifacts)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifacts)) {
                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            jars.add(file);
                        }
                    }
                }
            }
            jars.addAll(Dependencies.all(folder));
        }
        return jars;
    }
}
