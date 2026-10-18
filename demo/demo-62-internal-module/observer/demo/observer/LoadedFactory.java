package demo.observer;

import module build.jenesis;
import module java.base;

public class LoadedFactory implements ObservabilityFactory {

    @Override
    public Observation create(Environment environment,
                              SequencedMap<String, String> properties,
                              Permits permits,
                              String group,
                              Map<String, Repository> repositories,
                              Map<String, Resolver> resolvers) {
        for (String key : properties.sequencedKeySet()) {
            if (!key.equals("package")) {
                throw new IllegalArgumentException("The loaded plugin takes no value " + key
                        + ": name package, the package whose classes it reports");
            }
        }
        String prefix = properties.getOrDefault("package", "") + ".";
        return new Observation(new ClassLoading(group),
                (executor, inherited) -> executor.addStep("loaded", new Loaded(prefix), inherited.sequencedKeySet()));
    }

    public record ClassLoading(String name) implements ObservabilityEngine {

        @Override
        public SequencedMap<String, String> coordinates() {
            return new LinkedHashMap<>();
        }

        @Override
        public List<String> commands(SequencedMap<String, Path> resolved, Path output) {
            return List.of("-Xlog:class+load=info:file=\"" + output.resolve("class-load.log").toAbsolutePath() + "\"");
        }
    }

    public record Loaded(String prefix) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SortedSet<String> loaded = new TreeSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                Path log = argument.folder().resolve("class-load.log");
                if (Files.isRegularFile(log)) {
                    for (String line : Files.readAllLines(log)) {
                        int start = line.indexOf("] "), end = line.indexOf(" source: ");
                        if (start != -1 && end > start && line.substring(start + 2, end).startsWith(prefix)) {
                            loaded.add(line.substring(start + 2, end));
                        }
                    }
                }
            }
            Path report = Files.createDirectories(context.next().resolve(BuildStep.REPORTS + "loaded"))
                    .resolve("loaded.txt");
            Files.write(report, loaded);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
