package build.jenesis;

import module java.base;

public class ModuleGraph {

    public static final String JAVA_OPTIONS = "javaOptions";

    private static final String SELF_CONTAINED = "selfContainedModuleGraph";

    private static final String ADD_MODULES = "--add-modules", ROOTS = "ALL-MODULE-PATH,ALL-DEFAULT";

    private boolean modular, automatic, unnamed;

    public void place(PathPlacement placement, Path file, List<String> modulePath, List<String> classPath)
            throws IOException {
        (place(placement, file) ? modulePath : classPath).add(file.toString());
    }

    public boolean place(PathPlacement placement, Path file) throws IOException {
        if (placement.test(file)) {
            module(file);
            return true;
        }
        unnamed();
        return false;
    }

    public void module(Path file) {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
        modular = true;
        automatic |= descriptor == null ? !Files.isDirectory(file) : descriptor.isAutomatic();
    }

    public void unnamed() {
        unnamed = true;
    }

    private boolean selfContained() {
        return !modular || !automatic && !unnamed;
    }

    public List<String> arguments() {
        return selfContained() ? List.of() : List.of(ADD_MODULES, ROOTS);
    }

    public List<String> options() {
        return selfContained() ? List.of() : List.of(ADD_MODULES + "=" + ROOTS);
    }

    public void store(SequencedProperties properties) {
        if (!selfContained()) {
            properties.setProperty(JAVA_OPTIONS, String.join(" ", options()));
        }
    }

    public static List<String> load(Properties properties) {
        String options = properties.getProperty(JAVA_OPTIONS);
        if (options != null) {
            return Arrays.stream(options.split(" ")).filter(option -> !option.isEmpty()).toList();
        }
        String legacy = properties.getProperty(SELF_CONTAINED);
        return legacy == null || Boolean.parseBoolean(legacy)
                ? List.of()
                : List.of("--add-modules=ALL-MODULE-PATH");
    }
}
