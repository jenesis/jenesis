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

    default Map<String, String> systemProperties() {
        return Map.of();
    }

    List<String> arguments(Path supplement,
                           Path output,
                           SequencedSet<String> classes,
                           SequencedMap<String, SequencedSet<String>> methods,
                           TestTags tags,
                           List<TestTags> ran,
                           boolean parallel,
                           boolean reporting);

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
        for (Path folder : folders) {
            List<Path> jars = new ArrayList<>();
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
            for (Path file : jars) {
                ModuleDescriptor module = PathPlacement.moduleDescriptor(file);
                if (module != null) {
                    modules.add(module);
                }
            }
        }
        modules.sort(Comparator.comparing(ModuleDescriptor::name));
        return modules;
    }
}
