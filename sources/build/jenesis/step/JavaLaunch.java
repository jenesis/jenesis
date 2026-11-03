package build.jenesis.step;

import module java.base;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public record JavaLaunch(List<String> options,
                         List<String> attached,
                         SequencedMap<String, String> paths,
                         List<String> relaxations,
                         List<String> main) {

    public static JavaLaunch of(SequencedProperties inventory, Path folder, String prefix, String mainClass)
            throws IOException {
        String path = prefix.equals("module") ? "<root>" : prefix.substring("module-".length()),
                module = inventory.getProperty(prefix + ".module");
        List<Path> runtime = Inventory.paths(inventory, folder, prefix + ".runtime");
        if (runtime.isEmpty()) {
            throw new IllegalStateException("No runtime artifacts for module: " + path);
        }
        List<String> jars = new ArrayList<>();
        for (Path resolved : runtime) {
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalStateException("Missing runtime artifact for module " + path + ": " + resolved);
            }
            jars.add(resolved.toString());
        }
        List<String> options = new ArrayList<>(), attached = new ArrayList<>();
        for (Path file : Inventory.paths(inventory, folder, prefix + ".process")) {
            SequencedProperties.ofFiles(file).forEachProperty((option, values) -> {
                for (String value : values.split("\n")) {
                    options.add(option);
                    if (!value.isEmpty()) {
                        options.addAll(List.of(value.split("\t")));
                    }
                }
            });
        }
        for (int index = 0; ; index++) {
            String agent = inventory.getProperty(prefix + ".agent." + index);
            if (agent == null) {
                break;
            }
            String coordinate = inventory.getProperty(prefix + ".agent." + index + ".coordinate");
            Path jar = folder.resolve(agent).normalize();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Missing agent artifact " + coordinate + ": " + jar);
            }
            try (JarFile file = new JarFile(jar.toFile())) {
                Manifest manifest = file.getManifest();
                if (manifest == null || manifest.getMainAttributes().getValue("Premain-Class") == null) {
                    throw new IllegalStateException("Attached agent "
                            + coordinate
                            + " does not declare Premain-Class: "
                            + jar);
                }
            }
            String arguments = inventory.getProperty(prefix + ".agent." + index + ".arguments");
            attached.add("-javaagent:" + jar.toAbsolutePath() + (arguments == null ? "" : "=" + arguments));
        }
        List<Path> granted = Inventory.paths(inventory, folder, prefix + ".nativeAccess");
        ModuleGraph graph = new ModuleGraph();
        String layered = inventory.getProperty(prefix + ".layers");
        SequencedMap<String, Layers.Membership> layers = layered == null
                ? Collections.emptyNavigableMap()
                : Layers.membership(folder.resolve(layered).getParent());
        if (!layers.isEmpty()) {
            SequencedMap<String, Path> resolved = new LinkedHashMap<>();
            for (int index = 0; ; index++) {
                String dependency = inventory.getProperty(prefix + ".dependency." + index);
                if (dependency == null) {
                    break;
                }
                int space = dependency.indexOf(' '), end = dependency.indexOf(' ', space + 1);
                Path jar = folder.resolve(dependency.substring(space + 1, end < 0 ? dependency.length() : end))
                        .toAbsolutePath()
                        .normalize();
                resolved.putIfAbsent(jar.getFileName().toString(), jar);
            }
            Set<Path> absolute = granted.stream().map(file -> file.toAbsolutePath().normalize()).collect(Collectors.toSet());
            for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
                for (String name : layer.getValue().all()) {
                    if (!resolved.containsKey(name)) {
                        throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                                + ", which was not resolved for this application");
                    }
                }
                attached.add("-Djlayer.modulepath." + layer.getKey() + "=" + layer.getValue().modulepath().stream()
                        .map(name -> resolved.get(name).toString())
                        .collect(Collectors.joining(File.pathSeparator)));
                if (!layer.getValue().classpath().isEmpty()) {
                    attached.add("-Djlayer.classpath." + layer.getKey() + "=" + layer.getValue().classpath().stream()
                            .map(name -> resolved.get(name).toString())
                            .collect(Collectors.joining(File.pathSeparator)));
                    graph.unnamed();
                }
                layer.getValue().nativeAccess(resolved, absolute)
                        .forEach((jar, name) -> graph.enableNativeAccess(layer.getKey(), jar, name));
            }
        }
        SequencedMap<String, String> paths = new LinkedHashMap<>();
        List<String> main = new ArrayList<>();
        if (module != null) {
            List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
            for (String jar : jars) {
                Path file = Path.of(jar);
                boolean placed = graph.place(PathPlacement.INFERRED, file);
                (placed ? modulePath : classPath).add(jar);
                if (granted.contains(file)) {
                    graph.enableNativeAccess(file, placed);
                }
            }
            paths.put("--module-path", String.join(File.pathSeparator, modulePath));
            paths.put("--class-path", String.join(File.pathSeparator, classPath));
            if (mainClass != null) {
                main.add("-m");
                main.add(module + "/" + mainClass);
            }
        } else {
            for (String jar : jars) {
                graph.place(PathPlacement.CLASS_PATH, Path.of(jar));
            }
            for (Path file : granted) {
                graph.enableNativeAccess(file, false);
            }
            paths.put("-cp", String.join(File.pathSeparator, jars));
            if (mainClass != null) {
                main.add(mainClass);
            }
        }
        return new JavaLaunch(options, attached, paths, graph.arguments(), main);
    }

    public List<String> arguments() {
        List<String> arguments = new ArrayList<>(options);
        arguments.addAll(attached);
        paths.forEach((option, value) -> {
            if (!value.isEmpty()) {
                arguments.add(option);
                arguments.add(value);
            }
        });
        arguments.addAll(relaxations);
        arguments.addAll(main);
        return arguments;
    }
}
