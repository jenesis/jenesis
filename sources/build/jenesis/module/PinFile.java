package build.jenesis.module;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;

public record PinFile(SequencedSet<String> paths,
                      Path file,
                      SequencedSet<Path> provided,
                      HashDigestFunction hashFunction)
        implements BuildStep {

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> entries = new TreeMap<>();
        Map<String, String> owners = new HashMap<>();
        SequencedSet<String> conflicts = new TreeSet<>();
        String name = file.getFileName().toString();
        SequencedMap<String, Set<String>> importers = new LinkedHashMap<>();
        for (String path : paths) {
            for (String imported : Inventory.bomImports(arguments.values(), path)) {
                int slash = imported.indexOf('/');
                if (imported.substring(slash + 1).equals(name)) {
                    importers.computeIfAbsent(path, _ -> new HashSet<>()).add(imported.substring(0, slash));
                }
            }
        }
        for (String path : paths) {
            Set<String> groups = importers.isEmpty() ? Set.of("main") : importers.getOrDefault(path, Set.of());
            if (groups.isEmpty()) {
                continue;
            }
            Set<String> internal = new LinkedHashSet<>();
            for (String identity : Inventory.identities(arguments.values())) {
                internal.add(identity);
                int first = identity.indexOf('/'), last = identity.lastIndexOf('/');
                if (first > 0 && last > first) {
                    internal.add(identity.substring(0, last));
                }
            }
            for (Map.Entry<String, Inventory.Dependency> dependency
                    : Inventory.closure(arguments.values(), path).entrySet()) {
                String group = dependency.getValue().group();
                String key = dependency.getKey().substring(group.length() + 1);
                if (!groups.contains(group) || internal.contains(key)) {
                    continue;
                }
                int lastSlash = key.lastIndexOf('/'), firstSlash = key.indexOf('/');
                if (lastSlash <= 0 || lastSlash == firstSlash) {
                    continue;
                }
                String coordinate = key.substring(0, lastSlash);
                String value = key.substring(lastSlash + 1);
                Inventory.Dependency resolved = dependency.getValue();
                String checksum = resolved.jar() != null && Files.isRegularFile(resolved.jar())
                        ? hashFunction.encodedHash(resolved.jar())
                        : resolved.checksum();
                if (checksum != null && !checksum.isEmpty()) {
                    value += " " + checksum;
                }
                String entry;
                if (coordinate.startsWith("module/")) {
                    String module = coordinate.substring("module/".length());
                    int dash = module.indexOf('-');
                    if (dash >= 0) {
                        value = ":" + module.substring(dash + 1) + ":" + value;
                        module = module.substring(0, dash);
                    }
                    entry = module;
                } else {
                    String maven = coordinate.startsWith("maven/")
                            ? coordinate.substring("maven/".length())
                            : null;
                    entry = maven != null
                            && maven.indexOf('/') > 0
                            && maven.indexOf('/') == maven.lastIndexOf('/')
                            ? maven
                            : coordinate;
                }
                String owner = (path.isEmpty() ? "." : path) + " (" + group + ")";
                String previous = entries.putIfAbsent(entry, value);
                if (previous == null) {
                    owners.put(entry, owner);
                } else if (!previous.equals(value)) {
                    conflicts.add(entry + " " + previous + " in " + owners.get(entry) + ", " + value + " in " + owner);
                }
            }
            for (Map.Entry<String, Inventory.Dependency> pom : Inventory.poms(arguments.values(), path).entrySet()) {
                if (groups.contains(pom.getValue().group())) {
                    String coordinate = pom.getKey().substring("main/".length());
                    entries.putIfAbsent(coordinate, coordinate.substring(coordinate.lastIndexOf('/') + 1)
                            + " " + pom.getValue().checksum(hashFunction));
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Cannot write "
                    + file
                    + ": the modules importing it resolve "
                    + String.join("; ", conflicts)
                    + " - a file holds one version of a coordinate, so pin the version every importer is to"
                    + " resolve in the file, or import a file of its own where a module needs another");
        }
        for (Path other : provided) {
            if (!Files.isRegularFile(other)) {
                throw new IllegalStateException("No such file in jenesis.pin.provided: " + other);
            }
            SequencedProperties covered = SequencedProperties.ofFiles(other);
            for (String key : covered.stringPropertyNames()) {
                String value = covered.getProperty(key).trim();
                int space = value.indexOf(' ');
                if (space < 0) {
                    continue;
                }
                if (value.equals(entries.get(key))) {
                    entries.remove(key);
                }
            }
        }
        SequencedProperties pins = new SequencedProperties();
        entries.forEach(pins::setProperty);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        pins.store(file);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
