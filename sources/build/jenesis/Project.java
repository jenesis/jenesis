package build.jenesis;

import module java.base;
import build.jenesis.docker.DockerizedJava;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenProject;
import build.jenesis.maven.MavenRepositoryExport;
import build.jenesis.maven.MavenRepositoryStaging;
import build.jenesis.maven.MavenResolver;
import build.jenesis.maven.PinPom;
import build.jenesis.maven.Pom;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisModuleRepositoryExport;
import build.jenesis.module.JenesisRepository;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.module.ModularProject;
import build.jenesis.module.ModularStaging;
import build.jenesis.module.PinModuleInfo;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.Ide;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.MultiProjectModule;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.project.ReleaseModule;
import build.jenesis.project.ProjectWatch;
import build.jenesis.step.Bind;
import build.jenesis.step.Bom;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ImageStaging;
import build.jenesis.step.Inventory;
import build.jenesis.step.ReportStaging;
import build.jenesis.step.Tree;

public record Project(
        Path root,
        Path target,
        Path artifacts,
        SequencedSet<Path> metadata,
        SequencedSet<Path> configuration,
        SequencedSet<Path> boms,
        SequencedSet<Path> signatures,
        SequencedSet<Path> profiles,
        BuildExecutorCache cache,
        HashDigestFunction hashFunction,
        Layout layout,
        boolean tests,
        boolean sources,
        boolean documentation,
        Pinning pinning,
        String version,
        String tag,
        String revision,
        String tree,
        SequencedSet<String> defaultTarget,
        MultiProjectAssembler<? super ProjectModuleDescriptor> assembler,
        Supplier<BuildExecutor.Configuration> configurator,
        Map<String, Repository> repositories,
        Map<String, Resolver> resolvers,
        Function<String, String> keys,
        Output output) {

    public static final String BUILD = "build",
            STAGE = "stage",
            EXPORT = "export",
            RELEASE = "release",
            PIN = "pin",
            DEPENDENCIES = "dependencies",
            IDE = "ide",
            METADATA = "metadata",
            HELP = "help",
            SKILL = "skill",
            PROPERTIES = "properties",
            CONFIGURATION = "configuration";

    @FunctionalInterface
    public interface Layout {

        Function<String, String> apply(BuildExecutor executor,
                                       Project project,
                                       MultiProjectAssembler<? super ProjectModuleDescriptor> assembler) throws IOException;

        private static Path modularConfigurationFolder(Path location) {
            return location == null ? null : location.resolve("META-INF").resolve("build.jenesis");
        }

        static SequencedSet<Path> configurations(Path local, SequencedSet<Path> folders, SequencedSet<Path> profiles) {
            return configurations(Collections.singletonList(local), folders, profiles);
        }

        static SequencedSet<Path> configurations(List<Path> locals, SequencedSet<Path> folders, SequencedSet<Path> profiles) {
            LinkedHashSet<Path> base = new LinkedHashSet<>();
            Stream.concat(locals.stream(), folders.stream())
                    .filter(Objects::nonNull)
                    .map(folder -> folder.toAbsolutePath().normalize())
                    .forEach(base::add);
            LinkedHashSet<Path> ordered = new LinkedHashSet<>();
            for (Path folder : base) {
                for (Path profile : profiles) {
                    Path resolved = folder.resolve(profile);
                    if (Files.isDirectory(resolved)) {
                        ordered.add(resolved);
                    }
                }
            }
            for (Path folder : base) {
                if (Files.isDirectory(folder)) {
                    ordered.add(folder);
                }
            }
            return Collections.unmodifiableSequencedSet(ordered);
        }

        Layout MAVEN = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("maven", assembler.getClass().getName(), project.output()));
            executor.addModule(SKILL, new SkillModule(project.target(), project.output()));
            executor.addModule(METADATA, project.metadataModule());
            MultiProjectAssembler<? super ProjectModuleDescriptor> pomAware = new PomAwareAssembler(assembler, null, null, false);
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                repositories.putIfAbsent("maven",
                        MavenDefaultRepository.ofKeys(project.keys(), project.output())
                                .cached(project.keys(), project.output(), project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                repositories.putIfAbsent("OpenPGP", OpenPgpRepository.ofKeys(project.keys()));
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("maven", MavenPomResolver.ofKeys(project.keys()));
                SequencedSet<String> mavenDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(mavenDeps::add);
                sub.addModule("maven", MavenProject.make(project.keys(), project.output(), project.root(),
                                "main",
                                "maven",
                                Collections.unmodifiableMap(repositories),
                                Collections.unmodifiableMap(resolvers),
                                project.pinning(),
                                project.licenseFiles(Dependencies.SPDX),
                                (descriptor, mergedRepos, mergedResolvers) -> pomAware.apply(
                                        new ProjectModuleDescriptor(descriptor,
                                                configurations(descriptor.configurations(), project.configuration(), project.profiles()),
                                                project.tests(),
                                                project.sources(),
                                                project.documentation(),
                                                project.pinning(),
                                                PathPlacement.CLASS_PATH),
                                        mergedRepos,
                                        mergedResolvers)),
                        mavenDeps);
            }, METADATA);
            executor.addModule(STAGE, (stage, inherited) -> {
                stage.addStep("maven", MavenRepositoryStaging.ofKeys(project.keys()), inherited.sequencedKeySet());
                stage.addStep("packages", new ImageStaging("package").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("native", new ImageStaging("native").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("docker", new ImageStaging("docker"), inherited.sequencedKeySet());
                stage.addStep("reports", new ReportStaging(), inherited.sequencedKeySet());
            }, BUILD);
            executor.addModule(EXPORT, (export, _) -> export.addStep(
                    "maven", MavenRepositoryExport.ofKeys(project.keys()), BuildExecutorModule.PREVIOUS + STAGE + "/maven"), STAGE);
            String prefix = BUILD + "/maven/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(PIN, new PinModule(project.root(),
                    "pom.xml",
                    (path, file) -> PinPom.ofKeys(project.keys(), "maven", path, List.of(file), project.hashFunction()),
                    project.hashFunction(),
                    project.keys(),
                    project.output()), BUILD);
            executor.addModule(DEPENDENCIES, (tree, inherited) -> tree.addStep(
                    "tree", Tree.ofKeys(project.keys(), project.output()), inherited.sequencedKeySet()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, ReleaseModule.ofKeys(project.keys(), project.output(), project.root(), project.version()), STAGE);
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/module-" + BuildExecutorModule.encode(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout MODULAR = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("modular", assembler.getClass().getName(), project.output()));
            executor.addModule(SKILL, new SkillModule(project.target(), project.output()));
            executor.addModule(METADATA, project.metadataModule());
            MultiProjectAssembler<? super ProjectModuleDescriptor> bomAware = new BomAwareAssembler(assembler, project.hashFunction());
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                repositories.putIfAbsent("module",
                        JenesisModuleRepository.ofKeys(project.keys(), project.output(), JenesisRepository.Scope.MODULE)
                                .cached(project.keys(), project.output(), project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                repositories.putIfAbsent("OpenPGP", OpenPgpRepository.ofKeys(project.keys()));
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("module", ModularJarResolver.ofKeys(project.keys(), false));
                SequencedSet<String> modulesDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(modulesDeps::add);
                sub.addModule("modules", ModularProject.make(project.keys(), project.output(), project.root(),
                                "main",
                                "module",
                                _ -> true,
                                Collections.unmodifiableMap(repositories),
                                Collections.unmodifiableMap(resolvers),
                                project.pinning(),
                                true,
                                project.licenseFiles(Dependencies.SPDX),
                                project.boms(),
                project.signatures(),
                                (descriptor, mergedRepos, mergedResolvers) -> bomAware.apply(
                                        new ProjectModuleDescriptor(descriptor,
                                                configurations(
                                                        modularConfigurationFolder(descriptor.location()),
                                                        project.configuration(),
                                                        project.profiles()),
                                                project.tests(),
                                                project.sources(),
                                                project.documentation(),
                                                project.pinning(),
                                                PathPlacement.MODULE_PATH),
                                        mergedRepos,
                                        mergedResolvers)),
                        modulesDeps);
            }, METADATA);
            executor.addModule(STAGE, (stage, inherited) -> {
                stage.addStep("modular", ModularStaging.ofKeys(project.keys()), inherited.sequencedKeySet());
                stage.addStep("packages", new ImageStaging("package").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("runtime", new ImageStaging("image"), inherited.sequencedKeySet());
                stage.addStep("layers", new ImageStaging("layers"), inherited.sequencedKeySet());
                stage.addStep("native", new ImageStaging("native").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("docker", new ImageStaging("docker"), inherited.sequencedKeySet());
                stage.addStep("reports", new ReportStaging(), inherited.sequencedKeySet());
            }, BUILD);
            executor.addModule(EXPORT, (export, _) -> export.addStep(
                    "modular", JenesisModuleRepositoryExport.ofKeys(project.keys()), BuildExecutorModule.PREVIOUS + STAGE + "/modular"), STAGE);
            String prefix = BUILD + "/modules/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(PIN, new PinModule(project.root(), "module-info.java",
                    (path, file) -> PinModuleInfo.ofKeys(project.keys(), project.output(), "module", path, List.of(file), project.hashFunction()),
                    project.hashFunction(),
                    project.keys(),
                    project.output()), BUILD);
            executor.addModule(DEPENDENCIES, (tree, inherited) -> tree.addStep(
                    "tree", Tree.ofKeys(project.keys(), project.output()), inherited.sequencedKeySet()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, ReleaseModule.ofKeys(project.keys(), project.output(), project.root(), project.version()), STAGE);
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/module-" + BuildExecutorModule.encode(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout MODULAR_TO_MAVEN = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("modular_to_maven", assembler.getClass().getName(), project.output()));
            executor.addModule(SKILL, new SkillModule(project.target(), project.output()));
            executor.addModule(METADATA, project.metadataModule());
            MultiProjectAssembler<? super ProjectModuleDescriptor> pomAware = new PomAwareAssembler(assembler,
                    BuildExecutorModule.PREVIOUS.repeat(2) + MultiProjectModule.MANIFESTS,
                    "module",
                    true);
            MultiProjectAssembler<? super ProjectModuleDescriptor> bomAware = new BomAwareAssembler(pomAware, project.hashFunction());
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                repositories.putIfAbsent("maven",
                        MavenDefaultRepository.ofKeys(project.keys(), project.output())
                                .cached(project.keys(), project.output(), project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                repositories.putIfAbsent("module",
                        JenesisModuleRepository.ofKeys(project.keys(), project.output(), JenesisRepository.Scope.ARTIFACT)
                                .cached(project.keys(), project.output(), project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                repositories.putIfAbsent("OpenPGP", OpenPgpRepository.ofKeys(project.keys()));
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("maven", MavenPomResolver.ofKeys(project.keys()));
                resolvers.putIfAbsent("module", new MavenModuleResolver("maven",
                        MavenResolver.of(resolvers.get("maven")), repositories.get("module")));
                SequencedSet<String> modulesDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(modulesDeps::add);
                sub.addModule("modules", ModularProject.make(project.keys(), project.output(), project.root(),
                                "main",
                                "module",
                                _ -> true,
                                Collections.unmodifiableMap(repositories),
                                Collections.unmodifiableMap(resolvers),
                                project.pinning(),
                                true,
                                project.licenseFiles(Dependencies.SPDX),
                                project.boms(),
                project.signatures(),
                                (descriptor, mergedRepos, mergedResolvers) -> bomAware.apply(
                                        new ProjectModuleDescriptor(descriptor,
                                                configurations(modularConfigurationFolder(descriptor.location()), project.configuration(), project.profiles()),
                                                project.tests(),
                                                project.sources(),
                                                project.documentation(),
                                                project.pinning(),
                                                PathPlacement.INFERRED),
                                        mergedRepos,
                                        mergedResolvers)),
                        modulesDeps);
            }, METADATA);
            executor.addModule(STAGE, (stage, inherited) -> {
                stage.addStep("maven", MavenRepositoryStaging.ofKeys(project.keys()), inherited.sequencedKeySet());
                stage.addStep("modular", ModularStaging.ofKeys(project.keys()), inherited.sequencedKeySet());
                stage.addStep("packages", new ImageStaging("package").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("runtime", new ImageStaging("image"), inherited.sequencedKeySet());
                stage.addStep("layers", new ImageStaging("layers"), inherited.sequencedKeySet());
                stage.addStep("native", new ImageStaging("native").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("docker", new ImageStaging("docker"), inherited.sequencedKeySet());
                stage.addStep("reports", new ReportStaging(), inherited.sequencedKeySet());
            }, BUILD);
            executor.addModule(EXPORT, (export, _) -> {
                export.addStep("maven", MavenRepositoryExport.ofKeys(project.keys()), BuildExecutorModule.PREVIOUS + STAGE + "/maven");
                export.addStep("modular", JenesisModuleRepositoryExport.ofKeys(project.keys()), BuildExecutorModule.PREVIOUS + STAGE + "/modular");
            }, STAGE);
            String prefix = BUILD + "/modules/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(PIN,
                    new PinModule(project.root(),
                            "module-info.java",
                            (path, file) -> PinModuleInfo.ofKeys(project.keys(), project.output(), "module", path, List.of(file), project.hashFunction()),
                            project.hashFunction(),
                            project.keys(),
                            project.output()),
                    BUILD);
            executor.addStep(DEPENDENCIES, Tree.ofKeys(project.keys(), project.output()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, ReleaseModule.ofKeys(project.keys(), project.output(), project.root(), project.version()), STAGE);
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/module-" + BuildExecutorModule.encode(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout AUTO = (executor, project, assembler) -> of(project.root()).apply(executor, project, assembler);

        static Layout of(Path root) throws IOException {
            if (Files.isRegularFile(root.resolve("pom.xml"))) {
                return MAVEN;
            }
            List<Path> moduleInfos = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root)
                            && Files.exists(directory.resolve(BuildExecutor.SKIP_MARKER))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    Path name = file.getFileName();
                    if (name != null && "module-info.java".equals(name.toString())) {
                        moduleInfos.add(file);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            if (!moduleInfos.isEmpty()) {
                return MODULAR_TO_MAVEN;
            }
            throw new IllegalStateException(
                    "No build descriptor found under " + root.toAbsolutePath()
                            + " (expected a module-info.java or a pom.xml)");
        }
    }

    private SequencedSet<Path> licenseFiles(String file) {
        SequencedSet<Path> located = new LinkedHashSet<>();
        for (Path folder : Layout.configurations((Path) null, configuration, profiles)) {
            Path candidate = folder.resolve(file);
            if (Files.isRegularFile(candidate)) {
                located.add(candidate);
            }
        }
        return Collections.unmodifiableSequencedSet(located);
    }

    private BuildExecutorModule metadataModule() {
        Path base = root.toAbsolutePath().normalize();
        SequencedMap<String, Path> files = new LinkedHashMap<>();
        for (Path file : metadata) {
            Path absolute = (file.isAbsolute() ? file : root.resolve(file)).toAbsolutePath().normalize();
            Path relative = base.relativize(absolute);
            files.put(METADATA + "-" + BuildExecutorModule.encode(relative.toString()), relative);
        }
        return new MetadataModule(files, version, tag, revision, tree);
    }

    private record MetadataModule(SequencedMap<String, Path> files,
                                  String version,
                                  String tag,
                                  String revision,
                                  String tree) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            files.forEach((name, file) -> buildExecutor.addSource("file-" + name, Bind.asMetadata(), file));
            SequencedMap<String, String> values = new LinkedHashMap<>();
            if (version != null && !version.isEmpty()) {
                values.put("version", version);
            }
            if (tag != null) {
                values.put("scm.tag", tag);
            }
            if (revision != null) {
                values.put("scm.revision", revision);
            }
            if (tree != null) {
                values.put("scm.tree", tree);
            }
            if (!values.isEmpty()) {
                buildExecutor.addStep("command", new MetadataValues(values));
            }
        }
    }

    private record MetadataValues(SequencedMap<String, String> values) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties properties = new SequencedProperties();
            values.forEach(properties::setProperty);
            properties.store(context.next().resolve(BuildStep.METADATA));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record HelpModule(String layout, String assembler, Output output) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            output.out().accept(("""
                    %{title}Jenesis%{reset} - a Java build tool, written and configured in Java.

                    %{header}Active configuration:%{reset}
                      layout      %{name}%{layout}%{reset}
                      assembler   %{name}%{assembler}%{reset}

                    %{header}Getting started:%{reset}
                      Jenesis infers the build from the project's own layout, so there is no build
                      script to write. Pass selectors as command-line arguments; without one,
                      %{name}build%{reset} runs.

                        %{name}java build/jenesis/Make.java%{reset}         Build every module
                        %{name}java build/jenesis/Make.java stage%{reset}   Run another selector
                        %{name}jenesis stage%{reset}                        The same on the installed CLI

                      Every step prints a line naming its place in the build graph, and those
                      names are the selector grammar: run %{name}build%{reset} once and read them back.

                    %{header}Selectors:%{reset}
                      %{name}build%{reset}         Resolve, compile, package, and test every module
                      %{name}stage%{reset}         Stage produced artifacts into a local repository
                      %{name}export%{reset}        Export the staged repository as the build deliverable
                      %{name}pin%{reset}           Rewrite version/checksum pins into pom.xml or module-info.java
                      %{name}dependencies%{reset}  Print each module's resolved dependency graph
                      %{name}ide%{reset}           Generate IntelliJ IDEA, VS Code, and Eclipse project metadata
                      %{name}metadata%{reset}      Refresh the metadata module outputs
                      %{name}configuration%{reset} Print every setting with the value in force, one per line
                      %{name}properties%{reset}    Print only the %{name}-Djenesis.*%{reset} properties that are set
                      %{name}help%{reset}          Print this message
                      %{name}skill%{reset}         Print the briefing for a coding agent

                      %{name}+<module>%{reset} narrows %{name}build%{reset} to one module, not %{name}stage%{reset}, %{name}export%{reset} or
                      %{name}pin%{reset}, and %{name}+<module>/<step>%{reset} narrows it to a single step inside that
                      module; %{name}:%{reset} matches one path segment and %{name}::%{reset} any depth, as in
                      %{name}::/test%{reset}.

                    %{header}Watching what a step does:%{reset}
                      A step prints its name and its timing, and nothing of the tool underneath.
                      To see into one:
                      %{name}-Djenesis.print.process%{reset}   Stream each external tool's command line and its
                                                output to the console as it runs - the first thing
                                                to reach for when a step fails or will not finish.
                                                Narrow it to one tool with %{name}-Djenesis.print.<command>%{reset},
                                                as %{name}-Djenesis.print.javac%{reset} or %{name}-Djenesis.print.tests%{reset}
                      %{name}-Djenesis.print.command%{reset}   Print those command lines without their output
                      %{name}-Djenesis.print.fetch%{reset}     Name each artifact as it is fetched from a repository -
                                                what a long, quiet resolve is busy with
                      %{name}-Djenesis.print.cache%{reset}     Print each step served from or written to the cache
                      %{name}-Djenesis.print.signatures%{reset}
                                                Name each verified dependency with its signer, and each
                                                one no key covers
                      %{name}-Djenesis.print.checksum%{reset}  Print each step's input and output checksums, to
                                                see what made a step re-run
                      %{name}-Djenesis.print.progress=false%{reset}
                                                Drop the progress lines themselves

                      To always build this way, keep the setting in your own
                      %{name}~/.jenesis/jenesis.properties%{reset} rather than typing it (see below).

                    %{header}Configuration:%{reset}
                      Every setting is a %{name}-Djenesis.<area>.<name>%{reset} property. Three places set
                      them, each overriding the one before it:

                        %{name}~/.jenesis/jenesis.properties%{reset}  Your own defaults, for every project
                        %{name}jenesis.properties%{reset}             The project's own, committed with it
                        %{name}-Djenesis.<key>=<value>%{reset}        This one run

                      So a taste in output lives in your home folder and travels with you,
                      while a build server that has no such file is unaffected by it.
                      %{name}configuration%{reset} prints every setting with the value in force and whether
                      it is set or default, one per line to grep:

                        java build/jenesis/Make.java configuration | grep print

                      A module is configured by the %{name}@jenesis.*%{reset} tags on its %{name}module-info.java%{reset}
                      and by the files in its %{name}build.jenesis%{reset} folder.

                    %{header}Reading further:%{reset}
                      %{name}https://jenesis.build/tool%{reset}  The documentation
                      %{name}configuration%{reset}               Every setting, its value and what it does
                      %{name}skill%{reset}                       The whole tool as a briefing for a coding agent
                    """)
                    .replace("%{layout}", layout)
                    .replace("%{assembler}", assembler)
                    .replace("%{reset}", BuildExecutorCallback.RESET)
                    .replace("%{header}", BuildExecutorCallback.YELLOW)
                    .replace("%{name}", BuildExecutorCallback.CYAN)
                    .replace("%{title}", BuildExecutorCallback.GREEN));
        }
    }

    private record SkillModule(Path target, Output output) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            output.out().accept(("""
                    # Jenesis build tool - operating instructions

                    You are in a Jenesis-built Java project. Use this to drive the build, read its
                    state, and avoid the cache mistakes that catch agents. Full documentation:
                    https://jenesis.build/tool. `help` prints a short human orientation.

                    ## 1. Invoke the build

                      java build/jenesis/Make.java [selectors...]  source mode, always available
                      jenesis [selectors...]                       installed CLI
                      new Project(root).build(selectors...)        embedding it in Java

                    `Make` is the entry point, `Project` the configuration API and has no `main`. No
                    selector runs `build`; several, space-separated, run in one invocation.

                    The installed `jenesis` verifies `build/jenesis` against the released sources
                    named in `build/jenesis/jenesis.version` and refuses a tree that differs, while
                    `jenesis-make` runs the installed engine as it stands. Only source mode and
                    embedding run the project's vendored build code.

                    `Make` compiles the engine once and reuses those classes. Drive them yourself:

                      javac -d .jenesis/classes build/jenesis/Project.java
                      java -cp .jenesis/classes build.jenesis.Make [selectors...]

                    Pin the JDK a build runs on with -Djenesis.toolchain.version or the same key in
                    jenesis.properties, as 25, 25.0.3 or 25-temurin, its words matched against the
                    vendor and version in each JDK's release file. Make and Execute check the running
                    JVM first and otherwise relaunch on the newest match found under
                    jenesis.toolchain.searchpath, this system's usual JDK folders unless set, and only
                    the command line or ~/.jenesis/jenesis.properties may set it. Nothing is installed.

                    A project with its own entry point calls `new Make("build.Demo").run(selectors)`,
                    which returns the status to exit with. For a GraalVM native launcher, read the
                    documentation: it needs reachability metadata captured from a real build, a JDK
                    on PATH, and it cannot load foreign build modules.

                    ## 2. Take the layout the project infers

                      maven             pom.xml per module; jar plus pom.xml
                      modular           module-info.java per module; modular jar, no pom
                      modular_to_maven  module-info.java per module; modular jar plus generated pom

                    `auto` picks maven for a root pom.xml, else modular_to_maven; it never picks
                    plain modular. Override only with cause: -Djenesis.project.layout=<name>.

                    ## 3. Read target/, never delete it

                    Outputs live under %{target}:

                      build/<step path>/output/      what a step produced (jars, *.properties)
                      build/<step path>/supplement/  argument files and intermediates
                      stage/<layout>/output/         the tree `stage` builds and `export` publishes

                    Never delete target/ and never pass -Djenesis.executor.rebuild=true for a clean
                    slate: each step is keyed by its inputs, so a build re-runs exactly what changed
                    and wiping only forces repeated work. A folder ending in `~` is a running step's
                    staging area, renamed into place on success. One build at a time per target: the
                    root holds an exclusive .jenesis.lock and a second process fails fast.

                    ## 4. Turn a folder into a selector

                    target/build/ mirrors the build graph, so any folder under it is a selector: drop
                    the `target/` prefix and any trailing `/output` or `/supplement`.

                      target/build/maven/compose/module/<m>/produce/assemble/binary/artifacts/output
                      -> build/maven/compose/module/<m>/produce/assemble/binary/artifacts

                    ## 5. Address the graph

                      build stage export pin dependencies ide metadata configuration properties help skill
                          Top-level entry points; ide[/idea|/vscode|/eclipse] drills into one tool.
                      +<module>         module subgraph inside `build` (not stage/export/pin).
                                        <module> is the source folder holding its pom.xml or
                                        module-info.java; nested, foo/bar is written +foo+bar.
                      +<module>/<step>  one step in it, e.g. +foo+bar/compile/dependencies/resolved
                      pin/module-<path> one module's pins. `pin` is an entry point of its own and
                                        always rewrites every module, so `pin +foo` runs both and
                                        narrows nothing. <path> is the module's source folder,
                                        URL-encoded because selectors split on /: foo/bar is
                                        pin/module-foo%2Fbar.
                      :                 one path segment, e.g. build/:/java
                      ::                any depth, e.g. ::/test. Lenient: a typo matches nothing
                                        silently, so confirm a selector ran what you meant.

                    ## 6. Read per-module state from the properties files

                    Each per-module step writes these into its output folder. Read them rather than
                    inventing a side channel; the schemas are constants on the writing step.

                      metadata.properties   project, artifact, version, name, description, url,
                                            license.<id>.{name,url}, developer.<id>.{name,email},
                                            scm.{connection,developerConnection,url,tag,revision,tree}. Project-level
                                            overrides live in the file that
                                            -Djenesis.project.metadata=<path> names, conventionally
                                            project.properties.
                      module.properties     graph state: path, module, test, main
                      identity.properties   <repository>/<coordinate> -> path or empty
                      requires.properties   <group>/<scope>/<repository>/<coordinate> -> empty, or
                                            <algo>/<hex> when pinned
                      versions.properties   <group>/<repository>/<coordinate> -> <version>[ <algo>/<hex>]
                      boms.properties       bom/<...> -> [<version>[ <algo>/<hex>]] references and
                                            entry/<...> -> expanded entries; merged below versions
                      signatures.properties <algo>/<hex>, or Sigstore/<host>/<path> for an identity,
                                            -> space-separated coordinate tokens, each optionally
                                            ending in /* for a whole groupId
                      exclusions.properties <group>/<scope>/<repository>/<coordinate> -> comma-separated
                                            <groupId>/<artifactId>
                      inventory.properties  what staging reads: artifacts, sources, documentation,
                                            pom, runtime, prefixed
                      divergence.properties written by pin/divergence: <group>/<repository>/<coordinate>
                                            -> the versions it is pinned at and the modules holding
                                            each, for every coordinate pinned at more than one

                    ## 7. Bump serialVersionUID after editing a build step

                    A step is keyed by the digest of its serialized form plus every predecessor's
                    checksums. Project sources are always detected, but editing a step's *code* does
                    not change its serialized form, so its output stays cached. Bump that class's
                    `serialVersionUID` to force it to re-run; prefer that over executor.rebuild.

                    ## 8. Configure a module with @jenesis tags on module-info.java

                    Tags are read from the module's documentation comment in either form: the
                    traditional /** ... */ and the Markdown /// of JEP 467. `pin` writes back in
                    whichever form the comment already uses, and creates a /** ... */ when there is
                    no comment at all.

                    Token grammar, shared below: `<group>/<repo>/<coordinate>`, where a bare
                    `<module>` abbreviates `<group>/module/<module>` and `<groupId>/<artifactId>`
                    abbreviates `<group>/maven/<groupId>/<artifactId>`. A trailing `(<token>,...)`
                    guard applies a line only on a matching platform, with an unguarded line for the
                    same coordinate as fallback. Parentheses rather than brackets, because a
                    bracketed word is a link in a Markdown documentation comment and javadoc fails
                    on one it cannot resolve. alias, exclude and override are MODULAR_TO_MAVEN
                    only.

                      @jenesis.release <V>   Java release target (default: the running JDK's)
                      @jenesis.main <class>  main class
                      @jenesis.test [<module>|abstract]
                          Test variant of <module>. `abstract` supplies infrastructure only: declares
                          no tests, runs none, is never staged.
                      @jenesis.pin <token> <ver> [<algo>/<hex>] [(<guard>)]
                          Pin a version and optionally a content checksum.
                      @jenesis.signature <algo>/<hex> | Sigstore/<host>/<path> <token>... | [<group>/]signature-<name>.properties
                          Declare the OpenPGP key that signs these coordinates' artifacts - the
                          fingerprint first, because one key normally signs many. A Maven token may
                          end in /* to cover every artifact of one groupId. Carries no version: one
                          line covers every release that key signs, so it stays put across version
                          bumps. Nothing writes these lines: a key is checked against the upstream
                          project's published KEYS and added by hand, and a genuine key rotation is
                          accepted by adding the new fingerprint rather than deleting the old one.
                          Verification runs during dependency resolution, right after an artifact is
                          downloaded, and only when jenesis.dependency.signature asks for it:
                          declared verifies every coordinate a line covers, strict additionally
                          rejects one that no line covers or that publishes no signature, and the
                          default none verifies nothing, so a consumer who trusts the pins needs no
                          gpg at all. A coordinate's POM is verified with its artifact and must carry
                          the same signer, which closes the gap that POMs are read but never pinned.
                          The algorithm unsigned names no key, because there is none to name, and
                          its value says what to do when one turns up: unsigned/missing accepts a
                          coordinate that publishes no signature and fails once one appears, so an
                          upstream that starts signing is discovered rather than missed, and
                          unsigned/ignored never looks. Either lets strict hold everywhere else.
                          Key material comes from the local gpg keyring and is never fetched; an
                          unknown key is reported rather than retrieved. A lone
                          signature-<name>.properties token instead reads `<algo>/<hex>=<token>...`
                          lines from a local file in jenesis.project.signatures, the way
                          @jenesis.bom names a local pin-<name>.properties, so one vetted list
                          serves many modules; a list is never resolved from a repository, since one
                          that had to be downloaded would itself need verifying. A declaration never
                          switches verification on by itself, and when it is switched on it forks
                          gpgv, which must then be installed and on the PATH: a Java implementation
                          would have to be resolved from the repository being verified, and a
                          verifier downloaded on trust verifies nothing. gpgv reads a keyring file
                          and nothing else, so no home directory, agent or trust database takes
                          part; the build assembles that keyring from the declared fingerprints
                          alone, asking the repository registered under the algorithm a line
                          names: OpenPGP resolves through OpenPgpRepository, which asks the
                          jenesis.openpgp.uri servers for the fingerprint over HKP and holds what
                          it fetched in jenesis.openpgp.local. Those servers are asked in turn
                          because they speak one protocol; a key store that does not is registered
                          as its own repository rather than added to that list. A key the keyring holds is therefore a key a
                          line declares, and NO_PUBKEY means no line covers the signer. Registering
                          another repository under that name replaces where keys come from, so a
                          key store that is not an HTTP key server needs no change here. Fetching by fingerprint is not
                          trust in the server: the comparison is against the declared fingerprint,
                          so a server can withhold a key but never substitute one. The verifier is an ordinary
                          forked tool, so jenesis.print.gpgv shows each invocation,
                          jenesis.print.signatures names what was covered, and
                          jenesis.openpgp.command names a different binary.
                          Sigstore/<host>/<path> declares an identity rather than a key, for a
                          coordinate whose repository publishes a .sigstore.json beside the artifact:
                          nothing is fetched to check it, because the bundle carries the signing
                          certificate and the transparency log entry that records when it signed, and
                          the certificate is trusted for that recorded moment rather than for today -
                          it was valid for ten minutes and is long expired by the time anyone reads
                          it. The path is a prefix of the identity the certificate names, so a
                          declaration covers what lies below it and narrows a segment at a time:
                          Sigstore/github.com/acme covers every repository of an owner,
                          Sigstore/github.com/acme/lib one repository, and a workflow file may follow.
                          The ref a release was built from is never written, because that is the part
                          that moves, which is what lets one line cover every future release the way
                          a fingerprint does. The host names the issuer that must have authenticated
                          the identity, through jenesis.sigstore.issuers. What a bundle is checked
                          against is a sigstore-trusted-root.json read from jenesis.project.signatures
                          beside the key lists, and never resolved from a repository, for the reason a
                          key list is not: a trust root downloaded on trust verifies nothing.
                          Both forms may cover one coordinate, and each is then verified against
                          whatever it publishes; a coordinate covered only by an identity forks no
                          gpg at all, since the whole check is JDK cryptography in process.
                      @jenesis.alias <module> <groupId>/<artifactId>[/<type>[/<classifier>]]
                          Require a Maven artifact under a stable module name, so a non-modular jar
                          needs no derived automatic name. Carries no version: a pin or BOM entry
                          states it and is the place for a checksum; failing that the version the
                          closure already resolves is kept, and only a coordinate nothing else pulls
                          in is negotiated as LATEST. It also names the artifact a `requires` takes,
                          replacing the module index lookup, which is the way to pin down a name
                          several artifacts declare: alias org.bouncycastle.pg to bcpg-jdk18on and
                          neither the -debug nor the -lts build can land instead. Aliasing a name the
                          target already declares is allowed and does exactly that, so an alias need
                          not be dropped when its target grows a module name.
                      @jenesis.exclude <module> <groupId>/<artifactId>...
                          Drop transitive dependencies of <module>, each with the subtree it pulled
                          in, from the compile path, runtime path and generated pom alike. Repeated
                          lines add up. Excluding from a module that is not required is an error.
                          It is scoped to the path through <module>, as in Maven, so a coordinate
                          reached by two paths needs an exclusion on each. A sibling project module
                          is one such path: its generated pom is flat, so a consumer meets that
                          closure again through the sibling and excludes it there as well.
                      @jenesis.override <module> <carrier>...
                          Replace a module with the modules already carrying its packages, for a
                          dependency that shades another module (Tomcat Embed shades the Servlet
                          API). Jenesis substitutes an empty module requiring the carriers
                          transitively and drops every resolved artifact declaring the overridden
                          module, so the packages appear once. Reaches consumers through the
                          Jenesis-Overrides manifest header. A carrier nothing declares is an error.
                      @jenesis.layer <name> api <module> | <name> provider <token>
                          Keep a dependency private: resolve it, and its whole closure, into a run-time
                          ModuleLayer of its own rather than onto this module's path. Two versions of one
                          library then coexist with no package relocated - what shading is used for, without
                          rewriting a class file. `api` names the one module this module and the layer share;
                          `provider` names a coordinate the layer isolates, resolving it in the group
                          layer:<name>, which pins, verifies and reports like any other group. Every line
                          names which of the two it declares, so neither is the bare one.
                          The API module and everything it reaches are shared, so producer and consumer
                          exchange the very same classes and a service crosses as a plain interface call. A
                          dependency the API module reaches is exposed by it and cannot be isolated behind
                          it, which the build says rather than leaving to a LinkageError later.
                          The declaring module requires build.jenesis.launcher and asks for the layer by
                          name - Launcher.instance("<name>", Contract.class) - so its own consumers declare
                          nothing and need not know. Discovery runs to a fixpoint, so a module inside a
                          layer may declare one of its own; each layer is a child of its caller's, and a
                          test JVM is handed jlayer.modulepath.<name> like any deployment. That is a
                          jlayer.* key rather than a jenesis.* one: it configures no build, it is read by
                          the application a build produced.
                          A layer splits a module path and a class path as the application does: what
                          carries a module identity - a module-info, an Automatic-Module-Name, or a name
                          given in modules.properties - is resolved, and the long tail a legacy library
                          drags is the layer's own class path, which only its automatic modules read. A
                          layer holding no module at all is refused. A layer's jars are stored among the
                          application's rather than apart, each dependency
                          named once with its version, so a jar both need is stored once and loaded twice. It may not require what it isolates: an isolated module
                          is off its path, which javac reports on its own. The declaration reaches consumers
                          through the Jenesis-Layer manifest header. bundle and launcher both ship a layer;
                          native=true rejects one, because a layer is defined while the JVM runs.
                      @jenesis.bom <token> [<ver> [<algo>/<hex>]] [(<guard>)]
                          Import managed versions. A bare <module> names a BOM properties file in the
                          module repository, floating latest without a version; <groupId>/<artifactId>
                          names a Maven BOM whose <dependencyManagement> is imported with nested
                          import-scoped BOMs flattened, and takes no checksum because pom bytes are
                          not stable across repositories; [<group>/]pin-<name>.properties reads a
                          local file from jenesis.project.boms. Local @jenesis.pin lines override BOM
                          entries, and the last declared BOM wins a conflict.
                          A key in that file is <module>, <groupId>/<artifactId>, or a full
                          <repository>/<coordinate>, which is the form a type or a classifier needs:
                          maven/io.netty/netty-transport-native-epoll/jar/linux-x86_64, because
                          without the repository the groupId is read as one. An entry manages a
                          version wherever a closure reaches that coordinate, including modules that
                          never name it, so give every entry the checksum its version resolves to.
                      @jenesis.attach <token> [<arguments...>]
                          Attach a library as a -javaagent to this module's Execute run and its test
                          runs. The token carries no version: it comes from a declared dependency, a
                          pin or a BOM, else floats latest. Everything after the token is passed
                          verbatim as agent options. MAVEN modules declare the same lines in a
                          project-level <!--jenesis.attach ... --> comment, where a test-scoped match
                          attaches to test runs only and &#45;&#45; escapes a double dash.

                    ## 9. Activate a tool by dropping in its configuration file

                    A file in the module's build.jenesis location (its META-INF/build.jenesis/ folder
                    plus the project configuration locations) activates the feature; its contents
                    configure it. Generators read their inputs from META-INF/build.jenesis/ in the
                    sources, which the compiler never copies into the artifact, unless folders=<paths>
                    names other folders; each reads only the file kinds it compiles.

                      packaging.properties      jmod/jlink/bundle/launcher/native booleans, jpackage=<type>
                      test.properties           framework=junit-platform|junit4|testng, naming what
                                                this module's tests are written against; absent, it is
                                                inferred from the resolved dependencies
                      sbom.properties           CycloneDX format=json|xml|none; the SBOM is on by
                                                default, -Djenesis.sbom.cyclonedx=false disables it
                      bom.properties            publish the resolved closure as a repository BOM
                                                (Jenesis repository only)
                      licensing.properties      license check: allowed/denied/unknown/override.<coord>
                      vulnerability.properties  OSV check: severity, warn
                      jacoco.properties         test-coverage report
                      graal.properties          native-image reachability agent during the test run
                      pitest.properties         PIT mutation testing
                      japicmp.properties        japicmp compares the built jar against the last release of
                                                the module's own coordinate, or of baseline=<groupId>/
                                                <artifactId>[/<version>]; report-only until an
                                                error-on-<kind> key says otherwise
                                                (access, include, exclude, format, ignore-missing-classes,
                                                only-incompatible, only-modified, semantic-versioning,
                                                error-on-binary-incompatibility, ...)
                      errorprone.properties     turns on the Error Prone declared with
                                                @jenesis.plugin javac <coordinate>; javac forks so the
                                                compiler internals it reads can be exported to it
                                                (arguments, appended to -Xplugin:ErrorProne)
                      javaformat.properties     formatter=google|palantir
                      xjc.properties            JAXB: every .xsd compiled, every .xjb a binding; the
                                                generated package is compiled into the module
                                                (folders, package, catalog, arguments)
                      protoc.properties         every .proto compiled, the folders are the include
                                                path; protoc is a per-platform native executable, so
                                                each platform needs its own checksum pin
                                                (folders, classifier, plugins=<name>=<g>/<a>, arguments)
                      avro.properties           .avsc and .avpr, each in its own step (folders, arguments)
                      antlr.properties          ANTLR: every .g4 compiled into package=<name>, which
                                                also decides where the sources land
                                                (folders, package, arguments)
                      wsimport.properties       JAX-WS client from .wsdl; location=<url> is required
                                                and states where the description is served at run time
                                                (folders, package, catalog, arguments)
                      openapi.properties        OpenAPI Generator; a lone .yaml/.json is the
                                                specification, else name it with specification=<file>;
                                                only its source folder is collected
                                                (folders, generator, package, sources, arguments)
                      spdx.properties           extend the license alias/category tables
                      process-<tool>.properties extra arguments for a forked tool (javac, javadoc, jar,
                                                jlink, jpackage, ...); process-test.properties targets
                                                the test JVM, merged over process-java.properties

                    Linters and the ktlint/scalafmt formatters activate from their own native config
                    files instead (checkstyle.xml, pmd.xml, spotbugs-exclude.xml, .editorconfig,
                    .scalafmt.conf, ...).

                    ## 10. Override one build with -Djenesis.* properties

                    Run `configuration` for every setting with the value in force, one per line and
                    built to grep:

                      java build/jenesis/Make.java configuration                    every setting
                      java build/jenesis/Make.java configuration | grep test        one area
                      java build/jenesis/Make.java configuration | grep -F "[set]"  what this
                                                                                    project changed

                    Each line reads `jenesis.<key>=<value> [set|default|unset] <what it does>`, so
                    the catalogue and the state of the build come out together. jenesis.properties at
                    the project root sets the same keys, over your own
                    ~/.jenesis/jenesis.properties and under a -D; `properties` prints only the ones
                    that are set.

                    Two namespaces, split by who reads them. `jenesis.make.*` belongs to the entry
                    point: where the project is (root), which profiles to layer (profiles), where
                    the user-global file lives (global), and how the engine is compiled and reused
                    (compile, classes, daemon). `jenesis.toolchain.*` is read there too, to pick the
                    JDK the build runs on. `jenesis.project.*` belongs to the build itself.
                    Only -Djenesis.make.root can say where the project is - a properties file cannot,
                    because the root is what locates that file. Likewise only -Djenesis.make.global
                    moves the user-global file, so a project never chooses your own settings.

                    Reach for these without looking them up:

                      -Djenesis.print.process          stream a tool's command line and output as it
                                                       runs - the first move when a step fails or
                                                       will not finish; narrow it with
                                                       print.<command>, as print.javac
                      -Djenesis.dependency.pin=strict  fail the build on any unpinned artifact
                      -Djenesis.test.filter=<regex>    run one test class or method
                      -Djenesis.executor.rebuild       wipe target/ - avoid it, see section 7

                    ## 11. Run a built main class with Execute

                      java build/jenesis/Execute.java [args...]
                      jenesis-exec [args...]

                    Execute builds, finds the module declaring a main class and launches it on the
                    resolved runtime paths. Where more than one declares a main, name it with
                    -Djenesis.execute.module=<source folder> (nested: server/ui or server+ui) and
                    -Djenesis.execute.mainClass=<fqcn>. Wrap the program alone in Docker with
                    -Djenesis.execute.docker=true, plus .docker.image, .docker.mount (read-only),
                    .docker.mountWritable and .docker.env. Execute is its own entry point: build
                    selectors do not apply to it, nor its properties to a plain build.

                    To run a build, or a published program, inside your own JVM rather than as a
                    process of its own, the same three commands are java.util.spi.ToolProvider
                    services - jenesis-make, jenesis-exec and jpx:

                      ToolProvider.findFirst("jenesis-make").orElseThrow()
                              .run(out, err, "-Djenesis.project.version=1.0.0", "build");

                    Settings come first, as -Djenesis.* arguments, and configure that one run rather
                    than the JVM, so two runs in one program never clash; everything after them is
                    what the command line would take. A setting that replaces the process a build
                    runs in - toolchain.version, project.docker, execute.docker - is refused by name
                    there, and demo-56-tools-api shows the whole contract.

                    ## 12. Recommend pinning dependencies

                    Pin external dependencies so every machine resolves the same artifacts, and offer
                    to pin whenever you add or change one. `pin` records resolved versions and
                    checksums back into pom.xml (<dependencyManagement> with <!--Checksum/<algo>/<hex>-->
                    and a <!--jenesis.pin ... --> comment) or module-info.java (@jenesis.pin tags),
                    idempotently, refreshing only the lines matching the local platform. It covers the
                    whole project; to pin one module, name its step rather than adding +<module>.
                    -Djenesis.pin.file=<path> writes the project's whole closure to that properties
                    file instead of the declarations, in the grammar @jenesis.bom reads, which is how
                    a local bill of materials is refreshed rather than hand-edited. Each
                    module resolves alone, so nothing makes them agree: pin/divergence reports every
                    coordinate the tree pins at more than one version, which is the signal that a
                    shared version table is overdue. Enforce
                    coverage with -Djenesis.dependency.pin=strict; refresh with
                    -Djenesis.dependency.pin=ignore and the `pin` selector. A checksum says the bytes
                    did not change since they were vetted, not who produced them; @jenesis.signature
                    declares the OpenPGP key that signs a coordinate, and -Djenesis.dependency.signature
                    checks the detached signature with a local gpg as each artifact is downloaded, so
                    the run that establishes a pin is the run that proves who produced it. The same
                    tag declares a Sigstore identity instead, as Sigstore/<host>/<path>, for a
                    coordinate that publishes a .sigstore.json: a certificate that named the workflow
                    which built it and lived ten minutes, trusted for the moment a transparency log
                    recorded rather than for today, checked in process against a
                    sigstore-trusted-root.json held beside the key lists.

                    ## 13. Copy a demo: they are the recipe book

                    63 demos under `demo/`, each self-contained, runnable and minimal, ordered so the
                    sequence doubles as a tutorial; `demo/README.md` indexes them. Find the one
                    matching the task and copy its shape rather than inventing configuration.

                    If this project vendors Jenesis as a git submodule they are already on disk:
                    resolve the `build/jenesis` symlink and the demos sit under the root it points
                    into, conventionally `build/.upstream/demo/`. Otherwise read them at
                    https://github.com/jenesis/jenesis/tree/main/demo.

                      Project shapes     01 java-pom, 02 java-modular, 03 java-pom-multi,
                                         04 java-modular-multi, 19 module-layout (forcing MODULAR)
                      Starting a build   05 startup (what launching costs, and the daemon),
                                         61 toolchain (the JDK the build runs on)
                      Runnable output    06, 07 java-*-executable (jpackage), 08 bundle (jars for a
                                         stock JRE), 09 java-multi-release, 62 native-image (GraalVM)
                      Compiler control   10 javac-arguments (process-javac.properties),
                                         11 annotations (an annotation processor via @jenesis.plugin),
                                         12 error-prone (a javac plugin)
                      Generated sources  13 data-formats (xjc, protoc, avro),
                                         14 service-contracts (wsimport, OpenAPI),
                                         15 antlr (a grammar)
                      Dependencies       16 maven-exclusions, 17 bom, 18 module-alias,
                                         20 module-override, 21, 22 module-layers (a private
                                         dependency), 23, 24 platform-guard (classified and
                                         per-platform variants)
                      Trusting them      25 pinning (versions and checksums), 26 openpgp (a declared
                                         key), 27 sigstore (a declared identity, no key at all),
                                         28 sbom, 29 compliance (licenses), 30 vulnerabilities (OSV)
                      Quality gates      31 java-quality, 37 api-compatibility (japicmp),
                                         39 kotlin-quality, 42 scala-quality, 44 groovy-quality
                      Tests              32 test-framework (what the tests are written against),
                                         33 code-coverage (JaCoCo), 34 test-selection (incremental),
                                         35 pitest (mutation), 36 jmh (benchmark harness)
                      Other languages    38 kotlin, 40 kotlin-plugin, 41 scala, 43 groovy
                      Operating it       45 profiles, 46 build-cache, 47 docker-isolation,
                                         48 agents (@jenesis.attach)
                      Shipping it        57 code-signing (jarsigner), 58 publishing (Maven Central),
                                         59 module-convention (resolving what you published),
                                         60 reproducible (a jar checked against a recorded digest),
                                         63 jpx (run a released program without building)
                      Extending it       49 custom-assembler, 50 custom-jmod, 51 internal-module,
                                         52 external-module, 53 custom-maven, 54 custom-modular,
                                         55 custom-build (no Project at all),
                                         56 tools-api (a build inside another program's JVM)

                    ## 14. When stuck, read the source

                    Every public type lives under `sources/build/jenesis/` and is short enough to read
                    end to end; `tests/` documents the public API by example.

                      https://jenesis.build/tool          documentation, including the full reference
                      https://github.com/jenesis/jenesis  source, issues and releases
                    """).replace("%{target}", target.toAbsolutePath().normalize().toString()));
        }
    }

    private record PinModule(Path root,
                             String fileName,
                             BiFunction<String, Path, BuildStep> stepFactory,
                             Path file,
                             SequencedSet<Path> provided,
                             HashDigestFunction hashFunction,
                             Function<String, String> keys,
                             Output output)
            implements BuildExecutorModule {

        private PinModule(Path root,
                          String fileName,
                          BiFunction<String, Path, BuildStep> stepFactory,
                          HashDigestFunction hashFunction,
                          Function<String, String> keys,
                          Output output) {
            this(root,
                    fileName,
                    stepFactory,
                    fileFromKeys(keys, root),
                    providedFromKeys(keys, root),
                    hashFunction,
                    keys,
                    output);
        }

        private static Path fileFromKeys(Function<String, String> keys, Path root) {
            String value = SequencedProperties.getProperty(keys, "pin.file");
            return value == null ? null : root.resolve(value).normalize();
        }

        private static SequencedSet<Path> providedFromKeys(Function<String, String> keys, Path root) {
            SequencedSet<Path> provided = new LinkedHashSet<>();
            String value = SequencedProperties.getProperty(keys, "pin.provided");
            if (value != null) {
                for (String entry : value.split(",")) {
                    String candidate = entry.trim();
                    if (!candidate.isEmpty()) {
                        provided.add(root.resolve(candidate).normalize());
                    }
                }
            }
            return provided;
        }

        PinModule file(Path file) {
            return new PinModule(root, fileName, stepFactory, file, provided, hashFunction, keys, output);
        }

        PinModule provided(SequencedSet<Path> provided) {
            return new PinModule(root, fileName, stepFactory, file, provided, hashFunction, keys, output);
        }

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            SequencedSet<String> paths = new LinkedHashSet<>();
            for (Path folder : inherited.values()) {
                Path inventoryFile = folder.resolve(Inventory.INVENTORY);
                if (!Files.isRegularFile(inventoryFile)) {
                    continue;
                }
                SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
                for (String key : inventory.stringPropertyNames()) {
                    if (key.endsWith(".path")) {
                        paths.add(inventory.getProperty(key));
                    }
                }
            }
            if (file == null) {
                for (String path : paths) {
                    Path file = root.resolve(path).resolve(fileName);
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    buildExecutor.addStep("module-" + BuildExecutorModule.encode(path),
                            stepFactory.apply(path, file),
                            new LinkedHashSet<>(inherited.sequencedKeySet()));
                }
            } else {
                buildExecutor.addStep("file",
                        new Pins(paths, file, provided, hashFunction),
                        new LinkedHashSet<>(inherited.sequencedKeySet()));
            }
            buildExecutor.addStep("divergence",
                    new Divergence(paths, SequencedProperties.flag(keys, "print.divergence")
                            ? output.out()
                            : null),
                    new LinkedHashSet<>(inherited.sequencedKeySet()));
        }
    }

    private record Pins(SequencedSet<String> paths,
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
            for (String path : paths) {
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
                    if (!group.equals("main") || internal.contains(key)) {
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
                    entries.putIfAbsent(entry, value);
                }
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

    private static final class Divergence implements BuildStep {

        private final SequencedSet<String> paths;
        private final transient Consumer<String> printing;

        private Divergence(SequencedSet<String> paths, Consumer<String> printing) {
            this.paths = paths;
            this.printing = printing;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, SequencedMap<String, SequencedSet<String>>> versions = new TreeMap<>();
            for (String path : paths) {
                for (Map.Entry<String, Inventory.Dependency> entry
                        : Inventory.closure(arguments.values(), path).entrySet()) {
                    String key = entry.getKey();
                    int lastSlash = key.lastIndexOf('/');
                    if (lastSlash <= 0 || lastSlash == key.indexOf('/')) {
                        continue;
                    }
                    versions.computeIfAbsent(key.substring(0, lastSlash), _ -> new TreeMap<>())
                            .computeIfAbsent(key.substring(lastSlash + 1), _ -> new TreeSet<>())
                            .add(path.isEmpty() ? "." : path);
                }
            }
            SequencedProperties diverged = new SequencedProperties();
            versions.forEach((coordinate, byVersion) -> {
                if (byVersion.size() < 2) {
                    return;
                }
                List<String> rendered = new ArrayList<>();
                byVersion.forEach((version, modules) -> rendered.add(version + " (" + String.join(" ", modules) + ")"));
                diverged.setProperty(coordinate, String.join(", ", rendered));
                if (printing == null) {
                    return;
                }
                printing.accept("%s%-11s%s %s is pinned at %s".formatted(
                        BuildExecutorCallback.YELLOW,
                        "[DIVERGED]",
                        BuildExecutorCallback.RESET,
                        coordinate,
                        String.join(", ", rendered)));
            });
            diverged.store(context.next().resolve("divergence.properties"));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record PomAwareAssembler(MultiProjectAssembler<? super ProjectModuleDescriptor> base,
                                     String manifests,
                                     String prefix,
                                     boolean resolved) implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            return base.apply(descriptor.toInherited(), repositories, resolvers).mapBuild(delegate -> (sub, inherited) -> {
                sub.addModule("assemble", delegate, inherited.sequencedKeySet().stream());
                sub.addModule("describe", (describe, describeInherited) -> {
                            describe.addStep("pom", new Pom().resolved(resolved), describeInherited.sequencedKeySet().stream());
                            if (manifests != null) {
                                describe.addStep("identity", new MavenIdentity(prefix, manifests), "pom", manifests);
                            }
                        },
                        inherited.sequencedKeySet().stream());
            });
        }
    }

    private record BomAwareAssembler(MultiProjectAssembler<? super ProjectModuleDescriptor> base,
                                     HashDigestFunction hashFunction) implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            AssemblyDescriptor assembly = base.apply(descriptor, repositories, resolvers);
            if (BuildStep.locate(descriptor.configuration(), "bom.properties") == null) {
                return assembly;
            }
            return assembly.mapBuild(delegate -> (sub, inherited) -> {
                delegate.accept(sub, inherited);
                sub.addStep("bom", new Bom(hashFunction), inherited.sequencedKeySet().stream());
            });
        }
    }

    private record MavenIdentity(String prefix, String manifests) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path pomFile = arguments.get("pom").folder().resolve("pom.xml");
            Path folder = arguments.get(manifests).folder();
            SequencedProperties metadata = SequencedProperties.ofFiles(folder.resolve(BuildStep.METADATA));
            String groupId = metadata.getProperty("project");
            String artifactId = metadata.getProperty("artifact");
            String version = metadata.getProperty("version");
            String module = SequencedProperties.ofFiles(folder.resolve(BuildStep.MODULE)).getProperty("module");
            String pom = context.next().relativize(pomFile).toString().replace(File.separatorChar, '/');
            SequencedProperties identity = new SequencedProperties();
            identity.setProperty("maven/" + groupId + "/" + artifactId + "/" + version, "");
            identity.setProperty("maven/" + groupId + "/" + artifactId + "/pom/" + version, pom);
            identity.setProperty(prefix + "/" + module + ":pom", pom);
            identity.store(context.next().resolve(BuildStep.IDENTITY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    public Project(Path root) {
        this(resolvedRoot(root), Collections.unmodifiableSequencedSet(
                new LinkedHashSet<>(List.of(resolvedRoot(root).resolve("build.jenesis")))));
    }

    private Project(Path root, SequencedSet<Path> configuration) {
        this(root,
                Path.of("target"),
                Path.of(".jenesis", "artifacts"),
                Collections.emptyNavigableSet(),
                configuration,
                configuration,
                configuration,
                Collections.emptyNavigableSet(),
                null,
                new HashDigestFunction("SHA-256"),
                Layout.AUTO,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(BUILD))),
                new InferredMultiProjectAssembler(),
                BuildExecutor.Configuration::new,
                Map.of(),
                Map.of(),
                SequencedProperties.NONE,
                new Output());
    }

    private static Path resolvedRoot(Path root) {
        if (!root.isAbsolute()) {
            return root;
        }
        Path absoluteCwd = Path.of("").toAbsolutePath().normalize();
        Path absoluteRoot = root.normalize();
        if (!absoluteRoot.startsWith(absoluteCwd)) {
            return root;
        }
        Path relative = absoluteCwd.relativize(absoluteRoot);
        return relative.toString().isEmpty() ? Path.of(".") : relative;
    }

    public static Project ofKeys(Function<String, String> keys, Output output, Path root) {
        Project project = new Project(root).keys(keys).output(output);
        String configuration = SequencedProperties.getProperty(keys, "project.configuration");
        if (configuration != null) {
            project = project.configuration(locations(keys, configuration, project).toArray(Path[]::new));
            project = project.boms(project.configuration().toArray(Path[]::new));
            project = project.signatures(project.configuration().toArray(Path[]::new));
        }
        String boms = SequencedProperties.getProperty(keys, "project.boms");
        if (boms != null) {
            project = project.boms(locations(keys, boms, project).toArray(Path[]::new));
        }
        String signatures = SequencedProperties.getProperty(keys, "project.signatures");
        if (signatures != null) {
            project = project.signatures(locations(keys, signatures, project).toArray(Path[]::new));
        }
        String target = SequencedProperties.getProperty(keys, "project.target");
        if (target != null) {
            project = project.target(Path.of(target));
        }
        String artifacts = SequencedProperties.getProperty(keys, "project.artifacts");
        if (artifacts != null) {
            project = project.artifacts(Path.of(artifacts));
        }
        String cache = SequencedProperties.getProperty(keys, "project.cache");
        if (cache != null) {
            if (cache.contains("://")) {
                throw new IllegalArgumentException("jenesis.project.cache is a filesystem path, not a URI"
                        + " (use jenesis.cache.uri for a URI): " + cache);
            }
            project = project.cache(new BuildExecutorFileCache(project.root().resolve(cache.isEmpty()
                    ? Path.of(".jenesis", "cache")
                    : Path.of(cache))));
        }
        String layout = SequencedProperties.getProperty(keys, "project.layout");
        if (layout != null) {
            project = project.layout(switch (layout.toLowerCase(Locale.ROOT)) {
                case "auto" -> Layout.AUTO;
                case "maven" -> Layout.MAVEN;
                case "modular" -> Layout.MODULAR;
                case "modular_to_maven" -> Layout.MODULAR_TO_MAVEN;
                default -> throw new IllegalArgumentException(
                        "Unknown layout: " + layout + " (expected auto, maven, modular, or modular_to_maven)");
            });
        }
        String metadata = SequencedProperties.getProperty(keys, "project.metadata");
        if (metadata != null) {
            project = project.metadata(Arrays.stream(metadata.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Path::of)
                    .toArray(Path[]::new));
        }
        String digest = SequencedProperties.getProperty(keys, "project.digest");
        if (digest != null) {
            project = project.hashFunction(new HashDigestFunction(digest));
        }
        Boolean sources = SequencedProperties.flagOrNull(keys, "project.sources");
        if (sources != null) {
            project = project.sources(sources);
        }
        Boolean documentation = SequencedProperties.flagOrNull(keys, "project.documentation");
        if (documentation != null) {
            project = project.documentation(documentation);
        }
        String version = SequencedProperties.getProperty(keys, "project.version");
        if (version != null) {
            project = project.version(version);
        }
        String tag = SequencedProperties.getProperty(keys, "project.tag");
        if (tag != null) {
            project = project.tag(tag);
        }
        String revision = SequencedProperties.getProperty(keys, "project.revision");
        if (revision != null) {
            project = project.revision(revision);
        }
        String tree = SequencedProperties.getProperty(keys, "project.tree");
        if (tree != null) {
            project = project.tree(tree);
        }
        BuildExecutor.Configuration executor = BuildExecutor.Configuration.ofKeys(keys).output(output);
        return project.pinning(Pinning.ofKeys(keys))
                .assembler(InferredMultiProjectAssembler.ofKeys(keys, output))
                .configurator(() -> executor);
    }

    private static SequencedSet<Path> locations(Function<String, String> keys, String text, Project project) {
        SequencedSet<Path> target = new LinkedHashSet<>();
        locations(keys, text, project.root(), project.configuration(), new HashSet<>(), target);
        return target;
    }

    private static void locations(Function<String, String> keys,
                                  String text,
                                  Path root,
                                  SequencedSet<Path> defaults,
                                  Set<String> visited,
                                  SequencedSet<Path> target) {
        for (String entry : text.split(",")) {
            String candidate = entry.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith("@")) {
                String name = candidate.substring(1);
                if (name.isEmpty()) {
                    target.addAll(defaults);
                } else {
                    String value = SequencedProperties.getProperty(keys, name, System.getenv(name));
                    if (value == null) {
                        throw new IllegalStateException("Unresolved location reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular location reference: @" + name);
                    }
                    locations(keys, value, root, defaults, visited, target);
                    visited.remove(name);
                }
            } else {
                target.add(root.resolve(Path.of(candidate)));
            }
        }
    }

    public Project root(Path root) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project configuration(Path... configuration) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                new LinkedHashSet<>(List.of(configuration)),
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project signatures(Path... signatures) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                new LinkedHashSet<>(List.of(signatures)),
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project boms(Path... boms) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                new LinkedHashSet<>(List.of(boms)),
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project profiles(Path... profiles) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                new LinkedHashSet<>(List.of(profiles)),
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project target(Path target) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project artifacts(Path artifacts) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project cache(BuildExecutorCache cache) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project hashFunction(HashDigestFunction hashFunction) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project layout(Layout layout) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project tests(boolean tests) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project sources(boolean sources) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project documentation(boolean documentation) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project pinning(Pinning pinning) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project metadata(Path... metadata) {
        return new Project(root,
                target,
                artifacts,
                new LinkedHashSet<>(List.of(metadata)),
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project version(String version) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project tag(String tag) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project revision(String revision) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project tree(String tree) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project defaultTarget(String... defaultTarget) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(defaultTarget))),
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project assembler(MultiProjectAssembler<? super ProjectModuleDescriptor> assembler) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project repositories(Map<String, Repository> repositories) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project resolvers(Map<String, Resolver> resolvers) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project configurator(Supplier<BuildExecutor.Configuration> configurator) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project output(Output output) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public Project keys(Function<String, String> keys) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                keys,
                output);
    }

    public SequencedMap<String, Path> build(String... selectors) throws IOException {
        BuildExecutor.Configuration configuration = configurator.get();
        if (cache != null) {
            BuildExecutorCache configured = configuration.cache();
            configuration = configuration.cache(configured == null
                    ? cache
                    : new BuildExecutorLayeredCache(cache, configured));
        }
        BuildExecutor executor = configuration.of(target);
        Function<String, String> resolver = layout.apply(executor, this, assembler);
        return executor.execute(Arrays.stream(selectors.length == 0 ? defaultTarget.toArray(String[]::new) : selectors)
                .map(selector -> selector.startsWith("+") ? resolver.apply(selector.substring(1)) : selector)
                .toArray(String[]::new));
    }

    private void watch(String... selectors) throws IOException {
        Path absoluteRoot = root().toAbsolutePath().normalize();
        Set<Path> excluded = new LinkedHashSet<>();
        excluded.add(target().toAbsolutePath().normalize());
        if (artifacts() != null) {
            excluded.add(artifacts().toAbsolutePath().normalize());
        }
        new ProjectWatch(absoluteRoot, excluded, 200L, output).watch(() -> {
            try {
                build(selectors);
            } catch (Exception e) {
                output.out().accept("Build failed: " + e);
            }
        });
    }

    private static void printConfiguration(Function<String, String> keys, Output output) {
        String catalogue = """
                project.target|target|Folder the build writes its outputs to
                project.artifacts||Folder resolved dependencies and repository metadata are cached in
                project.layout|auto|auto|maven|modular|modular_to_maven; auto reads the project
                project.sources|false|Assemble a sources jar for every module
                project.documentation|false|Assemble a javadoc jar for every module
                project.version||Version stamped onto every produced artifact
                project.tag||SCM tag recorded in the generated POM and SBOM; empty for none
                project.revision||Source revision, such as a commit id, recorded in the SBOM; empty for none
                project.tree||Git tree id of the release, recorded in the SBOM as a SWHID; empty for none
                project.digest|SHA-256|Algorithm for pin and dependency checksums
                dependency.signature|none|Signatures verified after download: none|declared|strict
                openpgp.command|gpgv|Binary forked to verify detached OpenPGP signatures; a name is looked up on the PATH, a path is used as given
                openpgp.expiry|signing|An expired signing key: ignored accepts it, signing accepts what it signed before expiring, current rejects it
                sigstore.uri||URI of the Sigstore trust root; default: the published root of the public instance, carried as source
                sigstore.issuers|github.com=token.actions.githubusercontent.com|Comma-separated <host>=<issuer> pairs, both named without a scheme, for identity hosts whose OpenID Connect issuer is not the host itself
                project.metadata||Comma-separated extra metadata files
                project.configuration|build.jenesis|Comma-separated folders searched for tool configuration files; @ splices the default, and @<name> splices what jenesis.<name> or the environment variable <name> holds
                project.boms||Comma-separated locations of local pin-<name>.properties; default: the configuration folders
                project.signatures||Comma-separated locations of local signature-<name>.properties; default: the configuration folders
                project.watch|false|Rebuild the selected target whenever a source file changes
                project.cache||Project-local disk cache, layered in front of a remote; empty means .jenesis/cache
                project.docker|false|Run the whole build inside a container
                project.docker.image||Image for that container
                project.docker.mount||Extra read-only container mounts, host[:container],...
                project.docker.mountWritable||Extra writable container mounts
                project.docker.env||Host environment variables to forward, name[=value],...
                make.root|.|Folder Make looks for the project in; only settable on the command line
                make.profiles||Comma-separated profiles layered over jenesis.properties
                make.global||Folder holding the user-global .jenesis/jenesis.properties; default: the home folder; only settable on the command line
                make.provided||Settings that the files a project provides supplied, comma-separated and named without the jenesis. prefix; derived by Make and settable in no file, so that a repository or cache URL a project named is never sent a credential
                make.compile|true|Compile the build sources once and run from those classes
                make.classes|.jenesis/classes|Where those classes land, relative to the root
                make.daemon|false|Hand the build to a reused JVM; --stop as the only selector shuts it down
                daemon.idle|10800|Seconds an idle daemon waits before exiting
                daemon.options|-Xmx2g|JVM options for the daemon process itself, whitespace separated
                toolchain.version||JDK the build runs on, as 25, 25.0.3 or 25-temurin: the numbers match as a prefix, every word must be one of the vendor and version words in the JDK's release file, and a pre-release matches only when its word is named; Make and Execute relaunch on a match when the running JVM is none
                toolchain.searchpath|@|Comma-separated JDK folders searched for toolchain.version, absolute or under ~, * standing for any one folder name; @ splices this system's usual JDK locations and empty only checks the running JVM; settable only on the command line or in ~/.jenesis/jenesis.properties
                executor.concurrency|0|Run at most this many build steps at once; 0 is unbounded
                executor.timeout|PT0S|ISO-8601 timeout per step; PT0S is no timeout
                executor.digest|MD5|Algorithm behind the content and step hashes that drive the cache
                executor.rebuild|false|Wipe target/ before building; prefer letting the cache decide
                executor.aggregate|false|Collect independent step failures into one report
                process.concurrency|0|Run at most this many JDK tool runs at once; 0 is unbounded
                process.factory|tool|tool|fork; fork runs a JDK tool in a process of its own
                archive.timestamp|1980-02-01T00:00:00Z|ISO-8601 date-time with an offset recorded on every entry of the jars, jmods and zips the build writes; empty keeps the times the tools record and makes the archives unreproducible
                print.progress|true|The build progress lines
                print.process|false|Stream each external tool's command line and output as it runs
                print.<command>||The same for one tool only, as print.javac or print.tests
                print.command|false|Each external tool command line, without its output
                print.checksum|false|Each step's input and output checksums
                print.fetch|false|Each artifact downloaded from a repository
                print.cache|false|Each step served from or written to the build cache
                print.signatures|false|Each verified dependency with its signer, and each one no declaration covers
                print.pins|false|Each pin a refresh kept that no closure resolves, so it carries no checksum
                print.divergence|false|Each coordinate the project pins at more than one version
                print.aliases|false|Each module alias whose target already declares that name
                print.docker|true|The image notice when a build or run is containerized
                print.jreleaser|true|The JReleaser command line when a release runs
                dependency.pin||strict|versions|ignore; unset keeps existing pins and tolerates missing ones
                resolver.maven|maven|maven|closest|latest|release|stable|fail|managed: which version a Maven coordinate resolves to; stable skips pre-release qualifiers, fail rejects a coordinate two dependencies require at different versions, managed rejects that and any version only a dependency's POM names
                resolver.module|first|first|ignore|fail|managed: what to do with the versions a module-info records; fail rejects two requires that record different versions, managed rejects that and any module only another module's requires names
                pin.file||Write the whole project's pins to this properties file instead of the module declarations
                pin.provided||Comma-separated pin files whose entries this one leaves out, where the version and hash are the same
                pin.concurrency|(processor count)|Rewrite at most this many modules' pins at once; 0 is unbounded
                pin.checksum|true|Record content checksums in the pins that the pin selector writes
                pin.bom|keep|keep|flatten: whether pinning keeps BOM references or resolves them away
                platform.<token>||true adds a platform token and false removes one, selecting guarded pins
                repository.insecure|false|Allow plaintext http:// repository fetches; only the command line or ~/.jenesis/jenesis.properties may allow it, never a file a project provides
                repository.retries|2|Retries after a failed fetch; 0 disables
                repository.backoff|125|Initial retry backoff in milliseconds, doubling per attempt
                repository.connect.timeout|10000|Connect timeout for a repository fetch, in milliseconds
                repository.read.timeout|30000|Read timeout for a repository fetch, in milliseconds
                maven.uri||Maven remotes, comma-separated and queried left to right; a |<groupId> suffix, repeatable, asks a remote only for that group and the groups below it, and @<name> splices the chain that jenesis.<name> or the environment variable <name> holds (env MAVEN_REPOSITORY_URI)
                maven.local||Local Maven cache folder (env MAVEN_REPOSITORY_LOCAL)
                maven.token||Bearer token for the Maven remotes (env MAVEN_REPOSITORY_TOKEN); only the command line, ~/.jenesis/jenesis.properties or the environment may name one; a token the environment provides is sent only to the remotes the environment names, a remote a project's own files named is never sent one, and neither is the built-in public repository
                maven.segments|2|Leading dot-separated segments of a module name that form its Maven groupId, when a module is published or resolved by the coordinate convention; a shorter name becomes the groupId in full
                module.uri||Jenesis module remotes, likewise, where a |<module> suffix asks a remote only for that module and the modules whose name it prefixes, and a maven:[<segments>:]<uri> entry reads a remote as a Maven repository by the publishing convention, taking that many leading segments of a module name as its groupId where it names a count and jenesis.maven.segments where it does not (env JENESIS_REPOSITORY_URI)
                module.local||Local module cache folder (env JENESIS_REPOSITORY_LOCAL)
                module.token||Bearer token for the module remote (env JENESIS_REPOSITORY_TOKEN); likewise, and only the first remote of the chain is sent it, so a fallback mirror never sees it
                module.prerelease||Accept a pre-release when asking the module index for a module's newest version
                module.speculative||Accept a version the module index has not recorded but guesses exists
                openpgp.uri|keyserver.ubuntu.com, keys.openpgp.org|HKP key server roots, likewise, and @<name> splices what jenesis.<name> or the environment variable <name> holds; a server speaking another protocol is another repository (env OPENPGP_REPOSITORY_URI)
                openpgp.local|.jenesis/keys|Local key cache folder, one file per fingerprint (env OPENPGP_REPOSITORY_LOCAL)
                cache.uri||Build cache: a file:// folder, or an http(s):// cache server
                cache.project||Project name sent to a cache server (env JENESIS_CACHE_PROJECT)
                cache.key||Access key sent to a cache server (env JENESIS_CACHE_KEY); only the command line, ~/.jenesis/jenesis.properties or the environment may name one, and a cache server a project's own files named is not sent it
                cache.connect|PT1S|Connect timeout for a cache server
                cache.read|PT10S|Read timeout for a cache server
                cache.insecure|false|Permit the cache key over plaintext http off loopback; likewise yours alone to allow
                test.skip|false|Skip executing tests, still resolving what running them needs
                test.filter||Comma-separated <classRegex>[#<method>] entries restricting which tests run
                test.tag||Comma-separated tag expressions; only tests carrying one of them run
                test.force|false|Execute tests even where a previous run already covered them
                test.incremental||Run only the tests a change can reach; the value names the digest
                test.parallel|false|Let the engine execute the matched tests concurrently
                test.reporting|false|Write test reports into the module's reports/tests folder
                stage.tests|false|Stage test-variant artifacts alongside the main ones
                tree.format|full|full|compact: what the dependencies selector prints
                tree.tests|true|Include test-variant modules in that output
                execute.module||Module to run, named by its source folder (server/ui or server+ui)
                execute.mainClass||Main class to run, overriding the module's @jenesis.main
                execute.docker|false|Run the launched program in a container, independently of the build
                execute.docker.image||Image for that container
                execute.docker.mount||Extra read-only container mounts
                execute.docker.mountWritable||Extra writable container mounts
                execute.docker.env||Host environment variables to forward
                sbom.cyclonedx|true|Emit a CycloneDX SBOM; sbom.properties selects its format
                compliance|true|Run the license and vulnerability checks their configuration files activate
                source.checkstyle|true|Checkstyle, activated by a checkstyle.xml
                source.pmd|true|PMD, activated by a pmd.xml
                validator.spotbugs|true|SpotBugs, activated by a spotbugs-exclude.xml
                source.detekt|true|detekt, activated by a detekt.yml
                source.ktlint|true|ktlint linting, activated by an .editorconfig
                source.scalastyle|true|Scalastyle, activated by a scalastyle-config.xml
                source.scalafmt|true|scalafmt checking, activated by a .scalafmt.conf
                source.codenarc|true|CodeNarc, activated by a codenarc.groovy
                compile.errorprone|true|Error Prone over the javac plugin declared with @jenesis.plugin javac, activated by an errorprone.properties; javac forks to grant it the compiler internals it reads
                format.java|true|The Java formatter a javaformat.properties selects
                format.ktlint|true|ktlint formatting
                format.scalafmt|true|scalafmt formatting
                format.rewrite|false|Let the formatters rewrite sources instead of verifying them
                generate.xjc|true|JAXB generation, activated by an xjc.properties
                generate.protoc|true|protoc generation, activated by a protoc.properties
                generate.avro|true|Avro generation, activated by an avro.properties
                generate.wsimport|true|JAX-WS generation, activated by a wsimport.properties
                generate.openapi|true|OpenAPI generation, activated by an openapi.properties
                generate.antlr|true|ANTLR generation, activated by an antlr.properties
                observe.jacoco|true|JaCoCo coverage, activated by a jacoco.properties
                observe.native|true|native-image reachability agent, activated by a graal.properties
                mutate.pitest|true|PIT mutation testing, activated by a pitest.properties
                artifact.japicmp|true|japicmp API comparison, activated by a japicmp.properties
                jarsigner.keystore||Key store jarsigner signs the produced jar with, in place of the unsigned one; a release machine supplies it, and a build that names any jarsigner setting without it fails rather than shipping unsigned
                jarsigner.alias||Name of the key within that store
                jarsigner.storepass||Where that store's password is read from: env <variable>, or file <path>
                jarsigner.keypass||The same for the key's own password, where it has one of its own
                jarsigner.storetype||Type of the store, as jarsigner names it: PKCS12, JKS, ...
                jarsigner.tsa||Timestamp authority to stamp the signature with, so it outlives the certificate
                jarsigner.arguments||Further jarsigner arguments, whitespace separated
                jreleaser.executable|jreleaser|The JReleaser executable a release runs
                jreleaser.command|full-release|The JReleaser command a release runs
                jreleaser.config||JReleaser configuration file
                jreleaser.dryRun|true|Run JReleaser without actually publishing
                """;
        for (String line : catalogue.lines().toList()) {
            String[] entry = line.split("\\|", 3);
            String name = "jenesis." + entry[0];
            String value = keys.apply(entry[0]);
            String assignment = name + "=" + (value == null ? entry[1] : value);
            String state = value != null ? "[set]" : entry[1].isEmpty() ? "[unset]" : "[default]";
            String prefix = assignment + " ".repeat(Math.max(1, 46 - assignment.length())) + state;
            output.out().accept(prefix + " ".repeat(Math.max(1, 56 - prefix.length())) + entry[2]);
        }
    }

    SequencedMap<String, Path> doMain(String... selectors) throws IOException, InterruptedException {
        if (selectors.length == 1 && selectors[0].equals(CONFIGURATION)) {
            printConfiguration(keys, output);
            return Collections.emptyNavigableMap();
        }
        if (selectors.length == 1 && selectors[0].equals(PROPERTIES)) {
            SortedMap<String, String> properties = new TreeMap<>();
            for (String name : System.getProperties().stringPropertyNames()) {
                if (name.startsWith("jenesis.")) {
                    properties.put(name, System.getProperty(name));
                }
            }
            properties.forEach((name, value) -> output.out().accept(name + "=" + value));
            return Collections.emptyNavigableMap();
        }
        if (SequencedProperties.flag(keys, "project.watch")) {
            watch(selectors);
            return Collections.emptyNavigableMap();
        }
        if (SequencedProperties.flag(keys, "project.docker")) {
            SortedMap<String, String> properties = new TreeMap<>();
            for (String name : System.getProperties().stringPropertyNames()) {
                if (name.startsWith("jenesis.") && !name.startsWith("jenesis.project.docker")) {
                    properties.put(name, System.getProperty(name));
                }
            }
            String image = SequencedProperties.getProperty(keys, "project.docker.image");
            Path root = this.root().toAbsolutePath().normalize();
            DockerizedJava docker = image == null ? new DockerizedJava(root) : new DockerizedJava(root, image);
            for (Path path : List.of(this.target(), this.artifacts())) {
                Path absolute = (path.isAbsolute() ? path : root.resolve(path)).normalize();
                if (!absolute.startsWith(root)) {
                    docker = docker.mount(absolute, absolute.toString(), false);
                }
            }
            SequencedSet<Path> locations = new LinkedHashSet<>();
            locations.addAll(this.configuration());
            locations.addAll(this.boms());
            for (Path path : this.metadata()) {
                Path parent = (path.isAbsolute() ? path : root.resolve(path)).normalize().getParent();
                if (parent != null) {
                    locations.add(parent);
                }
            }
            for (Path location : locations) {
                Path absolute = (location.isAbsolute() ? location : root.resolve(location)).normalize();
                if (!absolute.startsWith(root) && Files.isDirectory(absolute)) {
                    docker = docker.mount(absolute, absolute.toString(), true);
                }
            }
            String cacheOverride = SequencedProperties.getProperty(keys, "project.cache");
            if (cacheOverride != null && !cacheOverride.contains("://")) {
                Path cache = root.resolve(cacheOverride.isEmpty()
                        ? Path.of(".jenesis", "cache")
                        : Path.of(cacheOverride)).normalize();
                if (!cache.startsWith(root)) {
                    docker = docker.mount(Files.createDirectories(cache), cache.toString(), false);
                }
            }
            String cacheUri = SequencedProperties.getProperty(keys, "cache.uri");
            if (cacheUri != null && cacheUri.startsWith("file:")) {
                Path cache = Path.of(URI.create(cacheUri)).toAbsolutePath().normalize();
                if (!cache.startsWith(root)) {
                    docker = docker.mount(Files.createDirectories(cache), cache.toString(), false);
                }
            }
            docker = docker.mounts(SequencedProperties.getProperty(keys, "project.docker.mount"), root, true);
            docker = docker.mounts(SequencedProperties.getProperty(keys, "project.docker.mountWritable"), root, false);
            docker = docker.envs(SequencedProperties.getProperty(keys, "project.docker.env"));
            String mavenRepositoryUri = SequencedProperties.getProperty(keys, "maven.uri", System.getenv("MAVEN_REPOSITORY_URI"));
            if (mavenRepositoryUri != null) {
                docker = docker.env("MAVEN_REPOSITORY_URI", mavenRepositoryUri);
            }
            String jenesisRepositoryUri = SequencedProperties.getProperty(keys, "module.uri", System.getenv("JENESIS_REPOSITORY_URI"));
            if (jenesisRepositoryUri != null) {
                docker = docker.env("JENESIS_REPOSITORY_URI", jenesisRepositoryUri);
            }
            String mavenRepositoryLocal = SequencedProperties.getProperty(keys, "maven.local", System.getenv("MAVEN_REPOSITORY_LOCAL"));
            Path mavenLocal = (mavenRepositoryLocal == null
                    ? Path.of(System.getProperty("user.home"), ".m2", "repository")
                    : Path.of(mavenRepositoryLocal)).toAbsolutePath().normalize();
            if (Files.isDirectory(mavenLocal)) {
                docker = docker.mount(mavenLocal, mavenLocal.toString(), true);
                docker = docker.env("MAVEN_REPOSITORY_LOCAL", mavenLocal.toString());
            }
            String openPgpRepositoryUri = SequencedProperties.getProperty(keys, "openpgp.uri", System.getenv("OPENPGP_REPOSITORY_URI"));
            if (openPgpRepositoryUri != null) {
                docker = docker.env("OPENPGP_REPOSITORY_URI", openPgpRepositoryUri);
            }
            String openPgpRepositoryLocal = SequencedProperties.getProperty(keys, "openpgp.local", System.getenv("OPENPGP_REPOSITORY_LOCAL"));
            if (openPgpRepositoryLocal != null) {
                Path openPgpLocal = Path.of(openPgpRepositoryLocal).toAbsolutePath().normalize();
                if (Files.isDirectory(openPgpLocal) && !openPgpLocal.startsWith(root)) {
                    docker = docker.mount(openPgpLocal, openPgpLocal.toString(), true);
                }
                docker = docker.env("OPENPGP_REPOSITORY_LOCAL", openPgpLocal.toString());
            }
            String jenesisRepositoryLocal = SequencedProperties.getProperty(keys, "module.local", System.getenv("JENESIS_REPOSITORY_LOCAL"));
            Path jenesisLocal = (jenesisRepositoryLocal == null
                    ? Path.of(System.getProperty("user.home"), ".jenesis")
                    : Path.of(jenesisRepositoryLocal)).toAbsolutePath().normalize();
            if (Files.isDirectory(jenesisLocal)) {
                docker = docker.mount(jenesisLocal, jenesisLocal.toString(), true);
                docker = docker.env("JENESIS_REPOSITORY_LOCAL", jenesisLocal.toString());
            }
            if (SequencedProperties.flag(keys, "print.docker", true)) {
                output.out().accept("Launching build within Docker image: " + docker.image());
            }
            int code = docker.execute("build/jenesis/Project.java", properties, selectors);
            if (code != 0) {
                System.exit(code);
            }
            return Collections.emptyNavigableMap();
        }
        return this.build(selectors);
    }

    public static SequencedMap<String, Path> perform(Function<String, String> keys,
                                                    Output output,
                                                    Path root,
                                                    SequencedSet<Path> profiles,
                                                    String... selectors) {
        try {
            return ofKeys(keys, output, root).profiles(profiles.toArray(Path[]::new)).doMain(selectors);
        } catch (Throwable t) {
            report(t, output);
            return null;
        }
    }

    public static int run(Function<String, String> keys,
                          Output output,
                          String mainClass,
                          Path root,
                          SequencedSet<Path> profiles,
                          String... selectors) {
        if (mainClass.equals(Project.class.getName())) {
            return perform(keys, output, root, profiles, selectors) == null ? 1 : 0;
        }
        try {
            Class.forName(mainClass, true, Project.class.getClassLoader())
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) selectors);
            return 0;
        } catch (Throwable t) {
            report(t, output);
            return 1;
        }
    }

    private static void report(Throwable t, Output output) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        trace.toString().lines().forEach(output.err());
        output.err().accept("");
        output.err().accept("The build failed with the error above. If you meant to look up how to"
                + " invoke Jenesis, pass `help` as the only argument on the command line, or `skill`"
                + " for an agent-oriented briefing.");
    }
}
