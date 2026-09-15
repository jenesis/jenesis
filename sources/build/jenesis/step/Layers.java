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

    private static final String SHARED = "/shared";
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
        SequencedMap<String, SequencedSet<String>> declared = declared(arguments);
        if (declared.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Path root = Files.createDirectory(context.next().resolve(LAYER_PATH));
        SequencedMap<String, ModuleDescriptor> parent = descriptors(arguments);
        for (Map.Entry<String, SequencedSet<String>> entry : declared.entrySet()) {
            Path folder = Files.createDirectory(root.resolve(entry.getKey()));
            verify(entry.getKey(), isolate(entry.getKey(), entry.getValue(), arguments, folder), parent);
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    public static SequencedMap<String, SequencedSet<String>> declared(
            SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, SequencedSet<String>> declared = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path declaration = argument.folder().resolve(BuildStep.LAYERS);
            if (!Files.isRegularFile(declaration)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(declaration);
            for (String key : properties.stringPropertyNames()) {
                int slash = key.indexOf('/');
                String layer = slash < 0 ? key : key.substring(0, slash);
                SAFE_SEGMENT.accept("layer name", layer);
                SequencedSet<String> shared = declared.computeIfAbsent(layer, _ -> new LinkedHashSet<>());
                if (slash < 0) {
                    continue;
                }
                if (!key.substring(slash).equals(SHARED)) {
                    throw new IllegalArgumentException("Unexpected entry in " + declaration + ": " + key
                            + " (expected <layer> or <layer>" + SHARED + ")");
                }
                List<String> modules = properties.entries(key);
                if (modules != null) {
                    shared.addAll(modules);
                }
            }
        }
        return declared;
    }

    private static SequencedMap<String, ModuleDescriptor> isolate(String layer,
                                                                  SequencedSet<String> shared,
                                                                  SequencedMap<String, BuildStepArgument> arguments,
                                                                  Path folder) throws IOException {
        SequencedMap<String, ModuleDescriptor> isolated = new LinkedHashMap<>();
        SequencedMap<String, String> carriers = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), layer, "runtime")) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                if (descriptor == null) {
                    throw new IllegalStateException(jar.getFileName() + " carries no module but is isolated in layer "
                            + layer + " - a layer resolves modules only, as a named module cannot read the unnamed"
                            + " module; declare it in modules.properties to derive a descriptor, or keep it out of"
                            + " the layer");
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
                            + descriptor.name() + " in layer " + layer + " - a layer resolves whichever of the two"
                            + " comes first, so drop one with @jenesis.exclude");
                }
            }
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

    private static void verify(String layer,
                               SequencedMap<String, ModuleDescriptor> isolated,
                               SequencedMap<String, ModuleDescriptor> parent) {
        for (ModuleDescriptor descriptor : isolated.values()) {
            for (ModuleDescriptor.Provides provides : descriptor.provides()) {
                String contract = provides.service();
                String declaring = declaring(contract, isolated);
                if (declaring != null) {
                    throw new IllegalStateException("layer " + layer + " provides " + contract
                            + ", but the module that declares it, " + declaring + ", is isolated in the layer"
                            + " - the application would look the service up against a different class of the"
                            + " same name and find no provider; declare @jenesis.layer " + layer + " shared "
                            + declaring + " to resolve it from the parent");
                }
            }
        }
        SequencedSet<String> seam = new LinkedHashSet<>();
        for (ModuleDescriptor descriptor : isolated.values()) {
            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                if (!platform(requires.name()) && !isolated.containsKey(requires.name())) {
                    seam.add(requires.name());
                }
            }
        }
        for (String module : seam) {
            List<String> chain = reaches(module, isolated.sequencedKeySet(), parent);
            if (chain != null) {
                throw new IllegalStateException("module " + module + " is shared with layer " + layer
                        + " but reaches " + chain.getLast() + ", which layer " + layer + " isolates ("
                        + String.join(" -> ", chain) + ") - declare @jenesis.layer " + layer + " shared "
                        + chain.getLast() + " to share it, or keep it out of the layer");
            }
        }
    }

    private static List<String> reaches(String module,
                                        SequencedSet<String> isolated,
                                        SequencedMap<String, ModuleDescriptor> parent) {
        SequencedMap<String, String> predecessors = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>();
        predecessors.put(module, null);
        pending.addLast(module);
        while (!pending.isEmpty()) {
            ModuleDescriptor descriptor = parent.get(pending.removeFirst());
            if (descriptor == null) {
                continue;
            }
            for (ModuleDescriptor.Requires requires : descriptor.requires()) {
                String required = requires.name();
                if (platform(required) || predecessors.containsKey(required)) {
                    continue;
                }
                predecessors.put(required, descriptor.name());
                if (isolated.contains(required)) {
                    List<String> chain = new ArrayList<>();
                    for (String step = required; step != null; step = predecessors.get(step)) {
                        chain.addFirst(step);
                    }
                    return chain;
                }
                pending.addLast(required);
            }
        }
        return null;
    }

    private static String declaring(String contract, SequencedMap<String, ModuleDescriptor> isolated) {
        int last = contract.lastIndexOf('.');
        if (last < 1) {
            return null;
        }
        String contractPackage = contract.substring(0, last);
        for (Map.Entry<String, ModuleDescriptor> entry : isolated.entrySet()) {
            if (entry.getValue().packages().contains(contractPackage)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static boolean platform(String module) {
        return module.startsWith("java.") || module.startsWith("jdk.");
    }
}
