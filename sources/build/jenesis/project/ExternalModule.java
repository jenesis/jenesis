package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;

public class ExternalModule implements BuildExecutorModule {

    public static final String COORDINATE = "coordinate", DEPENDENCIES = "dependencies", DELEGATE = "delegate",
            INPUTS = "inputs";

    private final String coordinate;
    private final Dependencies dependencyModule;
    private final SequencedSet<String> additionalDependencies;
    private final String buildModuleName;
    private final Pinning pinning;
    private final String group;
    private final SequencedMap<String, String> properties;
    private final SequencedMap<String, Bind.Input> inputs;

    public ExternalModule(String coordinate,
                          String group,
                          Map<String, Repository> repositories,
                          Map<String, Resolver> resolvers) {
        this(coordinate,
                new Dependencies(repositories, resolvers),
                Collections.emptyNavigableSet(),
                null,
                null,
                group == null ? "main" : group,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }

    public static ExternalModule ofEnvironment(Environment environment,
                                               String coordinate,
                                               String group,
                                               Map<String, Repository> repositories,
                                               Map<String, Resolver> resolvers) {
        return new ExternalModule(coordinate,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                Collections.emptyNavigableSet(),
                null,
                null,
                group == null ? "main" : group,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }

    private ExternalModule(String coordinate,
                           Dependencies dependencyModule,
                           SequencedSet<String> additionalDependencies,
                           String buildModuleName,
                           Pinning pinning,
                           String group,
                           SequencedMap<String, String> properties,
                           SequencedMap<String, Bind.Input> inputs) {
        this.coordinate = coordinate;
        this.dependencyModule = dependencyModule;
        this.additionalDependencies = additionalDependencies;
        this.buildModuleName = buildModuleName;
        this.pinning = pinning;
        this.group = group;
        this.properties = properties;
        this.inputs = inputs;
    }

    public ExternalModule dependencies(String... dependencies) {
        return dependencies(new LinkedHashSet<>(List.of(dependencies)));
    }

    public ExternalModule dependencies(SequencedSet<String> dependencies) {
        return new ExternalModule(coordinate,
                dependencyModule,
                dependencies,
                buildModuleName,
                pinning,
                group,
                properties,
                inputs);
    }

    public ExternalModule buildModuleName(String name) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                name,
                pinning,
                group,
                properties,
                inputs);
    }

    public ExternalModule pinning(Pinning pinning) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                properties,
                inputs);
    }

    public ExternalModule group(String group) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                properties,
                inputs);
    }

    public ExternalModule properties(SequencedMap<String, String> properties) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                properties,
                inputs);
    }

    public ExternalModule inputs(SequencedMap<String, Bind.Input> inputs) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                properties,
                inputs);
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.equals(DELEGATE)) {
            return Optional.of("");
        }
        if (path.startsWith(DELEGATE + "/")) {
            return Optional.of(path.substring(DELEGATE.length() + 1));
        }
        if (path.equals(DEPENDENCIES)) {
            return Optional.of(path);
        }
        return Optional.empty();
    }

    public BuildExecutorModule resolution() {
        return (buildExecutor, inherited) -> {
            List<String> coordinates = new ArrayList<>(additionalDependencies.size() + 1);
            coordinates.add(coordinate);
            coordinates.addAll(additionalDependencies);
            buildExecutor.addStep(COORDINATE,
                    new WriteCoordinates(group, coordinates),
                    inherited.sequencedKeySet().stream());
            buildExecutor.addModule(DEPENDENCIES,
                    dependencyModule.pinning(pinning),
                    COORDINATE);
        };
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        resolution().accept(buildExecutor, inherited);
        if (!inputs.isEmpty()) {
            buildExecutor.addModule(INPUTS, (bound, _) -> inputs.forEach((name, input) -> bound.addSource(name,
                    new Bind(Map.of(Path.of(""), input.target())),
                    input.path())));
        }
        buildExecutor.addModule(DELEGATE, (delegateExecutor, delegated) -> {
            List<Path> artifacts = new ArrayList<>(
                    Dependencies.select(delegated.get(PREVIOUS + DEPENDENCIES), group, "runtime"));
            artifacts.sort(null);
            JenesisClassLoaderBridge bridge;
            Object foreignModule;
            try {
                bridge = new JenesisClassLoaderBridge(artifacts);
                foreignModule = bridge.findProvider(buildModuleName, properties);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve external build execution module " + coordinate, e);
            }
            SequencedMap<String, Path> forwarded = new LinkedHashMap<>(delegated);
            forwarded.remove(PREVIOUS + DEPENDENCIES);
            bridge.accept(foreignModule, delegateExecutor, forwarded);
        }, Stream.of(Stream.of(DEPENDENCIES), inputs.isEmpty() ? Stream.<String>empty() : Stream.of(INPUTS), inherited.sequencedKeySet().stream())
                .flatMap(Function.identity()));
    }

    private record WriteCoordinates(String group, List<String> coordinates) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties properties = new SequencedProperties();
            for (String coordinate : coordinates) {
                properties.setProperty(group + "/runtime/" + coordinate, "");
            }
            properties.store(context.next().resolve(BuildStep.REQUIRES));
            SequencedProperties versions = new SequencedProperties();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(BuildStep.VERSIONS);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties.ofFiles(file).forEachProperty(versions::putIfAbsent);
            }
            if (!versions.isEmpty()) {
                versions.store(context.next().resolve(BuildStep.VERSIONS));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
