package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;

public record JUnitPlatform() implements TestEngine {

    private static final String JUNIT4 = "junit",
            JUPITER_API = "org.junit.jupiter.api",
            JUPITER_ENGINE = "org.junit.jupiter.engine",
            PLATFORM_COMMONS = "org.junit.platform.commons",
            PLATFORM_CONSOLE = "org.junit.platform.console",
            PLATFORM_ENGINE = "org.junit.platform.engine",
            VINTAGE_ENGINE = "org.junit.vintage.engine";

    @Override
    public String runnerModule() {
        return PLATFORM_CONSOLE;
    }

    @Override
    public boolean isFramework(ModuleDescriptor module) {
        return module.name().equals(PLATFORM_ENGINE) || module.name().equals(JUPITER_API);
    }

    @Override
    public SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
        SequencedMap<String, String> coordinates = new LinkedHashMap<>();
        if (!contains(modules, PLATFORM_CONSOLE)) {
            artifact(coordinates, PLATFORM_CONSOLE, "org.junit.platform/junit-platform-console",
                    version(modules, PLATFORM_ENGINE, PLATFORM_COMMONS));
        }
        if (contains(modules, JUPITER_API) && !contains(modules, JUPITER_ENGINE)) {
            artifact(coordinates, JUPITER_ENGINE, "org.junit.jupiter/junit-jupiter-engine",
                    version(modules, JUPITER_API));
        }
        if (contains(modules, JUPITER_API) && contains(modules, JUNIT4) && !contains(modules, VINTAGE_ENGINE)) {
            artifact(coordinates, VINTAGE_ENGINE, "org.junit.vintage/junit-vintage-engine",
                    version(modules, JUPITER_API));
        }
        return coordinates;
    }

    @Override
    public String mainClass() {
        return "org.junit.platform.console.ConsoleLauncher";
    }

    @Override
    public Map<String, String> properties() {
        return Map.of("org.jline.terminal.dumb", "true");
    }

    @Override
    public List<String> commands(Path supplement,
                                 Path output,
                                 SequencedSet<String> classes,
                                 SequencedMap<String, SequencedSet<String>> methods,
                                 SequencedSet<String> groups,
                                 boolean parallel,
                                 boolean reporting) {
        List<String> commands = new ArrayList<>(List.of("execute", "--disable-banner", "--disable-ansi-colors"));
        for (String group : groups) {
            commands.add("--include-tag=" + group);
        }
        if (parallel) {
            commands.add("--config=junit.jupiter.execution.parallel.enabled=true");
            commands.add("--config=junit.jupiter.execution.parallel.mode.default=concurrent");
        }
        if (reporting) {
            Path reports = output.resolve(BuildStep.REPORTS + "tests");
            commands.add("--reports-dir=" + reports);
            commands.add("--config=junit.platform.reporting.open.xml.enabled=true");
            commands.add("--config=junit.platform.reporting.output.dir=" + reports);
        }
        for (String className : classes) {
            commands.add("--select-class=" + className);
        }
        for (Map.Entry<String, SequencedSet<String>> entry : methods.entrySet()) {
            for (String method : entry.getValue()) {
                commands.add("--select-method=" + entry.getKey() + "#" + method);
            }
        }
        return commands;
    }

    private static void artifact(SequencedMap<String, String> coordinates,
                                 String module,
                                 String maven,
                                 String version) {
        coordinates.put("module/" + module, version);
        coordinates.put("maven/" + maven, version == null ? "RELEASE" : version);
    }

    private static boolean contains(List<ModuleDescriptor> modules, String name) {
        return modules.stream().anyMatch(module -> module.name().equals(name));
    }

    private static String version(List<ModuleDescriptor> modules, String... names) {
        return Stream.of(names)
                .flatMap(name -> modules.stream().filter(module -> module.name().equals(name)))
                .flatMap(module -> module.rawVersion().stream())
                .findFirst()
                .orElse(null);
    }
}
