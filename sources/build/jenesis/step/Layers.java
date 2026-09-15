package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.PathPlacement;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;

public class Layers implements BuildStep {

    public static final String LAYER_PATH = "layers/";

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final String group;

    public Layers() {
        this("main");
    }

    private Layers(String group) {
        this.group = group;
    }

    public Layers group(String group) {
        return new Layers(group);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> declared = declared(arguments);
        if (declared.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Path root = Files.createDirectory(context.next().resolve(LAYER_PATH));
        SequencedMap<String, ModuleDescriptor> host = descriptors(arguments);
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String layer = entry.getKey(), api = entry.getValue();
            Path folder = Files.createDirectory(root.resolve(layer));
            verify(layer, api, isolate(layer, api, closure(api, host), arguments, folder));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    /**
     * The layers a module declares, each name mapped to the API module it shares with them. What a layer
     * isolates is read from its resolved group rather than from here: a coordinate is declared, but its
     * whole closure is what ends up isolated.
     */
    public static SequencedMap<String, String> declared(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> declared = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path declaration = argument.folder().resolve(BuildStep.LAYERS);
            if (!Files.isRegularFile(declaration)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(declaration);
            for (String layer : properties.stringPropertyNames()) {
                SAFE_SEGMENT.accept("layer name", layer);
                List<String> tokens = properties.words(layer);
                if (tokens.isEmpty()) {
                    throw new IllegalArgumentException("Layer " + layer + " in " + declaration
                            + " names no API module");
                }
                declared.putIfAbsent(layer, tokens.getFirst());
            }
        }
        return declared;
    }

    /**
     * The modules a host and its layer hold in common: the API module and everything it reaches. They are
     * left out of the layer, so the layer resolves them from the host and both sides hold the very same
     * classes - which is what lets a service instance cross as a plain interface call rather than a proxy.
     */
    private static SequencedSet<String> closure(String api, SequencedMap<String, ModuleDescriptor> host) {
        SequencedSet<String> shared = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(api));
        while (!pending.isEmpty()) {
            String module = pending.removeFirst();
            if (platform(module) || !shared.add(module)) {
                continue;
            }
            ModuleDescriptor descriptor = host.get(module);
            if (descriptor != null) {
                descriptor.requires().forEach(requires -> pending.addLast(requires.name()));
            }
        }
        return shared;
    }

    private static SequencedMap<String, ModuleDescriptor> isolate(String layer,
                                                                  String api,
                                                                  SequencedSet<String> shared,
                                                                  SequencedMap<String, BuildStepArgument> arguments,
                                                                  Path folder) throws IOException {
        SequencedMap<String, ModuleDescriptor> isolated = new LinkedHashMap<>();
        SequencedMap<String, String> carriers = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), "layer:" + layer, "runtime")) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                if (descriptor == null) {
                    throw new IllegalStateException(jar.getFileName() + " carries no module but is isolated in"
                            + " layer " + layer + " - a layer resolves modules only, as a named module cannot"
                            + " read the unnamed module; declare it in modules.properties to derive a"
                            + " descriptor, or keep it out of the layer");
                }
                if (shared.contains(descriptor.name())) {
                    continue;
                }
                String file = jar.getFileName().toString();
                String carrier = carriers.putIfAbsent(descriptor.name(), file);
                if (carrier == null) {
                    isolated.put(descriptor.name(), descriptor);
                    BuildStep.linkOrCopy(folder.resolve(file), jar);
                } else if (!carrier.equals(file)) {
                    throw new IllegalStateException(carrier + " and " + file + " both carry module "
                            + descriptor.name() + " in layer " + layer + " - a layer resolves whichever of"
                            + " the two comes first, so drop one with @jenesis.exclude");
                }
            }
        }
        if (isolated.isEmpty()) {
            throw new IllegalStateException("Layer " + layer + " isolates nothing: everything it resolves is"
                    + " already shared through " + api + " - a dependency whose types the API module reaches"
                    + " is exposed by it, and cannot be isolated behind it");
        }
        return isolated;
    }

    private SequencedMap<String, ModuleDescriptor> descriptors(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "runtime")) {
                describe(jar, descriptors);
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        describe(file, descriptors);
                    }
                }
            }
        }
        return descriptors;
    }

    private static void describe(Path file, SequencedMap<String, ModuleDescriptor> descriptors) {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
        if (descriptor != null) {
            descriptors.putIfAbsent(descriptor.name(), descriptor);
        }
    }

    /**
     * Refuses a layer that provides a service and also holds the module declaring it. The host would look
     * that service up against a different class of the same name and find no provider - silent rather than
     * wrong - so it is named here, where the API module that should have carried the contract is known.
     */
    private static void verify(String layer, String api, SequencedMap<String, ModuleDescriptor> isolated) {
        for (ModuleDescriptor descriptor : isolated.values()) {
            for (ModuleDescriptor.Provides provides : descriptor.provides()) {
                String contract = provides.service();
                int last = contract.lastIndexOf('.');
                String declaring = last < 1 ? null : declaring(contract.substring(0, last), isolated);
                if (declaring != null) {
                    throw new IllegalStateException("Layer " + layer + " provides " + contract
                            + ", but holds " + declaring + ", which declares it - this module would look the"
                            + " service up against a different class of the same name and find no provider;"
                            + " the contract belongs in " + api + ", the module the layer shares with it");
                }
            }
        }
    }

    private static String declaring(String contract, SequencedMap<String, ModuleDescriptor> isolated) {
        for (Map.Entry<String, ModuleDescriptor> entry : isolated.entrySet()) {
            if (entry.getValue().packages().contains(contract)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean platform(String module) {
        return module.startsWith("java.") || module.startsWith("jdk.");
    }
}
