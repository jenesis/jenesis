package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bundle;
import build.jenesis.step.Docker;
import build.jenesis.step.Inventory;
import build.jenesis.step.JLink;
import build.jenesis.step.JMod;
import build.jenesis.step.JPackage;
import build.jenesis.step.Jar;
import build.jenesis.step.NativeImage;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Sbom;

public record InferredMultiProjectAssembler(Function<InferredSourceCodeQualityModule, BuildExecutorModule> check,
                                            Function<InferredSourceFormattingModule, BuildExecutorModule> format,
                                            Function<JavaToolchainModule, BuildExecutorModule> toolchain,
                                            Function<InferredTestObservationModule, BuildExecutorModule> observe,
                                            Function<TestModule, BuildExecutorModule> test,
                                            Function<InferredComplianceModule, BuildExecutorModule> compliance) implements MultiProjectAssembler<ProjectModuleDescriptor> {

    public InferredMultiProjectAssembler() {
        this(module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module);
    }

    public InferredMultiProjectAssembler check(Function<InferredSourceCodeQualityModule, BuildExecutorModule> check) {
        return new InferredMultiProjectAssembler(check, format, toolchain, observe, test, compliance);
    }

    public InferredMultiProjectAssembler format(Function<InferredSourceFormattingModule, BuildExecutorModule> format) {
        return new InferredMultiProjectAssembler(check, format, toolchain, observe, test, compliance);
    }

    public InferredMultiProjectAssembler toolchain(Function<JavaToolchainModule, BuildExecutorModule> toolchain) {
        return new InferredMultiProjectAssembler(check, format, toolchain, observe, test, compliance);
    }

    public InferredMultiProjectAssembler observe(Function<InferredTestObservationModule, BuildExecutorModule> observe) {
        return new InferredMultiProjectAssembler(check, format, toolchain, observe, test, compliance);
    }

    public InferredMultiProjectAssembler test(Function<TestModule, BuildExecutorModule> test) {
        return new InferredMultiProjectAssembler(check, format, toolchain, observe, test, compliance);
    }

    public InferredMultiProjectAssembler compliance(Function<InferredComplianceModule, BuildExecutorModule> compliance) {
        return new InferredMultiProjectAssembler(check, format, toolchain, observe, test, compliance);
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
        ProcessHandler.Factory factory = ProcessHandler.Factory.of();
        AssemblyDescriptor assembly = new AssemblyDescriptor((sub, outerInherited) -> {
            SequencedSet<String> closure = new LinkedHashSet<>(descriptor.artifacts());
            if (modules != null) {
                sub.addModule("modules",
                        new ModularizeModule(factory, modules),
                        descriptor.artifacts().stream());
                closure = new LinkedHashSet<>(Set.of("modules"));
            }
            sub.addStep("prepare",
                    new Prepare(descriptor.pathPlacement(), overrides),
                    outerInherited.sequencedKeySet().stream());
            sub.addModule("check",
                    check.apply(new InferredSourceCodeQualityModule(descriptor.configuration(), repositories, resolvers)
                            .pinning(descriptor.pinning())),
                    Stream.of(descriptor.sources().stream(), descriptor.spdx().stream(), descriptor.manifests().stream())
                            .flatMap(Function.identity()));
            sub.addModule("format",
                    format.apply(new InferredSourceFormattingModule(descriptor.configuration(), repositories, resolvers)
                            .pinning(descriptor.pinning())),
                    Stream.of(descriptor.sources().stream(), descriptor.spdx().stream(), descriptor.manifests().stream())
                            .flatMap(Function.identity()));
            Sbom sbom = Boolean.parseBoolean(System.getProperty("jenesis.sbom.cyclonedx", "true"))
                    ? Sbom.configured(BuildStep.locate(descriptor.configuration(), "sbom.properties"))
                    : null;
            if (sbom != null) {
                sub.addStep("sbom", sbom,
                        Stream.concat(descriptor.manifests().stream(), descriptor.artifacts().stream()));
            }
            sub.addModule("compliance", compliance.apply(new InferredComplianceModule(descriptor.configuration())),
                    Stream.concat(descriptor.manifests().stream(), descriptor.artifacts().stream()));
            sub.addModule("binary", toolchain.apply(new JavaToolchainModule()
                            .compiler(new InferredCompilerChainModule(repositories, resolvers)
                                    .pinning(descriptor.pinning())
                                    .pathPlacement(descriptor.pathPlacement()))
                            .validator(new InferredByteCodeQualityModule(descriptor.configuration(), repositories, resolvers)
                                    .pinning(descriptor.pinning()))
                            .archiver(new Jar(factory, Jar.Sort.CLASSES).asModule("jar"))),
                    Stream.of(
                            Stream.of("prepare"),
                            inputs(descriptor, closure),
                            descriptor.resources().stream(),
                            sbom == null ? Stream.<String>empty() : Stream.of("sbom"))
                            .flatMap(Function.identity()));
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
                    if (properties.getProperty("test") != null) {
                        sub.addModule("observed", observe.apply(new InferredTestObservationModule(
                                descriptor.configuration(),
                                repositories,
                                resolvers,
                                descriptor.pinning(),
                                engines -> test.apply(new TestModule(repositories, resolvers)
                                        .observe(engines)
                                        .pinning(descriptor.pinning())
                                        .pathPlacement(descriptor.pathPlacement())
                                        .moduleName(properties.getProperty("module")))
                                )), Stream.concat(Stream.of("prepare", "binary"), inputs(descriptor, closure)));
                    }
                }
            }
            if (descriptor.source()) {
                sub.addModule("sources", (module, inherited) ->
                        module.addStep("archive",
                                new Jar(factory, Jar.Sort.SOURCES),
                                inherited.sequencedKeySet()), descriptor.sources());
            }
            if (descriptor.documentation()) {
                sub.addModule("documentation", (module, inherited) -> {
                    module.addModule("generate",
                            new InferredDocumentationChainModule(repositories, resolvers)
                                    .pinning(descriptor.pinning()),
                            inherited.sequencedKeySet());
                    module.addStep("archive",
                            new Jar(factory, Jar.Sort.JAVADOC),
                            "generate/"
                                    + InferredDocumentationChainModule.DOCUMENT
                                    + "/"
                                    + InferredDocumentationChainModule.AGGREGATE);
                }, Stream.concat(Stream.of("binary"), inputs(descriptor, closure)));
            }
            if (packaging.jmod()) {
                sub.addStep("jmod",
                        new JMod(factory),
                        Stream.concat(Stream.of("binary"), descriptor.content().stream()));
            }
        });
        if (packaging.jlink() || packaging.jpackage() != null || packaging.bundle() || packaging.launcher() || packaging.nativeImage() || packaging.docker() != null) {
            assembly = assembly.then("package", (sub, inherited) -> {
                SequencedSet<String> images = new LinkedHashSet<>();
                SequencedSet<String> inputs = new LinkedHashSet<>(inherited.sequencedKeySet());
                if (modules != null) {
                    SequencedSet<String> replaced = descriptor.artifacts().stream()
                            .map(InferredMultiProjectAssembler::local)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    inputs.removeIf(key -> replaced.contains(local(key)));
                }
                if (packaging.jlink()) {
                    sub.addStep("jlink", new JLink(factory), inputs);
                    images.add("jlink");
                }
                if (packaging.jpackage() != null) {
                    sub.addStep("jpackage", new JPackage(factory, packaging.jpackage()), packaging.jlink()
                            ? Stream.concat(Stream.of("jlink"), inputs.stream())
                            : inputs.stream());
                    images.add("jpackage");
                }
                if (packaging.bundle()) {
                    sub.addStep("bundle", new Bundle(), inputs);
                }
                if (packaging.launcher()) {
                    sub.addModule("launcher",
                            new LauncherModule(repositories, resolvers)
                                    .pinning(descriptor.pinning())
                                    .pathPlacement(descriptor.pathPlacement()),
                            inputs.stream());
                }
                if (packaging.docker() != null) {
                    sub.addStep("docker", new Docker(packaging.docker()), inputs);
                    images.add("docker");
                }
                if (packaging.nativeImage()) {
                    sub.addStep("reachability", new NativeImageMetadata(), inputs);
                    sub.addStep("native-image", new NativeImage(descriptor.pathPlacement()),
                            Stream.concat(inputs.stream(), Stream.of("reachability")));
                    images.add("native-image");
                }
                if (!images.isEmpty()) {
                    sub.addStep("inventory", new Inventory(), images.stream());
                }
            });
        }
        return assembly;
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
            return new Packaging(flag(configuration, "jmod"),
                    flag(configuration, "jlink"),
                    flag(configuration, "bundle"),
                    flag(configuration, "launcher"),
                    flag(configuration, "native"),
                    value(configuration, "jpackage"),
                    value(configuration, "docker"));
        }

        private static boolean flag(SequencedProperties configuration, String key) {
            String value = configuration.getProperty(key);
            return value != null && Boolean.parseBoolean(value.trim());
        }

        private static String value(SequencedProperties configuration, String key) {
            String value = configuration.getProperty(key);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
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
