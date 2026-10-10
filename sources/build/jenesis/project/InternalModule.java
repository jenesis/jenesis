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
import build.jenesis.Platform;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.module.ModuleInfo;
import build.jenesis.module.ModuleInfoParser;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessHandler;

public class InternalModule implements BuildExecutorModule {

    public static final String SOURCE = "source",
            JAVA = "java",
            DELEGATE = "delegate",
            INPUTS = "inputs";
    private static final String DEPENDENCIES = "dependencies", REQUIRES = "requires";
    private static final String MAIN_ARTIFACTS = JAVA + "/" + JavaToolchainModule.ARTIFACTS;

    private final String prefix;
    private final Path source;
    private final Dependencies dependencyModule;
    private final Javac javacStep;
    private final SequencedSet<String> additionalDependencies;
    private final String buildModuleName;
    private final Pinning pinning;
    private final String group;
    private final Platform platform;
    private final SequencedMap<String, String> properties;
    private final SequencedMap<String, SequencedMap<Path, Path>> inputs;

    public InternalModule(String prefix, String group, Path source) {
        this(prefix,
                source,
                aliased(prefix, JenesisModuleRepository.of(JenesisRepository.Scope.MODULE),
                        MavenDefaultRepository.ofEnvironment(Environment.NONE)),
                aliased(prefix, new ModularJarResolver(true), new MavenPomResolver()),
                group == null ? "main" : group);
    }

    private InternalModule(String prefix,
                           Path source,
                           Map<String, Repository> repositories,
                           Map<String, Resolver> resolvers,
                           String group) {
        this(prefix,
                source,
                new Dependencies(repositories, resolvers),
                new Javac(ProcessHandler.Factory.of()),
                Collections.emptyNavigableSet(),
                null,
                null,
                group,
                new Platform(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }

    public static InternalModule ofEnvironment(Environment environment,
                                               String prefix,
                                               String group,
                                               Path source) {
        Map<String, Repository> repositories = aliased(prefix,
                JenesisRepository.ofEnvironment(environment, JenesisRepository.Scope.MODULE),
                MavenDefaultRepository.ofEnvironment(environment));
        Map<String, Resolver> resolvers = aliased(prefix,
                ModularJarResolver.ofEnvironment(environment, true),
                MavenPomResolver.ofEnvironment(environment));
        return new InternalModule(prefix,
                source,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                Javac.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment)),
                Collections.emptyNavigableSet(),
                null,
                null,
                group == null ? "main" : group,
                Platform.ofEnvironment(environment),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }

    private static <T> Map<String, T> aliased(String prefix, T module, T maven) {
        SequencedMap<String, T> resolution = new LinkedHashMap<>();
        resolution.put(prefix, module);
        resolution.putIfAbsent("maven", maven);
        return resolution;
    }

    public InternalModule repositories(Map<String, Repository> repositories) {
        return new InternalModule(prefix,
                source,
                dependencyModule.repositories(repositories),
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule resolvers(Map<String, Resolver> resolvers) {
        return new InternalModule(prefix,
                source,
                dependencyModule.resolvers(resolvers),
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule group(String group) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    private InternalModule(String prefix,
                           Path source,
                           Dependencies dependencyModule,
                           Javac javacStep,
                           SequencedSet<String> additionalDependencies,
                           String buildModuleName,
                           Pinning pinning,
                           String group,
                           Platform platform,
                           SequencedMap<String, String> properties,
                           SequencedMap<String, SequencedMap<Path, Path>> inputs) {
        this.prefix = prefix;
        this.source = source;
        this.dependencyModule = dependencyModule;
        this.javacStep = javacStep;
        this.additionalDependencies = additionalDependencies;
        this.buildModuleName = buildModuleName;
        this.pinning = pinning;
        this.group = group;
        this.platform = platform;
        this.properties = properties;
        this.inputs = inputs;
    }

    public InternalModule dependencies(String... dependencies) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                new LinkedHashSet<>(List.of(dependencies)),
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule dependencies(SequencedSet<String> dependencies) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                new LinkedHashSet<>(dependencies),
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule buildModuleName(String name) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                name,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule platform(Platform platform) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule pinning(Pinning pinning) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule properties(SequencedMap<String, String> properties) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
                properties,
                inputs);
    }

    public InternalModule inputs(SequencedMap<String, SequencedMap<Path, Path>> inputs) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                pinning,
                group,
                platform,
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
            buildExecutor.addSource(SOURCE, Bind.asSources(), source);
            buildExecutor.addStep(REQUIRES,
                    new ParseModuleInfo(group, prefix, additionalDependencies, platform),
                    Stream.concat(Stream.of(SOURCE), inherited.sequencedKeySet().stream()));
            buildExecutor.addModule(DEPENDENCIES,
                    dependencyModule.pinning(pinning),
                    REQUIRES);
        };
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        resolution().accept(buildExecutor, inherited);
        buildExecutor.addModule(JAVA,
                new JavaToolchainModule().compiler(javacStep.group(group).asModule("javac")),
                SOURCE,
                DEPENDENCIES);
        if (!inputs.isEmpty()) {
            buildExecutor.addModule(INPUTS, Bind.asInputs(inputs));
        }
        buildExecutor.addModule(DELEGATE, (delegateExecutor, delegated) -> {
            Path mainArtifacts = delegated.get(PREVIOUS + MAIN_ARTIFACTS).resolve(BuildStep.ARTIFACTS);
            List<Path> artifacts = new ArrayList<>();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(mainArtifacts)) {
                for (Path file : files) {
                    artifacts.add(file);
                }
            }
            artifacts.addAll(Dependencies.select(delegated.get(PREVIOUS + DEPENDENCIES), group, "runtime"));
            artifacts.sort(null);
            JenesisClassLoaderBridge bridge;
            Object foreignModule;
            try {
                bridge = new JenesisClassLoaderBridge(artifacts);
                foreignModule = bridge.findProvider(buildModuleName, properties);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to resolve internal build execution module " + source, e);
            }
            SequencedMap<String, Path> forwarded = new LinkedHashMap<>(delegated);
            forwarded.remove(PREVIOUS + MAIN_ARTIFACTS);
            forwarded.remove(PREVIOUS + DEPENDENCIES);
            bridge.accept(foreignModule, delegateExecutor, forwarded);
        }, Stream.of(Stream.of(MAIN_ARTIFACTS, DEPENDENCIES), inputs.isEmpty() ? Stream.<String>empty() : Stream.of(INPUTS), inherited.sequencedKeySet().stream())
                .flatMap(Function.identity()));
    }

    private record ParseModuleInfo(String group,
                                   String prefix,
                                   SequencedSet<String> additionalDependencies,
                                   Platform platform) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            if (arguments.get(SOURCE).hasChanged(Path.of(BuildStep.SOURCES + "module-info.java"))) {
                return true;
            }
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (!argument.getKey().equals(SOURCE) && argument.getValue().hasChanged(Path.of(BuildStep.VERSIONS))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path moduleInfo = arguments.get(SOURCE).folder()
                    .resolve(BuildStep.SOURCES)
                    .resolve("module-info.java");
            if (!Files.isRegularFile(moduleInfo)) {
                throw new IllegalStateException(
                        "Internal module source is not modular (missing module-info.java)");
            }
            ModuleInfo info = new ModuleInfoParser(group).identify(moduleInfo);
            SequencedMap<String, String> pinned = new LinkedHashMap<>(info.versions());
            for (Map.Entry<String, SequencedMap<String, String>> variant : info.variants().entrySet()) {
                String selected = platform.select(variant.getKey(),
                        pinned.get(variant.getKey()),
                        variant.getValue());
                if (selected != null) {
                    pinned.put(variant.getKey(), selected);
                }
            }
            SequencedMap<String, String> coordinates = new LinkedHashMap<>();
            for (String dependency : info.requires()) {
                String target = info.aliases().get(dependency);
                if (target == null) {
                    coordinates.put(dependency, prefix + "/" + dependency);
                } else {
                    String version = pinned.getOrDefault(group + "/maven/" + target, "").split(" ", 2)[0];
                    coordinates.put(dependency, "maven/" + target
                            + (version.isEmpty() || version.startsWith(":") ? "" : "/" + version));
                }
            }
            SequencedProperties properties = new SequencedProperties();
            coordinates.forEach((dependency, coordinate) -> {
                properties.setProperty(group + "/compile/" + coordinate, "");
                if (info.runtimeRequires().contains(dependency)) {
                    properties.setProperty(group + "/runtime/" + coordinate, "");
                }
            });
            for (String dependency : additionalDependencies) {
                properties.setProperty(group + "/compile/" + dependency, "");
                properties.setProperty(group + "/runtime/" + dependency, "");
            }
            info.plugins().forEach((coordinate, group) -> properties.setProperty(group + "/plugin/" + coordinate, ""));
            properties.store(context.next().resolve(BuildStep.REQUIRES));
            if (!info.aliases().isEmpty()) {
                SequencedProperties aliases = new SequencedProperties();
                info.aliases().forEach((alias, target) -> aliases.setProperty(group + "/" + prefix + "/" + alias, target));
                aliases.store(context.next().resolve(BuildStep.ALIASES));
            }
            if (!info.excludes().isEmpty()) {
                SequencedProperties exclusions = new SequencedProperties();
                for (Map.Entry<String, SequencedSet<String>> entry : info.excludes().entrySet()) {
                    String coordinate = coordinates.get(entry.getKey());
                    if (coordinate == null) {
                        throw new IllegalArgumentException("Cannot apply @jenesis.exclude to " + entry.getKey()
                                + ", which the plugin " + info.coordinate() + " does not require (declared requires: "
                                + info.requires() + ")");
                    }
                    exclusions.setProperty(group + "/compile/" + coordinate, String.join(",", entry.getValue()));
                    if (info.runtimeRequires().contains(entry.getKey())) {
                        exclusions.setProperty(group + "/runtime/" + coordinate, String.join(",", entry.getValue()));
                    }
                }
                exclusions.store(context.next().resolve(BuildStep.EXCLUSIONS));
            }
            SequencedProperties versions = new SequencedProperties();
            pinned.forEach(versions::setProperty);
            pinnedVersions(arguments).forEachProperty(versions::putIfAbsent);
            if (!versions.isEmpty()) {
                versions.store(context.next().resolve(BuildStep.VERSIONS));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static SequencedProperties pinnedVersions(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedProperties versions = new SequencedProperties();
        for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
            if (argument.getValue().removed()) {
                continue;
            }
            if (argument.getKey().equals(SOURCE)) {
                continue;
            }
            Path file = argument.getValue().folder().resolve(BuildStep.VERSIONS);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties.ofFiles(file).forEachProperty(versions::putIfAbsent);
        }
        return versions;
    }
}
