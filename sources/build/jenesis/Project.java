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
        SequencedSet<Path> profiles,
        BuildExecutorCache cache,
        HashDigestFunction hashFunction,
        Layout layout,
        boolean tests,
        boolean sources,
        boolean documentation,
        Pinning pinning,
        String version,
        SequencedSet<String> defaultTarget,
        MultiProjectAssembler<? super ProjectModuleDescriptor> assembler,
        Supplier<BuildExecutor.Configuration> configurator,
        Map<String, Repository> repositories,
        Map<String, Resolver> resolvers) {

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

        static SequencedSet<Path> licenseFiles(Project project, String file) {
            SequencedSet<Path> located = new LinkedHashSet<>();
            for (Path folder : configurations((Path) null, project.configuration(), project.profiles())) {
                Path candidate = folder.resolve(file);
                if (Files.isRegularFile(candidate)) {
                    located.add(candidate);
                }
            }
            return Collections.unmodifiableSequencedSet(located);
        }

        Layout MAVEN = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("maven", assembler.getClass().getName()));
            executor.addModule(SKILL, new SkillModule(project.target()));
            executor.addModule(METADATA, MetadataModule.toMetadataModule(project));
            MultiProjectAssembler<? super ProjectModuleDescriptor> pomAware = new PomAwareAssembler(assembler, null, null, false);
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                repositories.putIfAbsent("maven",
                        MavenDefaultRepository.of()
                                .cached(project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("maven", new MavenPomResolver());
                SequencedSet<String> mavenDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(mavenDeps::add);
                sub.addModule("maven", MavenProject.make(project.root(),
                                "main",
                                "maven",
                                Collections.unmodifiableMap(repositories),
                                Collections.unmodifiableMap(resolvers),
                                project.pinning(),
                                Layout.licenseFiles(project, Dependencies.SPDX),
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
                stage.addStep("maven", new MavenRepositoryStaging(), inherited.sequencedKeySet());
                stage.addStep("packages", new ImageStaging("package").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("native", new ImageStaging("native").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("docker", new ImageStaging("docker"), inherited.sequencedKeySet());
                stage.addStep("reports", new ReportStaging(), inherited.sequencedKeySet());
            }, BUILD);
            executor.addModule(EXPORT, (export, _) -> export.addStep(
                    "maven", new MavenRepositoryExport(), BuildExecutorModule.PREVIOUS + STAGE + "/maven"), STAGE);
            String prefix = BUILD + "/maven/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(PIN, new PinModule(project.root(),
                    "pom.xml",
                    (path, file) -> new PinPom("maven", path, List.of(file), project.hashFunction())), BUILD);
            executor.addModule(DEPENDENCIES, (tree, inherited) -> tree.addStep(
                    "tree", new Tree(), inherited.sequencedKeySet()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, new ReleaseModule(project.root(), project.version()), STAGE);
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/module-" + BuildExecutorModule.encode(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout MODULAR = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("modular", assembler.getClass().getName()));
            executor.addModule(SKILL, new SkillModule(project.target()));
            executor.addModule(METADATA, MetadataModule.toMetadataModule(project));
            MultiProjectAssembler<? super ProjectModuleDescriptor> bomAware = new BomAwareAssembler(assembler, project.hashFunction());
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                repositories.putIfAbsent("module",
                        JenesisModuleRepository.of(JenesisRepository.Scope.MODULE)
                                .cached(project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("module", new ModularJarResolver(false));
                SequencedSet<String> modulesDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(modulesDeps::add);
                sub.addModule("modules", ModularProject.make(project.root(),
                                "main",
                                "module",
                                _ -> true,
                                Collections.unmodifiableMap(repositories),
                                Collections.unmodifiableMap(resolvers),
                                project.pinning(),
                                true,
                                Layout.licenseFiles(project, Dependencies.SPDX),
                                project.boms(),
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
                stage.addStep("modular", new ModularStaging(), inherited.sequencedKeySet());
                stage.addStep("packages", new ImageStaging("package").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("runtime", new ImageStaging("image"), inherited.sequencedKeySet());
                stage.addStep("native", new ImageStaging("native").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("docker", new ImageStaging("docker"), inherited.sequencedKeySet());
                stage.addStep("reports", new ReportStaging(), inherited.sequencedKeySet());
            }, BUILD);
            executor.addModule(EXPORT, (export, _) -> export.addStep(
                    "modular", new JenesisModuleRepositoryExport(), BuildExecutorModule.PREVIOUS + STAGE + "/modular"), STAGE);
            String prefix = BUILD + "/modules/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(PIN, new PinModule(project.root(), "module-info.java",
                    (path, file) -> new PinModuleInfo("module", path, List.of(file), project.hashFunction())), BUILD);
            executor.addModule(DEPENDENCIES, (tree, inherited) -> tree.addStep(
                    "tree", new Tree(), inherited.sequencedKeySet()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, new ReleaseModule(project.root(), project.version()), STAGE);
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/module-" + BuildExecutorModule.encode(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout MODULAR_TO_MAVEN = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("modular_to_maven", assembler.getClass().getName()));
            executor.addModule(SKILL, new SkillModule(project.target()));
            executor.addModule(METADATA, MetadataModule.toMetadataModule(project));
            MultiProjectAssembler<? super ProjectModuleDescriptor> pomAware = new PomAwareAssembler(assembler,
                    BuildExecutorModule.PREVIOUS.repeat(2) + MultiProjectModule.MANIFESTS,
                    "module",
                    true);
            MultiProjectAssembler<? super ProjectModuleDescriptor> bomAware = new BomAwareAssembler(pomAware, project.hashFunction());
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                repositories.putIfAbsent("maven",
                        MavenDefaultRepository.of()
                                .cached(project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                repositories.putIfAbsent("module",
                        JenesisModuleRepository.of(JenesisRepository.Scope.ARTIFACT)
                                .cached(project.artifacts() == null ? null : Files.createDirectories(project.artifacts())));
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("maven", new MavenPomResolver());
                resolvers.putIfAbsent("module", new MavenModuleResolver("maven",
                        MavenResolver.of(resolvers.get("maven")), repositories.get("module")));
                SequencedSet<String> modulesDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(modulesDeps::add);
                sub.addModule("modules", ModularProject.make(project.root(),
                                "main",
                                "module",
                                _ -> true,
                                Collections.unmodifiableMap(repositories),
                                Collections.unmodifiableMap(resolvers),
                                project.pinning(),
                                true,
                                Layout.licenseFiles(project, Dependencies.SPDX),
                                project.boms(),
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
                stage.addStep("maven", new MavenRepositoryStaging(), inherited.sequencedKeySet());
                stage.addStep("modular", new ModularStaging(), inherited.sequencedKeySet());
                stage.addStep("packages", new ImageStaging("package").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("runtime", new ImageStaging("image"), inherited.sequencedKeySet());
                stage.addStep("native", new ImageStaging("native").noFolder(true), inherited.sequencedKeySet());
                stage.addStep("docker", new ImageStaging("docker"), inherited.sequencedKeySet());
                stage.addStep("reports", new ReportStaging(), inherited.sequencedKeySet());
            }, BUILD);
            executor.addModule(EXPORT, (export, _) -> {
                export.addStep("maven", new MavenRepositoryExport(), BuildExecutorModule.PREVIOUS + STAGE + "/maven");
                export.addStep("modular", new JenesisModuleRepositoryExport(), BuildExecutorModule.PREVIOUS + STAGE + "/modular");
            }, STAGE);
            String prefix = BUILD + "/modules/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(PIN,
                    new PinModule(project.root(),
                            "module-info.java",
                            (path, file) -> new PinModuleInfo("module", path, List.of(file), project.hashFunction())),
                    BUILD);
            executor.addStep(DEPENDENCIES, new Tree(), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, new ReleaseModule(project.root(), project.version()), STAGE);
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

    private record MetadataModule(SequencedMap<String, Path> files,
                                  String version) implements BuildExecutorModule {

        static BuildExecutorModule toMetadataModule(Project project) {
            Path root = project.root().toAbsolutePath().normalize();
            SequencedMap<String, Path> files = new LinkedHashMap<>();
            for (Path file : project.metadata()) {
                Path absolute = (file.isAbsolute() ? file : project.root().resolve(file)).toAbsolutePath().normalize();
                Path relative = root.relativize(absolute);
                files.put(METADATA + "-" + BuildExecutorModule.encode(relative.toString()), relative);
            }
            return new MetadataModule(files, project.version());
        }

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            files.forEach((name, file) -> buildExecutor.addSource("file-" + name, Bind.asMetadata(), file));
            if (version != null && !version.isEmpty()) {
                SequencedMap<String, String> values = new LinkedHashMap<>();
                values.put("version", version);
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

    private record HelpModule(String layout, String assembler) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            System.out.println(("""
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

                      %{name}+<module>%{reset} narrows the build to one module and %{name}+<module>/<step>%{reset} to a
                      single step inside it; %{name}:%{reset} matches one path segment and %{name}::%{reset} any depth,
                      as in %{name}::/test%{reset}.

                    %{header}Watching what a step does:%{reset}
                      A step prints its name and its timing, and nothing of the tool underneath.
                      To see into one:
                      %{name}-Djenesis.print.process%{reset}   Stream each external tool's command line and its
                                                output to the console as it runs - the first thing
                                                to reach for when a step fails or will not finish.
                                                Narrow it to one tool with %{name}-Djenesis.print.<command>%{reset},
                                                as %{name}-Djenesis.print.javac%{reset} or %{name}-Djenesis.print.tests%{reset}
                      %{name}-Djenesis.print.command%{reset}   Print those command lines without their output
                      %{name}-Djenesis.print.fetch%{reset}     Print each artifact downloaded from a repository
                      %{name}-Djenesis.print.cache%{reset}     Print each step served from or written to the cache
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

    private record SkillModule(Path target) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            System.out.println(("""
                    # Jenesis build tool - operating instructions

                    You are in a Jenesis-built Java project. Use this to drive the build, read its
                    state, and avoid the cache mistakes that catch agents. Full documentation:
                    https://jenesis.build/tool. `help` prints a short human orientation.

                    ## 1. Invoke the build

                      java build/jenesis/Make.java [selectors...]  source mode, always available
                      jenesis [selectors...]                       installed CLI
                      Project.build(selectors...)                  embedding it in Java

                    `Make` is the entry point, `Project` the configuration API and has no `main`. No
                    selector runs `build`; several, space-separated, run in one invocation.

                    The installed `jenesis` verifies `build/jenesis` against the released sources
                    named in `build/jenesis/jenesis.version` and refuses a tree that differs, while
                    `jenesis-make` runs the installed engine as it stands. Only source mode and
                    embedding run the project's vendored build code.

                    `Make` compiles the engine once and reuses those classes. Drive them yourself:

                      javac -d .jenesis/tool build/jenesis/Project.java
                      java -cp .jenesis/tool build.jenesis.Make [selectors...]

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
                      :                 one path segment, e.g. build/:/java
                      ::                any depth, e.g. ::/test. Lenient: a typo matches nothing
                                        silently, so confirm a selector ran what you meant.

                    ## 6. Read per-module state from the properties files

                    Each per-module step writes these into its output folder. Read them rather than
                    inventing a side channel; the schemas are constants on the writing step.

                      metadata.properties   project, artifact, version, name, description, url,
                                            license.<id>.{name,url}, developer.<id>.{name,email},
                                            scm.{connection,developerConnection,url}. Project-level
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
                      exclusions.properties <group>/<scope>/<repository>/<coordinate> -> comma-separated
                                            <groupId>/<artifactId>
                      inventory.properties  what staging reads: artifacts, sources, documentation,
                                            pom, runtime, prefixed

                    ## 7. Bump serialVersionUID after editing a build step

                    A step is keyed by the digest of its serialized form plus every predecessor's
                    checksums. Project sources are always detected, but editing a step's *code* does
                    not change its serialized form, so its output stays cached. Bump that class's
                    `serialVersionUID` to force it to re-run; prefer that over executor.rebuild.

                    ## 8. Configure a module with @jenesis tags on module-info.java

                    Token grammar, shared below: `<group>/<repo>/<coordinate>`, where a bare
                    `<module>` abbreviates `<group>/module/<module>` and `<groupId>/<artifactId>`
                    abbreviates `<group>/maven/<groupId>/<artifactId>`. A trailing `[<token>,...]`
                    guard applies a line only on a matching platform, with an unguarded line for the
                    same coordinate as fallback. alias, exclude and override are MODULAR_TO_MAVEN
                    only.

                      @jenesis.release <V>   Java release target
                      @jenesis.main <class>  main class
                      @jenesis.test [<module>|abstract]
                          Test variant of <module>. `abstract` supplies infrastructure only: declares
                          no tests, runs none, is never staged.
                      @jenesis.pin <token> <ver> [<algo>/<hex>] [<guard>]
                          Pin a version and optionally a content checksum.
                      @jenesis.alias <module> <groupId>/<artifactId>[/<type>[/<classifier>]]
                          Require a Maven artifact under a stable module name, so a non-modular jar
                          needs no derived automatic name. Carries no version: a pin or BOM entry
                          states it and is the place for a checksum; failing that the version the
                          closure already resolves is kept, and only a coordinate nothing else pulls
                          in is negotiated as LATEST.
                      @jenesis.exclude <module> <groupId>/<artifactId>...
                          Drop transitive dependencies of <module>, each with the subtree it pulled
                          in, from the compile path, runtime path and generated pom alike. Repeated
                          lines add up. Excluding from a module that is not required is an error.
                      @jenesis.override <module> <carrier>...
                          Replace a module with the modules already carrying its packages, for a
                          dependency that shades another module (Tomcat Embed shades the Servlet
                          API). Jenesis substitutes an empty module requiring the carriers
                          transitively and drops every resolved artifact declaring the overridden
                          module, so the packages appear once. Reaches consumers through the
                          Jenesis-Overrides manifest header. A carrier nothing declares is an error.
                      @jenesis.bom <token> [<ver> [<algo>/<hex>]] [<guard>]
                          Import managed versions. A bare <module> names a BOM properties file in the
                          module repository, floating latest without a version; <groupId>/<artifactId>
                          names a Maven BOM whose <dependencyManagement> is imported with nested
                          import-scoped BOMs flattened, and takes no checksum because pom bytes are
                          not stable across repositories; [<group>/]pin-<name>.properties reads a
                          local file from jenesis.project.boms. Local @jenesis.pin lines override BOM
                          entries, and the last declared BOM wins a conflict.
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
                      sbom.properties           CycloneDX format=json|xml|none; the SBOM is on by
                                                default, -Djenesis.sbom.cyclonedx=false disables it
                      bom.properties            publish the resolved closure as a repository BOM
                                                (Jenesis repository only)
                      licensing.properties      license check: allowed/denied/unknown/override.<coord>
                      vulnerability.properties  OSV check: severity, warn
                      jacoco.properties         test-coverage report
                      graal.properties          native-image reachability agent during the test run
                      pitest.properties         PIT mutation testing
                      javaformat.properties     formatter=google|palantir
                      xjc.properties            JAXB: every .xsd compiled, every .xjb a binding; the
                                                generated package is compiled into the module
                                                (folders, package, catalog, arguments)
                      protoc.properties         every .proto compiled, the folders are the include
                                                path; protoc is a per-platform native executable, so
                                                each platform needs its own checksum pin
                                                (folders, classifier, plugins=<name>=<g>/<a>, arguments)
                      avro.properties           .avsc and .avpr, each in its own step (folders, arguments)
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

                    ## 12. Recommend pinning dependencies

                    Pin external dependencies so every machine resolves the same artifacts, and offer
                    to pin whenever you add or change one. `pin` records resolved versions and
                    checksums back into pom.xml (<dependencyManagement> with <!--Checksum/<algo>/<hex>-->
                    and a <!--jenesis.pin ... --> comment) or module-info.java (@jenesis.pin tags),
                    idempotently, refreshing only the lines matching the local platform. Enforce
                    coverage with -Djenesis.dependency.pin=strict; refresh with
                    -Djenesis.dependency.pin=ignore and the `pin` selector.

                    ## 13. Copy a demo: they are the recipe book

                    51 demos under `demo/`, each self-contained, runnable and minimal, ordered so the
                    sequence doubles as a tutorial; `demo/README.md` indexes them. Find the one
                    matching the task and copy its shape rather than inventing configuration.

                    If this project vendors Jenesis as a git submodule they are already on disk:
                    resolve the `build/jenesis` symlink and the demos sit under the root it points
                    into, conventionally `.jenesis/upstream/demo/`. Otherwise read them at
                    https://github.com/jenesis/jenesis/tree/main/demo.

                      Project shapes     01 java-pom, 02 java-modular, 03 java-pom-multi,
                                         04 java-modular-multi, 32 module-layout (forcing MODULAR)
                      Runnable output    05, 06 java-*-executable (jpackage), 07 bundle (jars for a
                                         stock JRE), 08 java-multi-release, 48 native-image (GraalVM)
                      Compiler control   09 javac-arguments (process-javac.properties),
                                         10 annotations (an annotation processor via @jenesis.plugin)
                      Generated sources  11 data-formats (xjc, protoc, avro),
                                         12 service-contracts (wsimport, OpenAPI)
                      Other languages    18 kotlin, 20 kotlin-plugin, 21 scala, 23 groovy
                      Quality gates      13 java-quality, 19 kotlin-quality, 22 scala-quality,
                                         24 groovy-quality
                      Tests              25 code-coverage (JaCoCo), 26 test-selection (incremental),
                                         27 pitest (mutation), 28 jmh (benchmark harness)
                      Dependencies       29 agents (@jenesis.attach), 30 maven-exclusions, 31 bom,
                                         33 module-classifier, 34 module-alias, 35 module-override,
                                         36, 37 platform-guard (per-platform variants)
                      Supply chain       14 sbom, 15 compliance (licenses), 16 vulnerabilities (OSV),
                                         17 profiles, 46 supply-chain-security (what must fail)
                      Extending it       38 custom-assembler, 39 custom-jmod, 40 internal-module,
                                         41 external-module, 42 custom-maven, 43 custom-modular,
                                         44 custom-build (no Project at all)
                      Operating it       45 docker-isolation, 47 publishing (Maven Central),
                                         49 build-cache, 50 jpx (run a released program without
                                         building), 51 startup (what launching costs)

                    ## 14. When stuck, read the source

                    Every public type lives under `sources/build/jenesis/` and is short enough to read
                    end to end; `tests/` documents the public API by example.

                      https://jenesis.build/tool          documentation, including the full reference
                      https://github.com/jenesis/jenesis  source, issues and releases
                    """).replace("%{target}", target.toAbsolutePath().normalize().toString()));
        }
    }

    private record PinModule(Path root, String fileName, BiFunction<String, Path, BuildStep> stepFactory)
            implements BuildExecutorModule {

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
            for (String path : paths) {
                Path file = root.resolve(path).resolve(fileName);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                buildExecutor.addStep("module-" + BuildExecutorModule.encode(path),
                        stepFactory.apply(path, file),
                        new LinkedHashSet<>(inherited.sequencedKeySet()));
            }
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

    public Project() {
        Path resolvedRoot = Path.of(".");
        String rootOverride = System.getProperty("jenesis.project.root");
        if (rootOverride != null) {
            resolvedRoot = Path.of(rootOverride);
        }
        if (resolvedRoot.isAbsolute()) {
            Path absoluteCwd = Path.of("").toAbsolutePath().normalize();
            Path absoluteRoot = resolvedRoot.normalize();
            if (absoluteRoot.startsWith(absoluteCwd)) {
                Path relative = absoluteCwd.relativize(absoluteRoot);
                resolvedRoot = relative.toString().isEmpty() ? Path.of(".") : relative;
            }
        }
        String configurationOverride = System.getProperty("jenesis.project.configuration");
        SequencedSet<Path> defaultConfiguration = new LinkedHashSet<>(List.of(
                resolvedRoot.resolve("build.jenesis")));
        SequencedSet<Path> resolvedConfiguration;
        if (configurationOverride == null) {
            resolvedConfiguration = defaultConfiguration;
        } else {
            resolvedConfiguration = new LinkedHashSet<>();
            locations(configurationOverride,
                    resolvedRoot,
                    defaultConfiguration,
                    new HashSet<>(),
                    resolvedConfiguration);
        }
        String bomsOverride = System.getProperty("jenesis.project.boms");
        SequencedSet<Path> resolvedBoms;
        if (bomsOverride == null) {
            resolvedBoms = resolvedConfiguration;
        } else {
            resolvedBoms = new LinkedHashSet<>();
            locations(bomsOverride, resolvedRoot, resolvedConfiguration, new HashSet<>(), resolvedBoms);
        }
        String profilesOverride = System.getProperty("jenesis.project.properties");
        SequencedSet<Path> resolvedProfiles = profilesOverride == null
                ? Collections.emptyNavigableSet()
                : Arrays.stream(profilesOverride.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.endsWith(".properties")
                        ? value.substring(0, value.length() - ".properties".length())
                        : value)
                .map(Path::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Path resolvedTarget = Path.of("target");
        String targetOverride = System.getProperty("jenesis.project.target");
        if (targetOverride != null) {
            resolvedTarget = Path.of(targetOverride);
        }
        Path resolvedArtifacts = Path.of(".jenesis", "artifacts");
        String artifactsOverride = System.getProperty("jenesis.project.artifacts");
        if (artifactsOverride != null) {
            resolvedArtifacts = Path.of(artifactsOverride);
        }
        String cacheOverride = System.getProperty("jenesis.project.cache");
        if (cacheOverride != null && cacheOverride.contains("://")) {
            throw new IllegalArgumentException(
                    "jenesis.project.cache is a filesystem path, not a URI (use jenesis.cache.uri for a URI): " + cacheOverride);
        }
        BuildExecutorCache resolvedCache = cacheOverride == null
                ? null
                : new BuildExecutorFileCache(resolvedRoot.resolve(cacheOverride.isEmpty()
                        ? Path.of(".jenesis", "cache")
                        : Path.of(cacheOverride)));
        String layoutOverride = System.getProperty("jenesis.project.layout");
        Layout resolvedLayout = layoutOverride == null ? Layout.AUTO : switch (layoutOverride.toLowerCase(Locale.ROOT)) {
            case "auto" -> Layout.AUTO;
            case "maven" -> Layout.MAVEN;
            case "modular" -> Layout.MODULAR;
            case "modular_to_maven" -> Layout.MODULAR_TO_MAVEN;
            default -> throw new IllegalArgumentException(
                    "Unknown layout: " + layoutOverride + " (expected auto, maven, modular, or modular_to_maven)");
        };
        String metadataOverride = System.getProperty("jenesis.project.metadata");
        SequencedSet<Path> resolvedMetadata = metadataOverride == null
                ? Collections.emptyNavigableSet()
                : Arrays.stream(metadataOverride.split(Pattern.quote(File.pathSeparator)))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this(resolvedRoot,
                resolvedTarget,
                resolvedArtifacts,
                resolvedMetadata,
                resolvedConfiguration,
                resolvedBoms,
                resolvedProfiles,
                resolvedCache,
                new HashDigestFunction(System.getProperty("jenesis.project.digest", "SHA-256")),
                resolvedLayout,
                true,
                Boolean.getBoolean("jenesis.project.sources"),
                Boolean.getBoolean("jenesis.project.documentation"),
                Pinning.fromProperty(),
                System.getProperty("jenesis.project.version"),
                Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(BUILD))),
                new InferredMultiProjectAssembler(),
                BuildExecutor.Configuration::new,
                Map.of(),
                Map.of());
    }

    private static void locations(String text,
                                  Path root,
                                  SequencedSet<Path> defaults,
                                  Set<String> visited,
                                  SequencedSet<Path> target) {
        for (String entry : text.split(Pattern.quote(File.pathSeparator))) {
            String candidate = entry.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith("@")) {
                String name = candidate.substring(1);
                if (name.isEmpty()) {
                    target.addAll(defaults);
                } else {
                    String value = System.getProperty(name, System.getenv(name));
                    if (value == null) {
                        throw new IllegalStateException("Unresolved location reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular location reference: @" + name);
                    }
                    locations(value, root, defaults, visited, target);
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
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project configuration(Path... configuration) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                new LinkedHashSet<>(List.of(configuration)),
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project boms(Path... boms) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                new LinkedHashSet<>(List.of(boms)),
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project profiles(Path... profiles) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                new LinkedHashSet<>(List.of(profiles)),
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project target(Path target) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project artifacts(Path artifacts) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project cache(BuildExecutorCache cache) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project hashFunction(HashDigestFunction hashFunction) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project layout(Layout layout) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project tests(boolean tests) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project sources(boolean sources) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project documentation(boolean documentation) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project pinning(Pinning pinning) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project metadata(Path... metadata) {
        return new Project(root,
                target,
                artifacts,
                new LinkedHashSet<>(List.of(metadata)),
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project version(String version) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project defaultTarget(String... defaultTarget) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(defaultTarget))),
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project assembler(MultiProjectAssembler<? super ProjectModuleDescriptor> assembler) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project repositories(Map<String, Repository> repositories) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project resolvers(Map<String, Resolver> resolvers) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
    }

    public Project configurator(Supplier<BuildExecutor.Configuration> configurator) {
        return new Project(root,
                target,
                artifacts,
                metadata,
                configuration,
                boms,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers);
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
        new ProjectWatch(absoluteRoot, excluded, 200L).watch(() -> {
            try {
                build(selectors);
            } catch (Exception e) {
                System.out.println("Build failed: " + e);
            }
        });
    }

    public static void loadJenesisProperties(Path path) throws IOException {
        Path base = path.resolve("jenesis.properties");
        SequencedProperties project = Files.isRegularFile(base) ? SequencedProperties.ofFiles(base) : null;
        if (project != null) {
            requireApplicable(base, project, false);
        }
        String location = System.getProperty("jenesis.project.global");
        if (location == null && project != null) {
            location = project.getProperty("jenesis.project.global");
        }
        if (location == null) {
            location = System.getProperty("user.home");
        }
        SequencedProperties user = null;
        Path home = null;
        if (!location.isEmpty()) {
            home = Path.of(location).resolve(".jenesis");
            Path file = home.resolve("jenesis.properties");
            user = Files.isRegularFile(file) ? SequencedProperties.ofFiles(file) : null;
            if (user != null) {
                requireApplicable(file, user, true);
            }
        }
        Set<Path> loaded = new LinkedHashSet<>();
        Deque<Path> pending = new ArrayDeque<>();
        addProfiles(pending, path, System.getProperty("jenesis.project.properties"));
        if (project != null) {
            addProfiles(pending, path, project.getProperty("jenesis.project.properties"));
        }
        loadProfiles(loaded, pending, path);
        if (user != null) {
            addProfiles(pending, home, user.getProperty("jenesis.project.properties"));
            loadProfiles(loaded, pending, home);
        }
        if (project != null) {
            apply(project);
        }
        if (user != null) {
            apply(user);
        }
    }

    private static void loadProfiles(Set<Path> loaded, Deque<Path> pending, Path base) throws IOException {
        while (!pending.isEmpty()) {
            Path file = pending.removeFirst().normalize();
            if (!loaded.add(file) || !Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            requireApplicable(file, properties, true);
            addProfiles(pending, base, properties.getProperty("jenesis.project.properties"));
            apply(properties);
        }
    }

    private static void requireApplicable(Path file, SequencedProperties properties, boolean located) {
        if (properties.getProperty("jenesis.project.root") != null) {
            throw new IllegalStateException("jenesis.project.root cannot be set in " + file
                    + ": the project root locates this file, so it is resolved before the file is read"
                    + " (pass -Djenesis.project.root on the command line instead)");
        }
        if (located && properties.getProperty("jenesis.project.global") != null) {
            throw new IllegalStateException("jenesis.project.global cannot be set in " + file
                    + ": the user-global location is resolved from the command line or the project's"
                    + " jenesis.properties before this file is read");
        }
    }

    private static void apply(SequencedProperties properties) {
        for (String name : properties.stringPropertyNames()) {
            System.getProperties().putIfAbsent(name, properties.getProperty(name));
        }
    }

    private static void addProfiles(Deque<Path> pending, Path base, String list) {
        if (list == null) {
            return;
        }
        for (String name : list.split(",")) {
            String trimmed = name.trim();
            if (trimmed.endsWith(".properties")) {
                trimmed = trimmed.substring(0, trimmed.length() - ".properties".length());
            }
            if (!trimmed.isEmpty()) {
                pending.add(base.resolve("jenesis-" + trimmed + ".properties"));
            }
        }
    }

    private static void printConfiguration() {
        String catalogue = """
                project.root|.|Folder the build reads the project from
                project.target|target|Folder the build writes its outputs to
                project.artifacts||Folder resolved dependencies are cached in
                project.layout|auto|auto|maven|modular|modular_to_maven; auto reads the project
                project.sources|false|Assemble a sources jar for every module
                project.documentation|false|Assemble a javadoc jar for every module
                project.version||Version stamped onto every produced artifact
                project.digest|SHA-256|Algorithm for pin and dependency checksums
                project.metadata||Path-separated extra metadata files
                project.configuration|build.jenesis|Path-separated folders searched for tool configuration files; @ splices the default
                project.boms||Path-separated locations of local pin-<name>.properties; default: the configuration folders
                project.properties||Comma-separated profiles layered over jenesis.properties
                project.global||Location of a global properties file read before the project's own
                project.watch|false|Rebuild the selected target whenever a source file changes
                project.cache||Project-local disk cache, layered in front of a remote; empty means .jenesis/cache
                project.docker|false|Run the whole build inside a container
                project.docker.image||Image for that container
                project.docker.mount||Extra read-only container mounts, host[:container],...
                project.docker.mountWritable||Extra writable container mounts
                project.docker.env||Host environment variables to forward, name[=value],...
                make.compile|true|Compile the build sources once and run from those classes
                make.classes||Where those classes land, relative to the root; default: beside the sources
                make.daemon|false|Hand the build to a reused JVM; --stop as the only selector shuts it down
                daemon.idle|10800|Seconds an idle daemon waits before exiting
                daemon.options|-Xmx2g|JVM options for the daemon process itself, whitespace separated
                executor.concurrency|0|Run at most this many build steps at once; 0 is unbounded
                executor.timeout|PT0S|ISO-8601 timeout per step; PT0S is no timeout
                executor.digest|MD5|Algorithm behind the content and step hashes that drive the cache
                executor.rebuild|false|Wipe target/ before building; prefer letting the cache decide
                executor.aggregate|false|Collect independent step failures into one report
                process.concurrency|0|Run at most this many JDK tool runs at once; 0 is unbounded
                process.factory|tool|tool|fork; fork runs a JDK tool in a process of its own
                print.progress|true|The build progress lines
                print.process|false|Stream each external tool's command line and output as it runs
                print.<command>||The same for one tool only, as print.javac or print.tests
                print.command|false|Each external tool command line, without its output
                print.checksum|false|Each step's input and output checksums
                print.fetch|false|Each artifact downloaded from a repository
                print.cache|false|Each step served from or written to the build cache
                print.docker|true|The image notice when a build or run is containerized
                print.jreleaser|true|The JReleaser command line when a release runs
                dependency.pin||strict|versions|ignore; unset keeps existing pins and tolerates missing ones
                resolver.maven|maven|maven|closest|latest|release: which version a Maven coordinate resolves to
                resolver.module|first|first|ignore|fail: what to do with the versions a module-info records
                pin.checksum|true|Record content checksums in the pins that the pin selector writes
                pin.bom|keep|keep|flatten: whether pinning keeps BOM references or resolves them away
                platform.<token>||true adds a platform token and false removes one, selecting guarded pins
                repository.insecure|false|Allow plaintext http:// repository fetches
                repository.retries|2|Retries after a failed fetch; 0 disables
                repository.backoff|125|Initial retry backoff in milliseconds, doubling per attempt
                repository.connect.timeout|10000|Connect timeout for a repository fetch, in milliseconds
                repository.read.timeout|30000|Read timeout for a repository fetch, in milliseconds
                maven.uri||Maven remotes, comma-separated and queried left to right (env MAVEN_REPOSITORY_URI)
                maven.local||Local Maven cache folder (env MAVEN_REPOSITORY_LOCAL)
                maven.token||Bearer token for the Maven remote (env MAVEN_REPOSITORY_TOKEN)
                module.uri||Jenesis module remotes, likewise (env JENESIS_REPOSITORY_URI)
                module.local||Local module cache folder (env JENESIS_REPOSITORY_LOCAL)
                module.token||Bearer token for the module remote (env JENESIS_REPOSITORY_TOKEN)
                cache.uri||Build cache: a file:// folder, or an http(s):// cache server
                cache.project||Project name sent to a cache server (env JENESIS_CACHE_PROJECT)
                cache.key||Access key sent to a cache server (env JENESIS_CACHE_KEY)
                cache.connect|PT1S|Connect timeout for a cache server
                cache.read|PT10S|Read timeout for a cache server
                cache.insecure|false|Permit the cache key over plaintext http off loopback
                test.skip||Skip executing tests; presence alone switches it on
                test.engine||junit-platform|junit4|testng; unset detects it from the resolved dependencies
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
                format.java|true|The Java formatter a javaformat.properties selects
                format.ktlint|true|ktlint formatting
                format.scalafmt|true|scalafmt formatting
                format.rewrite|false|Let the formatters rewrite sources instead of verifying them
                generate.xjc|true|JAXB generation, activated by an xjc.properties
                generate.protoc|true|protoc generation, activated by a protoc.properties
                generate.avro|true|Avro generation, activated by an avro.properties
                generate.wsimport|true|JAX-WS generation, activated by a wsimport.properties
                generate.openapi|true|OpenAPI generation, activated by an openapi.properties
                observe.jacoco|true|JaCoCo coverage, activated by a jacoco.properties
                observe.native|true|native-image reachability agent, activated by a graal.properties
                mutate.pitest|true|PIT mutation testing, activated by a pitest.properties
                jreleaser.executable|jreleaser|The JReleaser executable a release runs
                jreleaser.command|full-release|The JReleaser command a release runs
                jreleaser.config||JReleaser configuration file
                jreleaser.dryRun|true|Run JReleaser without actually publishing
                """;
        for (String line : catalogue.lines().toList()) {
            String[] entry = line.split("\\|", 3);
            String name = "jenesis." + entry[0];
            String value = System.getProperty(name);
            String assignment = name + "=" + (value == null ? entry[1] : value);
            String state = value != null ? "[set]" : entry[1].isEmpty() ? "[unset]" : "[default]";
            String prefix = assignment + " ".repeat(Math.max(1, 46 - assignment.length())) + state;
            System.out.println(prefix + " ".repeat(Math.max(1, 56 - prefix.length())) + entry[2]);
        }
    }

    SequencedMap<String, Path> doMain(String... selectors) throws IOException, InterruptedException {
        if (selectors.length == 1 && selectors[0].equals(CONFIGURATION)) {
            printConfiguration();
            return Collections.emptyNavigableMap();
        }
        if (selectors.length == 1 && selectors[0].equals(PROPERTIES)) {
            SortedMap<String, String> properties = new TreeMap<>();
            for (String name : System.getProperties().stringPropertyNames()) {
                if (name.startsWith("jenesis.")) {
                    properties.put(name, System.getProperty(name));
                }
            }
            properties.forEach((name, value) -> System.out.println(name + "=" + value));
            return Collections.emptyNavigableMap();
        }
        if (Boolean.getBoolean("jenesis.project.watch")) {
            watch(selectors);
            return Collections.emptyNavigableMap();
        }
        if (Boolean.getBoolean("jenesis.project.docker")) {
            SortedMap<String, String> properties = new TreeMap<>();
            for (String name : System.getProperties().stringPropertyNames()) {
                if (name.startsWith("jenesis.") && !name.startsWith("jenesis.project.docker")) {
                    properties.put(name, System.getProperty(name));
                }
            }
            String image = System.getProperty("jenesis.project.docker.image");
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
            String cacheOverride = System.getProperty("jenesis.project.cache");
            if (cacheOverride != null && !cacheOverride.contains("://")) {
                Path cache = root.resolve(cacheOverride.isEmpty()
                        ? Path.of(".jenesis", "cache")
                        : Path.of(cacheOverride)).normalize();
                if (!cache.startsWith(root)) {
                    docker = docker.mount(Files.createDirectories(cache), cache.toString(), false);
                }
            }
            String cacheUri = System.getProperty("jenesis.cache.uri");
            if (cacheUri != null && cacheUri.startsWith("file:")) {
                Path cache = Path.of(URI.create(cacheUri)).toAbsolutePath().normalize();
                if (!cache.startsWith(root)) {
                    docker = docker.mount(Files.createDirectories(cache), cache.toString(), false);
                }
            }
            docker = docker.mounts(System.getProperty("jenesis.project.docker.mount"), root, true);
            docker = docker.mounts(System.getProperty("jenesis.project.docker.mountWritable"), root, false);
            docker = docker.envs(System.getProperty("jenesis.project.docker.env"));
            String mavenRepositoryUri = System.getProperty("jenesis.maven.uri", System.getenv("MAVEN_REPOSITORY_URI"));
            if (mavenRepositoryUri != null) {
                docker = docker.env("MAVEN_REPOSITORY_URI", mavenRepositoryUri);
            }
            String jenesisRepositoryUri = System.getProperty("jenesis.module.uri", System.getenv("JENESIS_REPOSITORY_URI"));
            if (jenesisRepositoryUri != null) {
                docker = docker.env("JENESIS_REPOSITORY_URI", jenesisRepositoryUri);
            }
            String mavenRepositoryLocal = System.getProperty("jenesis.maven.local", System.getenv("MAVEN_REPOSITORY_LOCAL"));
            Path mavenLocal = (mavenRepositoryLocal == null
                    ? Path.of(System.getProperty("user.home"), ".m2", "repository")
                    : Path.of(mavenRepositoryLocal)).toAbsolutePath().normalize();
            if (Files.isDirectory(mavenLocal)) {
                docker = docker.mount(mavenLocal, mavenLocal.toString(), true);
                docker = docker.env("MAVEN_REPOSITORY_LOCAL", mavenLocal.toString());
            }
            String jenesisRepositoryLocal = System.getProperty("jenesis.module.local", System.getenv("JENESIS_REPOSITORY_LOCAL"));
            Path jenesisLocal = (jenesisRepositoryLocal == null
                    ? Path.of(System.getProperty("user.home"), ".jenesis")
                    : Path.of(jenesisRepositoryLocal)).toAbsolutePath().normalize();
            if (Files.isDirectory(jenesisLocal)) {
                docker = docker.mount(jenesisLocal, jenesisLocal.toString(), true);
                docker = docker.env("JENESIS_REPOSITORY_LOCAL", jenesisLocal.toString());
            }
            if (Boolean.parseBoolean(System.getProperty("jenesis.print.docker", "true"))) {
                System.out.println("Launching build within Docker image: " + docker.image());
            }
            int code = docker.execute("build/jenesis/Project.java", properties, selectors);
            if (code != 0) {
                System.exit(code);
            }
            return Collections.emptyNavigableMap();
        }
        return this.build(selectors);
    }

    public static SequencedMap<String, Path> perform(String... selectors) {
        try {
            loadJenesisProperties(Path.of(System.getProperty("jenesis.project.root", ".")));
            return new Project().doMain(selectors);
        } catch (Throwable t) {
            report(t);
            return null;
        }
    }

    public static int run(String mainClass, String... selectors) {
        if (mainClass.equals(Project.class.getName())) {
            return perform(selectors) == null ? 1 : 0;
        }
        try {
            loadJenesisProperties(Path.of(System.getProperty("jenesis.project.root", ".")));
            Class.forName(mainClass, true, Project.class.getClassLoader())
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) selectors);
            return 0;
        } catch (Throwable t) {
            report(t);
            return 1;
        }
    }

    private static void report(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        t.printStackTrace();
        System.err.println();
        System.err.println("The build failed with the error above. If you meant to look up how to"
                + " invoke Jenesis, pass `help` as the only argument on the command line, or `skill`"
                + " for an agent-oriented briefing.");
    }
}
