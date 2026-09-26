package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Bundle;
import build.jenesis.step.Docker;
import build.jenesis.step.Inventory;
import build.jenesis.step.JLink;
import build.jenesis.step.JMod;
import build.jenesis.step.JPackage;
import build.jenesis.step.Jar;
import build.jenesis.step.Layers;
import build.jenesis.step.Legal;
import build.jenesis.step.NativeImage;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Sbom;
import build.jenesis.step.Versions;

public record InferredMultiProjectAssembler(Function<InferredSourceCodeQualityModule, BuildExecutorModule> check,
                                            Function<InferredSourceFormattingModule, BuildExecutorModule> format,
                                            Function<InferredComplianceModule, BuildExecutorModule> compliance,
                                            Function<InferredJavaToolchainModule, BuildExecutorModule> toolchain,
                                            Function<InferredArtifactQualityModule, BuildExecutorModule> artifact,
                                            Function<InferredTestObservationModule, BuildExecutorModule> observe,
                                            Function<InferredDocumentationModule, BuildExecutorModule> documentation,
                                            SequencedMap<String, BuildExecutorModule> custom,
                                            SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins,
                                            SequencedMap<Path, Path> resources,
                                            Environment environment) implements MultiProjectAssembler<ProjectModuleDescriptor> {

    private static final List<String> HOOK_POINTS = List.of("",
            "check",
            "format",
            "compliance",
            "binary",
            "binary/generated",
            "binary/compiled",
            "binary/validate",
            "artifact",
            "observed",
            "package",
            "documentation",
            "documentation/generate");

    public InferredMultiProjectAssembler() {
        this(Environment.NONE);
    }

    private InferredMultiProjectAssembler(Environment environment) {
        this(module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                environment);
    }

    public static InferredMultiProjectAssembler ofEnvironment(Environment environment) {
        return new InferredMultiProjectAssembler(environment);
    }

    public InferredMultiProjectAssembler check(Function<InferredSourceCodeQualityModule, BuildExecutorModule> check) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler format(Function<InferredSourceFormattingModule, BuildExecutorModule> format) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler compliance(Function<InferredComplianceModule, BuildExecutorModule> compliance) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler toolchain(Function<InferredJavaToolchainModule, BuildExecutorModule> toolchain) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler artifact(Function<InferredArtifactQualityModule, BuildExecutorModule> artifact) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler observe(Function<InferredTestObservationModule, BuildExecutorModule> observe) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler documentation(Function<InferredDocumentationModule, BuildExecutorModule> documentation) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler plugins(SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins) {
        for (String key : plugins.keySet()) {
            int plus = key.indexOf('+');
            String name = plus == -1 ? key : key.substring(0, plus);
            if (name.isEmpty() || name.contains("/") || plus != -1 && !HOOK_POINTS.contains(key.substring(plus + 1))
                    || plus == key.length() - 1) {
                throw new IllegalArgumentException("Cannot add the plugin " + key + " - name a plugin as <name>+<hook point>"
                        + " with a hook point of " + HOOK_POINTS.stream().filter(hook -> !hook.isEmpty()).toList()
                        + ", or as <name> alone for the module build, where the name holds no / or +");
            }
        }
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler resources(SequencedMap<Path, Path> resources) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredMultiProjectAssembler custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                    Map<String, Repository> repositories,
                                    Map<String, Resolver> resolvers) throws IOException {
        Packaging packaging = Packaging.configured(
                BuildStep.locate(descriptor.configuration(), "packaging.properties"));
        Boolean modules = descriptor.pathPlacement() == PathPlacement.MODULE_PATH
                ? null
                : ModularizeModule.configured(BuildStep.locate(descriptor.configuration(), "modules.properties"));
        SequencedMap<String, SequencedMap<String, String>> overrides = overridesOf(descriptor.configuration());
        ProcessHandler.Factory factory = ProcessHandler.Factory.ofEnvironment(environment);
        SequencedMap<String, SequencedMap<String, BuildExecutorModule>> hooks = new LinkedHashMap<>();
        hooks.put("", new LinkedHashMap<>(custom));
        for (Map.Entry<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugin : plugins.entrySet()) {
            int plus = plugin.getKey().indexOf('+');
            String name = plus == -1 ? plugin.getKey() : plugin.getKey().substring(0, plus);
            Path file = BuildStep.locate(descriptor.configuration(), "plugin-" + name + ".properties");
            if (file == null) {
                continue;
            }
            SequencedMap<String, String> properties = new LinkedHashMap<>();
            SequencedProperties.ofFiles(file).forEachProperty(properties::put);
            SequencedMap<String, BuildExecutorModule> hooked = hooks.computeIfAbsent(plus == -1
                    ? ""
                    : plugin.getKey().substring(plus + 1), _ -> new LinkedHashMap<>());
            if (hooked.putIfAbsent(name, plugin.getValue().apply(descriptor.location(), properties)) != null) {
                throw new IllegalArgumentException("The plugin " + plugin.getKey() + " takes the name of a custom module"
                        + " that is added already - give the plugin another name");
            }
        }
        SequencedMap<String, BuildExecutorModule> none = Collections.emptyNavigableMap();
        Sbom sbom = environment.flag("sbom.cyclonedx", true)
                ? Sbom.configured(BuildStep.locate(descriptor.configuration(), "sbom.properties"))
                : null;
        AssemblyDescriptor assembly = new AssemblyDescriptor((sub, outerInherited) -> {
            SequencedSet<String> closure = new LinkedHashSet<>(descriptor.artifacts());
            if (modules != null) {
                sub.addModule("modules",
                        ModularizeModule.ofEnvironment(environment, factory, modules),
                        descriptor.artifacts().stream());
                closure = new LinkedHashSet<>(Set.of("modules"));
            }
            sub.addStep("prepare",
                    new Prepare(descriptor.pathPlacement(), packaging.jpackage(), overrides),
                    outerInherited.sequencedKeySet().stream());
            sub.addModule("check",
                    check.apply(InferredSourceCodeQualityModule.ofEnvironment(environment, descriptor.configuration(), repositories, resolvers)
                                .pinning(descriptor.pinning())
                                .custom(hooks.getOrDefault("check", none))),
                    Stream.of(descriptor.sources().stream(), descriptor.spdx().stream(), descriptor.manifests().stream())
                            .flatMap(Function.identity()));
            sub.addModule("format",
                    format.apply(InferredSourceFormattingModule.ofEnvironment(environment, descriptor.configuration(), repositories, resolvers)
                                 .pinning(descriptor.pinning())
                                 .custom(hooks.getOrDefault("format", none))),
                    Stream.of(descriptor.sources().stream(), descriptor.spdx().stream(), descriptor.manifests().stream())
                            .flatMap(Function.identity()));
            if (sbom != null) {
                sub.addStep("sbom", sbom,
                        Stream.of(descriptor.manifests().stream(),
                                        descriptor.artifacts().stream(),
                                        descriptor.sources().stream(),
                                        descriptor.resources().stream())
                                .flatMap(Function.identity()));
            }
            sub.addModule("compliance", compliance.apply(InferredComplianceModule.ofEnvironment(environment, descriptor.configuration())
                            .custom(hooks.getOrDefault("compliance", none))),
                          Stream.concat(descriptor.manifests().stream(), descriptor.artifacts().stream()));
            InferredJavaToolchainModule toolchainModule = InferredJavaToolchainModule.ofEnvironment(environment,
                            descriptor.configuration(),
                            repositories,
                            resolvers)
                    .pinning(descriptor.pinning())
                    .pathPlacement(descriptor.pathPlacement())
                    .custom(hooks.getOrDefault("binary", none));
            if (hooks.containsKey("binary/generated")) {
                toolchainModule = toolchainModule.generatorModule(InferredSourceGenerationModule.ofEnvironment(environment,
                        descriptor.configuration(),
                        repositories,
                        resolvers).custom(hooks.get("binary/generated")));
            }
            if (hooks.containsKey("binary/compiled")) {
                toolchainModule = toolchainModule.compilerModule(InferredCompilerChainModule.ofEnvironment(environment,
                        descriptor.configuration(),
                        repositories,
                        resolvers).custom(hooks.get("binary/compiled")));
            }
            if (hooks.containsKey("binary/validate")) {
                toolchainModule = toolchainModule.validatorModule(InferredByteCodeQualityModule.ofEnvironment(environment,
                        descriptor.configuration(),
                        repositories,
                        resolvers).custom(hooks.get("binary/validate")));
            }
            if (!resources.isEmpty()) {
                SequencedMap<Path, Path> bound = new LinkedHashMap<>();
                resources.forEach((target, source) -> bound.put(Path.of(BuildStep.RESOURCES).resolve(target), source));
                sub.addModule("include", Bind.asInputs(new LinkedHashMap<>(Map.of("resources", bound))));
            }
            sub.addModule("binary", toolchain.apply(toolchainModule),
                    Stream.of(
                            Stream.of("prepare"),
                            inputs(descriptor, closure),
                            descriptor.resources().stream(),
                            sbom == null ? Stream.<String>empty() : Stream.of("sbom"),
                            resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"))
                            .flatMap(Function.identity()));
            sub.addModule("artifact",
                    artifact.apply(
                            InferredArtifactQualityModule.ofEnvironment(environment, descriptor.configuration(), repositories, resolvers)
                                    .pinning(descriptor.pinning())
                                    .custom(hooks.getOrDefault("artifact", none))),
                    Stream.concat(Stream.of("binary"), inputs(descriptor, closure)));
            sub.addStep("layers",
                    new Layers(),
                    Stream.concat(Stream.of("binary"), inputs(descriptor, closure)));
            if (descriptor.test()) {
                Path module = null;
                for (String manifest : descriptor.manifests()) {
                    Path candidate = outerInherited.get(manifest);
                    if (candidate != null && Files.isRegularFile(candidate.resolve(BuildStep.MODULE))) {
                        module = candidate.resolve(BuildStep.MODULE);
                        break;
                    }
                }
                if (module != null) {
                    SequencedProperties properties = SequencedProperties.ofFiles(module);
                    if (properties.getProperty("test") != null && !properties.flag("abstract")) {
                        sub.addModule("observed", observe.apply(
                                InferredTestObservationModule.ofEnvironment(environment, descriptor.configuration(), repositories, resolvers)
                                        .pinning(descriptor.pinning())
                                        .pathPlacement(descriptor.pathPlacement())
                                        .moduleName(properties.getProperty("module"))
                                        .custom(hooks.getOrDefault("observed", none))),
                                Stream.concat(Stream.of("prepare", "binary", "layers"),
                                        inputs(descriptor, closure)));
                    }
                }
            }
            if (descriptor.source()) {
                sub.addModule("sources", (module, inherited) ->
                        module.addStep("archive",
                                Jar.ofEnvironment(environment, factory, Jar.Sort.SOURCES),
                                inherited.sequencedKeySet()), descriptor.sources());
            }
            if (descriptor.documentation()) {
                InferredDocumentationModule documentationModule = InferredDocumentationModule.ofEnvironment(environment,
                                repositories,
                                resolvers)
                        .pinning(descriptor.pinning())
                        .custom(hooks.getOrDefault("documentation", none));
                if (hooks.containsKey("documentation/generate")) {
                    documentationModule = documentationModule.generateModule(InferredDocumentationChainModule.ofEnvironment(environment,
                            repositories,
                            resolvers).custom(hooks.get("documentation/generate")));
                }
                sub.addModule("documentation",
                        documentation.apply(documentationModule),
                        Stream.concat(Stream.of("binary"), inputs(descriptor, closure)));
            }
            if (packaging.jmod() || packaging.jlink() || packaging.jpackage() != null || packaging.nativeImage()) {
                sub.addStep("legal", Legal.ofEnvironment(environment), Stream.concat(Stream.of("binary"), closure.stream()));
            }
            if (packaging.jmod()) {
                sub.addStep("jmod",
                        JMod.ofEnvironment(environment, factory),
                        Stream.of(Stream.of("binary", "legal"),
                                        descriptor.content().stream(),
                                        descriptor.resources().stream(),
                                        sbom == null ? Stream.<String>empty() : Stream.of("sbom"),
                                        resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"))
                                .flatMap(Function.identity()));
            }
            if (!hooks.get("").isEmpty()) {
                sub.addModule("custom", (nested, nestedInherited) -> hooks.get("").forEach((name, module) ->
                        nested.addModule(name, module, nestedInherited.sequencedKeySet())), outerInherited.sequencedKeySet());
            }
        });
        SequencedMap<String, BuildExecutorModule> packagers = hooks.getOrDefault("package", none);
        if (packaging.jlink() || packaging.jpackage() != null || packaging.bundle() || packaging.launcher() || packaging.nativeImage() || packaging.docker() != null
                || !packagers.isEmpty()) {
            assembly = assembly.then("package", (sub, inherited) -> {
                SequencedSet<String> images = new LinkedHashSet<>();
                SequencedSet<String> inputs = new LinkedHashSet<>(inherited.sequencedKeySet());
                SequencedSet<String> identified = Stream.of(descriptor.manifests(),
                                descriptor.coordinates(),
                                descriptor.sources(),
                                descriptor.resources())
                        .flatMap(SequencedSet::stream)
                        .map(InferredMultiProjectAssembler::local)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                inputs.removeIf(key -> identified.contains(local(key)));
                if (modules != null) {
                    SequencedSet<String> replaced = descriptor.artifacts().stream()
                            .map(InferredMultiProjectAssembler::local)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    inputs.removeIf(key -> replaced.contains(local(key)));
                }
                SequencedSet<String> linked = new LinkedHashSet<>(inputs);
                if (!packaging.jmod() && (packaging.jlink() || packaging.jpackage() != null)) {
                    SequencedSet<String> packed = descriptor.resources().stream()
                            .map(InferredMultiProjectAssembler::local)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    sub.addStep("jmod", JMod.ofEnvironment(environment, factory), Stream.concat(inputs.stream(),
                            inherited.sequencedKeySet().stream().filter(key -> packed.contains(local(key)))));
                    linked.add("jmod");
                }
                if (packaging.jlink()) {
                    sub.addStep("jlink", JLink.ofEnvironment(environment, factory), linked);
                    images.add("jlink");
                }
                if (packaging.jpackage() != null) {
                    sub.addStep("jpackage", JPackage.ofEnvironment(environment, factory).type(packaging.jpackage()), packaging.jlink()
                                ? Stream.concat(Stream.of("jlink"), linked.stream())
                                : linked.stream());
                    images.add("jpackage");
                }
                if (packaging.bundle()) {
                    sub.addStep("bundle", Bundle.ofEnvironment(environment), inputs);
                }
                SequencedSet<String> locals = Stream.of(descriptor.manifests(),
                                descriptor.artifacts(),
                                descriptor.sources(),
                                descriptor.resources())
                        .flatMap(SequencedSet::stream)
                        .map(InferredMultiProjectAssembler::local)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                SequencedSet<String> described = inherited.sequencedKeySet().stream()
                        .filter(key -> locals.contains(local(key)))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (packaging.launcher()) {
                    LauncherModule launcher = LauncherModule.ofEnvironment(environment, repositories, resolvers)
                            .pinning(descriptor.pinning())
                            .pathPlacement(descriptor.pathPlacement());
                    SequencedSet<String> launched = new LinkedHashSet<>(inputs);
                    if (sbom != null) {
                        launcher = launcher.sbom(sbom.type("application")).sbomInputs(described);
                        launched.addAll(described);
                    }
                    sub.addModule("launcher", launcher, launched);
                }
                if (packaging.docker() != null) {
                    sub.addStep("docker", new Docker(packaging.docker()), inputs);
                    images.add("docker");
                }
                if (packaging.nativeImage()) {
                    sub.addStep("reachability", new NativeImageMetadata(), inputs);
                    sub.addStep("native-image", NativeImage.ofEnvironment(environment, descriptor.pathPlacement()),
                                Stream.concat(inputs.stream(), Stream.of("reachability")));
                    if (sbom == null) {
                        images.add("native-image");
                    } else {
                        sub.addStep("native-sbom",
                                sbom.type("application").graalvmLicense(environment.value("graalvm.license")),
                                Stream.concat(described.stream(), Stream.of("native-image")));
                        sub.addStep("native", new Described(), "native-image", "native-sbom");
                        images.add("native");
                    }
                }
                if (!packagers.isEmpty()) {
                    SequencedSet<String> handed = new LinkedHashSet<>(linked);
                    handed.addAll(images);
                    if (packaging.bundle()) {
                        handed.add("bundle");
                    }
                    if (packaging.launcher()) {
                        handed.add("launcher");
                    }
                    sub.addModule("custom", (nested, nestedInherited) -> packagers.forEach((name, module) ->
                            nested.addModule(name, module, nestedInherited.sequencedKeySet())), handed);
                    sub.addStep("packaged", new Packaged(), images.contains("jpackage")
                            ? Stream.of("jpackage", "custom")
                            : Stream.of("custom"));
                    images.remove("jpackage");
                    images.addFirst("packaged");
                    images.add("custom");
                }
                if (packaging.launcher()) {
                    images.add("launcher");
                }
                if (!images.isEmpty()) {
                    String named = descriptor.name().endsWith("-")
                            ? descriptor.name().substring(0, descriptor.name().length() - 1)
                            : descriptor.name();
                    sub.addStep("inventory", new Inventory().module(named), images.stream());
                }
            });
        }
        return assembly;
    }

    private record Packaged() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path packages = context.next().resolve(JPackage.PACKAGES);
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder().resolve(JPackage.PACKAGES);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path target = packages.resolve(folder.relativize(file).toString());
                        if (Files.exists(target)) {
                            throw new IllegalStateException(argument.getKey() + " packages " + folder.relativize(file)
                                    + ", which another packager writes already - give each package a name of its own");
                        }
                        Files.createDirectories(target.getParent());
                        BuildStep.linkOrCopy(target, file);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Described() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path image = context.next().resolve(NativeImage.NATIVE);
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path folder = argument.folder().resolve(NativeImage.NATIVE);
                if (Files.isDirectory(folder)) {
                    try (Stream<Path> files = Files.walk(folder)) {
                        for (Path file : files.filter(Files::isRegularFile).toList()) {
                            Path target = image.resolve(folder.relativize(file).toString());
                            Files.createDirectories(target.getParent());
                            BuildStep.linkOrCopy(target, file);
                        }
                    }
                }
            }
            if (!Files.isDirectory(image)) {
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path fragment = argument.folder().resolve(Versions.MANIFEST);
                if (Files.isRegularFile(fragment)) {
                    String location;
                    try (InputStream in = Files.newInputStream(fragment)) {
                        location = new Manifest(in).getMainAttributes().getValue("Sbom-Location");
                    }
                    Path document = location == null ? null : argument.folder().resolve(RESOURCES).resolve(location);
                    if (document != null && Files.isRegularFile(document)) {
                        Path target = image.resolve(document.getFileName().toString());
                        if (Files.exists(target)) {
                            throw new IllegalStateException("The native image already holds " + target.getFileName()
                                    + ", where its bill of materials belongs - give the image another name");
                        }
                        BuildStep.linkOrCopy(target, document);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Packaging(boolean jmod,
                            boolean jlink,
                            boolean bundle,
                            boolean launcher,
                            boolean nativeImage,
                            String jpackage,
                            String docker) {

        private static Packaging configured(Path properties) throws IOException {
            if (properties == null) {
                return new Packaging(false, false, false, false, false, null, null);
            }
            SequencedProperties configuration = SequencedProperties.ofFiles(properties);
            return new Packaging(configuration.flag("jmod"),
                    configuration.flag("jlink"),
                    configuration.flag("bundle"),
                    configuration.flag("launcher"),
                    configuration.flag("native"),
                    configuration.value("jpackage"),
                    configuration.value("docker"));
        }
    }

    private static String local(String identity) {
        while (identity.startsWith(BuildExecutorModule.PREVIOUS)) {
            identity = identity.substring(BuildExecutorModule.PREVIOUS.length());
        }
        return identity;
    }

    private static Stream<String> inputs(ProjectModuleDescriptor descriptor, SequencedSet<String> closure) {
        return Stream.of(descriptor.sources(),
                descriptor.manifests(),
                closure,
                descriptor.spdx()).flatMap(SequencedSet::stream);
    }

    private static SequencedMap<String, SequencedMap<String, String>> overridesOf(SequencedSet<Path> configuration)
            throws IOException {
        SequencedMap<String, Path> files = new LinkedHashMap<>();
        for (Path folder : configuration) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "process-*.properties")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();
                    String command = fileName.substring("process-".length(), fileName.length() - ".properties".length());
                    files.putIfAbsent(command, file);
                }
            }
        }
        SequencedMap<String, SequencedMap<String, String>> overrides = new LinkedHashMap<>();
        for (String command : new TreeSet<>(files.keySet())) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(files.get(command)).forEachProperty(values::put);
            overrides.put(command, values);
        }
        return overrides;
    }

    private record Prepare(PathPlacement pathPlacement,
                           String packageType,
                           SequencedMap<String, SequencedMap<String, String>> overrides) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            String main = null;
            String version = null;
            String artifact = null;
            String moduleName = null;
            SequencedProperties described = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path moduleFile = argument.folder().resolve(BuildStep.MODULE);
                if (Files.isRegularFile(moduleFile)) {
                    SequencedProperties module = SequencedProperties.ofFiles(moduleFile);
                    if (main == null) {
                        String value = module.getProperty("main");
                        if (value != null && !value.isEmpty()) {
                            main = value;
                        }
                    }
                    if (moduleName == null) {
                        String value = module.getProperty("module");
                        if (value != null && !value.isEmpty()) {
                            moduleName = value;
                        }
                    }
                }
                Path metadataFile = argument.folder().resolve(BuildStep.METADATA);
                if (Files.isRegularFile(metadataFile)) {
                    SequencedProperties metadata = SequencedProperties.ofFiles(metadataFile);
                    if (described == null) {
                        described = metadata;
                    }
                    if (version == null) {
                        String value = metadata.getProperty("version");
                        if (value != null && !value.isEmpty()) {
                            version = value;
                        }
                    }
                    if (artifact == null) {
                        String value = metadata.getProperty("artifact");
                        if (value != null && !value.isEmpty()) {
                            artifact = value;
                        }
                    }
                }
            }
            Path processFolder = null;
            if (main != null) {
                processFolder = Files.createDirectories(context.next().resolve(ProcessBuildStep.PROCESS));
                SequencedProperties jar = new SequencedProperties();
                jar.setProperty("--main-class", main);
                jar.store(processFolder.resolve("jar.properties"));
                SequencedProperties jpackage = new SequencedProperties();
                if (artifact != null) {
                    jpackage.setProperty("--name", artifact);
                }
                if (pathPlacement.modular() && moduleName != null) {
                    jpackage.setProperty("--module", moduleName + "/" + main);
                } else {
                    jpackage.setProperty("--main-jar", Jar.Sort.CLASSES.getFile());
                    jpackage.setProperty("--main-class", main);
                }
                if (version != null) {
                    jpackage.setProperty("--app-version", version);
                }
                if (described != null) {
                    String description = described.value("description"), url = described.value("url");
                    if (description != null) {
                        jpackage.setProperty("--description", description.replaceAll("\\s+", " "));
                    }
                    if (url != null && packageType != null && !packageType.equals("app-image")) {
                        jpackage.setProperty("--about-url", url);
                    }
                    List<String> emails = new ArrayList<>(), licenses = new ArrayList<>();
                    described.forEachProperty((key, value) -> {
                        if (key.startsWith("developer.") && key.endsWith(".email") && !value.isBlank()) {
                            emails.add(value.trim());
                        } else if (key.startsWith("license.") && key.endsWith(".name") && !value.isBlank()) {
                            licenses.add(value.trim());
                        }
                    });
                    if ("deb".equals(packageType) && !emails.isEmpty()) {
                        jpackage.setProperty("--linux-deb-maintainer", emails.getFirst());
                    }
                    if ("rpm".equals(packageType) && !licenses.isEmpty()) {
                        jpackage.setProperty("--linux-rpm-license-type", String.join(" OR ", licenses));
                    }
                }
                jpackage.store(processFolder.resolve("jpackage.properties"));
                SequencedProperties launcher = new SequencedProperties();
                launcher.setProperty("mainClass", main);
                if (pathPlacement.modular() && moduleName != null) {
                    launcher.setProperty("mainModule", moduleName);
                }
                if (artifact != null) {
                    launcher.setProperty("name", artifact);
                }
                launcher.store(context.next().resolve("launcher.properties"));
            }
            if (moduleName != null) {
                if (processFolder == null) {
                    processFolder = Files.createDirectories(context.next().resolve(ProcessBuildStep.PROCESS));
                }
                SequencedProperties jlink = new SequencedProperties();
                jlink.setProperty("--add-modules", moduleName);
                jlink.store(processFolder.resolve("jlink.properties"));
            }
            if (version != null) {
                if (processFolder == null) {
                    processFolder = Files.createDirectories(context.next().resolve(ProcessBuildStep.PROCESS));
                }
                SequencedProperties javac = new SequencedProperties();
                javac.setProperty("--module-version", version);
                javac.store(processFolder.resolve("javac.properties"));
            }
            if (!overrides.isEmpty() && processFolder == null) {
                processFolder = Files.createDirectories(context.next().resolve(ProcessBuildStep.PROCESS));
            }
            for (Map.Entry<String, SequencedMap<String, String>> override : overrides.entrySet()) {
                Path target = processFolder.resolve(override.getKey() + ".properties");
                SequencedProperties merged = Files.isRegularFile(target)
                        ? SequencedProperties.ofFiles(target)
                        : new SequencedProperties();
                override.getValue().forEach(merged::setProperty);
                merged.store(target);
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

    }
}
