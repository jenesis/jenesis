package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.PathPlacement;
import build.jenesis.step.Dependencies;

public interface TestEngine extends Serializable {

    String runnerModule();

    String mainClass();

    boolean isEngine(ModuleDescriptor module);

    default boolean isFramework(ModuleDescriptor module) {
        return isEngine(module);
    }

    default SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
        return Collections.emptyNavigableMap();
    }

    default Map<String, String> properties() {
        return Map.of();
    }

    List<String> commands(Path supplement,
                          Path output,
                          SequencedSet<String> classes,
                          SequencedMap<String, SequencedSet<String>> methods,
                          SequencedSet<String> groups,
                          boolean parallel,
                          boolean reporting);

    static TestEngine of(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "junit-platform" -> new JUnitPlatform();
            case "junit4" -> new JUnit4();
            case "testng" -> new TestNG();
            default -> throw new IllegalArgumentException("Unknown test engine: " + name
                    + " (expected junit-platform, junit4, or testng)");
        };
    }

    static Optional<TestEngine> of(List<ModuleDescriptor> modules) {
        return Stream.<TestEngine>of(new JUnitPlatform(), new JUnit4(), new TestNG())
                .filter(engine -> modules.stream().anyMatch(engine::isFramework))
                .findFirst();
    }

    static Optional<TestEngine> of(Iterable<Path> folders) throws IOException {
        return of(scan(folders));
    }

    static List<ModuleDescriptor> scan(Iterable<Path> folders) throws IOException {
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
