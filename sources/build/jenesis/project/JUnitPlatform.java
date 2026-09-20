package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;

public record JUnitPlatform() implements TestEngine {

    private static final String JUNIT4 = "junit";

    private static final String JUPITER_API = "org.junit.jupiter.api";

    private static final String JUPITER_ENGINE = "org.junit.jupiter.engine";

    private static final String PLATFORM_COMMONS = "org.junit.platform.commons";

    private static final String PLATFORM_CONSOLE = "org.junit.platform.console";

    private static final String PLATFORM_ENGINE = "org.junit.platform.engine";

    private static final String VINTAGE_ENGINE = "org.junit.vintage.engine";

    @Override
    public String runnerModule() {
        return PLATFORM_CONSOLE;
    }

    @Override
    public boolean isEngine(ModuleDescriptor module) {
        return module.name().equals(PLATFORM_ENGINE);
    }

    @Override
    public boolean isFramework(ModuleDescriptor module) {
        return isEngine(module) || module.name().equals(JUPITER_API);
    }

    @Override
    public boolean isRunner(ModuleDescriptor module) {
        return module.name().equals(PLATFORM_CONSOLE);
    }

    @Override
    public SequencedMap<String, String> coordinates(ModuleDescriptor engine) {
        SequencedMap<String, String> coordinates = new LinkedHashMap<>();
        console(coordinates, engine == null ? null : engine.rawVersion().orElse(null));
        return coordinates;
    }

    @Override
    public SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
        SequencedMap<String, String> coordinates = new LinkedHashMap<>();
        if (!hasRunner(modules)) {
            console(coordinates, version(modules, PLATFORM_ENGINE, PLATFORM_COMMONS));
        }
        if (contains(modules, JUPITER_API) && !contains(modules, JUPITER_ENGINE)) {
            engine(coordinates, JUPITER_ENGINE, "org.junit.jupiter/junit-jupiter-engine", version(modules, JUPITER_API));
        }
        if (contains(modules, JUPITER_API) && contains(modules, JUNIT4) && !contains(modules, VINTAGE_ENGINE)) {
            engine(coordinates, VINTAGE_ENGINE, "org.junit.vintage/junit-vintage-engine", version(modules, JUPITER_API));
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

    private static void engine(SequencedMap<String, String> coordinates,
                               String module,
                               String artifact,
                               String version) {
        coordinates.put("module/" + module, version);
        coordinates.put("maven/" + artifact, version == null ? "RELEASE" : version);
    }

    private static void console(SequencedMap<String, String> coordinates, String version) {
        coordinates.put("module/" + PLATFORM_CONSOLE, version);
        coordinates.put("maven/org.junit.platform/junit-platform-console", version == null ? "RELEASE" : version);
    }

    private static boolean contains(List<ModuleDescriptor> modules, String name) {
        for (ModuleDescriptor module : modules) {
            if (module.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String version(List<ModuleDescriptor> modules, String... names) {
        for (String name : names) {
            for (ModuleDescriptor module : modules) {
                if (module.name().equals(name) && module.rawVersion().isPresent()) {
                    return module.rawVersion().get();
                }
            }
        }
        return null;
    }
}
