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
                      SequencedSet<String> groups,
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
        Map<String, String> covered = new HashMap<>();
        for (Path other : provided) {
            if (!Files.isRegularFile(other)) {
                throw new IllegalStateException("No such file in jenesis.pin.provided: " + other);
            }
            SequencedProperties.ofFiles(other).forEachProperty((key, value) -> covered.putIfAbsent(key, value.trim()));
        }
        Set<String> internal = new LinkedHashSet<>();
        for (String identity : Inventory.identities(arguments.values())) {
            internal.add(identity);
            int first = identity.indexOf('/'), last = identity.lastIndexOf('/');
            if (first > 0 && last > first) {
                internal.add(identity.substring(0, last));
            }
        }
        SequencedMap<String, String> entries = new TreeMap<>();
        Map<String, String> owners = new HashMap<>();
        SequencedSet<String> conflicts = new TreeSet<>();
        for (String path : paths) {
            String module = path.isEmpty() ? "." : path;
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
                    String name = coordinate.substring("module/".length());
                    int dash = name.indexOf('-');
                    if (dash >= 0) {
                        value = ":" + name.substring(dash + 1) + ":" + value;
                        name = name.substring(0, dash);
                    }
                    entry = name;
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
                if (value.equals(covered.get(entry))) {
                    continue;
                }
                String owner = module + " (" + group + ")";
                String previous = entries.putIfAbsent(entry, value);
                if (previous == null) {
                    owners.put(entry, owner);
                } else if (!previous.equals(value)) {
                    conflicts.add(entry + " " + previous + " in " + owners.get(entry) + ", " + value + " in " + owner);
                }
            }
            for (Map.Entry<String, Inventory.Dependency> pom : Inventory.poms(arguments.values(), path).entrySet()) {
                String group = pom.getValue().group();
                if (!groups.contains(group)) {
                    continue;
                }
                String coordinate = pom.getKey().substring(group.length() + 1);
                String value = coordinate.substring(coordinate.lastIndexOf('/') + 1)
                        + " " + pom.getValue().checksum(hashFunction);
                if (!value.equals(covered.get(coordinate))) {
                    entries.putIfAbsent(coordinate, value);
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("Cannot write "
                    + file
                    + ": the groups "
                    + String.join(", ", groups)
                    + " resolve "
                    + String.join("; ", conflicts)
                    + " - a file holds one version of a coordinate, so pin the version every module is to resolve"
                    + " in the file, or name fewer groups in jenesis.pin.groups");
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
