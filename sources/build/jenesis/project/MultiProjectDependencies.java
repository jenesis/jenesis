package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class MultiProjectDependencies implements BuildStep {

    private final Predicate<String> isModule;

    public <P extends Predicate<String> & Serializable> MultiProjectDependencies(P isModule) {
        this.isModule = isModule;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(REQUIRES),
                Path.of(VERSIONS),
                Path.of(ALIASES),
                Path.of(BOMS),
                Path.of(EXCLUSIONS),
                Path.of(IDENTITY),
                Path.of(ARTIFACTS)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, Path> coordinates = new LinkedHashMap<>();
        SequencedMap<String, String> dependencies = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, String>> inherited = new LinkedHashMap<>();
        for (String name : List.of(VERSIONS, ALIASES, BOMS, EXCLUSIONS)) {
            inherited.put(name, new LinkedHashMap<>());
        }
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().folder() == null) {
                continue;
            }
            if (isModule.test(entry.getKey())) {
                Path requiresPath = entry.getValue().folder().resolve(REQUIRES);
                if (Files.exists(requiresPath)) {
                    SequencedProperties.ofFiles(requiresPath).forEachProperty(dependencies::put);
                }
                for (Map.Entry<String, SequencedMap<String, String>> merged : inherited.entrySet()) {
                    Path file = entry.getValue().folder().resolve(merged.getKey());
                    if (Files.exists(file)) {
                        SequencedProperties.ofFiles(file).forEachProperty(merged.getValue()::putIfAbsent);
                    }
                }
            } else {
                Path file = entry.getValue().folder().resolve(IDENTITY);
                if (Files.exists(file)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(file);
                    Path folder = entry.getValue().folder();
                    for (String property : properties.stringPropertyNames()) {
                        String value = properties.getProperty(property);
                        if (!value.isEmpty()) {
                            coordinates.put(property, folder.resolve(value).normalize());
                        }
                    }
                }
            }
        }
        SequencedProperties properties = new SequencedProperties();
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String key = entry.getKey();
            int second = key.indexOf('/', key.indexOf('/') + 1);
            Path candidate = coordinates.get(key.substring(second + 1));
            properties.setProperty(entry.getKey(),
                    candidate != null
                            ? fingerprint(arguments.values(), candidate)
                            : entry.getValue());
        }
        properties.store(context.next().resolve(REQUIRES));
        for (Map.Entry<String, SequencedMap<String, String>> merged : inherited.entrySet()) {
            if (!merged.getValue().isEmpty()) {
                SequencedProperties mergedProperties = new SequencedProperties();
                merged.getValue().forEach(mergedProperties::setProperty);
                mergedProperties.store(context.next().resolve(merged.getKey()));
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String fingerprint(Collection<BuildStepArgument> arguments, Path artifact) {
        for (BuildStepArgument argument : arguments) {
            if (argument.folder() == null) {
                continue;
            }
            if (artifact.startsWith(argument.folder())) {
                String checksum = argument.checksum(argument.folder().relativize(artifact));
                if (checksum != null) {
                    return checksum;
                }
            }
        }
        throw new IllegalStateException("No tracked checksum for sibling artifact: " + artifact);
    }
}
