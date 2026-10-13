package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Json;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Javac;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class DokkaDocumentationModule implements BuildExecutorModule {

    public static final String DOCUMENTED = "documented";
    private static final String REQUIRED = "required", ARTIFACTS = "artifacts", DEPENDENCIES = "dependencies", FILES = "dokka/";
    private static final String MAVEN_GROUP = "org.jetbrains.dokka", JAVADOC_PLUGIN = "javadoc-plugin";
    private static final List<String> CLI_ARTIFACTS = List.of("dokka-cli", "dokka-base", "analysis-kotlin-symbols");

    private final Map<String, Resolver> resolvers;
    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final String within;
    private final Function<List<String>, ? extends ProcessHandler> factory;
    private final ProcessBuildStep.Terms terms;
    private final String moduleName;
    private final Format format;
    private final List<Path> includes;
    private final Set<Visibility> documentedVisibilities;
    private final boolean suppressObviousFunctions;
    private final boolean suppressInheritedMembers;
    private final boolean skipEmptyPackages;
    private final boolean skipDeprecated;
    private final boolean reportUndocumented;
    private final List<Pattern> suppressedPackages;
    private final boolean failOnWarning;
    private final Integer jdkVersion;
    private final URI sourceLink;
    private final String sourceLinkSuffix;
    private final SequencedMap<URI, URI> externalLinks;
    private final List<Path> customAssets;
    private final List<Path> customStyles;
    private final String footerMessage;

    public DokkaDocumentationModule(Map<String, Repository> repositories,
                                    Map<String, Resolver> resolvers) {
        this(resolvers,
             new Dependencies(repositories, resolvers),
             null,
             "dokka",
             "main",
             null,
             null,
             new ProcessBuildStep.Terms(),
             "documentation",
             Format.HTML,
             List.of(),
             Collections.emptySet(),
             true,
             false,
             true,
             false,
             false,
             List.of(),
             false,
             null,
             null,
             "#L",
             Collections.emptyNavigableMap(),
             List.of(),
             List.of(),
             null);
    }

    public static DokkaDocumentationModule ofEnvironment(Environment environment,
                                                         Map<String, Repository> repositories,
                                                         Map<String, Resolver> resolvers) {
        return new DokkaDocumentationModule(resolvers,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "dokka",
                "main",
                null,
                null,
                ProcessBuildStep.Terms.ofEnvironment(environment, "dokka"),
                "documentation",
                Format.HTML,
                List.of(),
                Collections.emptySet(),
                true,
                false,
                true,
                false,
                false,
                List.of(),
                false,
                null,
                null,
                "#L",
                Collections.emptyNavigableMap(),
                List.of(),
                List.of(),
                null);
    }

    private DokkaDocumentationModule(Map<String, Resolver> resolvers,
                                     Dependencies dependencies,
                                     Pinning pinning,
                                     String tool,
                                     String group,
                                     String within,
                                     Function<List<String>, ? extends ProcessHandler> factory,
                                     ProcessBuildStep.Terms terms,
                                     String moduleName,
                                     Format format,
                                     List<Path> includes,
                                     Set<Visibility> documentedVisibilities,
                                     boolean suppressObviousFunctions,
                                     boolean suppressInheritedMembers,
                                     boolean skipEmptyPackages,
                                     boolean skipDeprecated,
                                     boolean reportUndocumented,
                                     List<Pattern> suppressedPackages,
                                     boolean failOnWarning,
                                     Integer jdkVersion,
                                     URI sourceLink,
                                     String sourceLinkSuffix,
                                     SequencedMap<URI, URI> externalLinks,
                                     List<Path> customAssets,
                                     List<Path> customStyles,
                                     String footerMessage) {
        this.resolvers = resolvers;
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.within = within;
        this.factory = factory;
        this.terms = terms;
        this.moduleName = moduleName;
        this.format = format;
        this.includes = includes;
        this.documentedVisibilities = documentedVisibilities;
        this.suppressObviousFunctions = suppressObviousFunctions;
        this.suppressInheritedMembers = suppressInheritedMembers;
        this.skipEmptyPackages = skipEmptyPackages;
        this.skipDeprecated = skipDeprecated;
        this.reportUndocumented = reportUndocumented;
        this.suppressedPackages = suppressedPackages;
        this.failOnWarning = failOnWarning;
        this.jdkVersion = jdkVersion;
        this.sourceLink = sourceLink;
        this.sourceLinkSuffix = sourceLinkSuffix;
        this.externalLinks = externalLinks;
        this.customAssets = customAssets;
        this.customStyles = customStyles;
        this.footerMessage = footerMessage;
    }

    public DokkaDocumentationModule factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule pinning(Pinning pinning) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule tool(String tool) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule group(String group) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule within(String within) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule printing(BiConsumer<Boolean, String> printing) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory,
                terms.printing(printing), moduleName, format, includes, documentedVisibilities,
                suppressObviousFunctions, suppressInheritedMembers, skipEmptyPackages, skipDeprecated,
                reportUndocumented, suppressedPackages, failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix,
                externalLinks, customAssets, customStyles, footerMessage);
    }

    public DokkaDocumentationModule moduleName(String moduleName) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule format(Format format) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule includes(List<Path> includes) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, List.copyOf(includes), documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule documentedVisibilities(Set<Visibility> documentedVisibilities) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, Set.copyOf(documentedVisibilities), suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule suppressObviousFunctions(boolean suppressObviousFunctions) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule suppressInheritedMembers(boolean suppressInheritedMembers) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule skipEmptyPackages(boolean skipEmptyPackages) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule skipDeprecated(boolean skipDeprecated) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule reportUndocumented(boolean reportUndocumented) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule suppressedPackages(List<Pattern> suppressedPackages) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented,
                List.copyOf(suppressedPackages), failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks,
                customAssets, customStyles, footerMessage);
    }

    public DokkaDocumentationModule failOnWarning(boolean failOnWarning) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule jdkVersion(Integer jdkVersion) {
        if (jdkVersion != null && jdkVersion < 1) {
            throw new IllegalArgumentException("The JDK version " + jdkVersion + " is no feature release of the JDK"
                    + " whose documentation Dokka links to, expected one such as 17");
        }
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule sourceLink(URI sourceLink) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule sourceLinkSuffix(String sourceLinkSuffix) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule externalLinks(SequencedMap<URI, URI> externalLinks) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix,
                Collections.unmodifiableSequencedMap(new LinkedHashMap<>(externalLinks)), customAssets, customStyles,
                footerMessage);
    }

    public DokkaDocumentationModule externalLink(URI url, URI packageList) {
        SequencedMap<URI, URI> added = new LinkedHashMap<>(externalLinks);
        added.put(url, packageList);
        return externalLinks(added);
    }

    public DokkaDocumentationModule externalLink(URI url) {
        String root = url.toString();
        return externalLink(url, URI.create((root.endsWith("/") ? root : root + "/") + "package-list"));
    }

    public DokkaDocumentationModule customAssets(List<Path> customAssets) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, List.copyOf(customAssets),
                customStyles, footerMessage);
    }

    public DokkaDocumentationModule customStyles(List<Path> customStyles) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets,
                List.copyOf(customStyles), footerMessage);
    }

    public DokkaDocumentationModule footerMessage(String footerMessage) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms,
                moduleName, format, includes, documentedVisibilities, suppressObviousFunctions,
                suppressInheritedMembers, skipEmptyPackages, skipDeprecated, reportUndocumented, suppressedPackages,
                failOnWarning, jdkVersion, sourceLink, sourceLinkSuffix, externalLinks, customAssets, customStyles,
                footerMessage);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        SequencedMap<String, Path> bound = new LinkedHashMap<>();
        SequencedMap<String, List<String>> files = new LinkedHashMap<>();
        for (Map.Entry<String, List<Path>> kind : List.of(Map.entry("includes", includes),
                Map.entry("assets", customAssets),
                Map.entry("styles", customStyles))) {
            List<String> targets = new ArrayList<>();
            for (Path source : kind.getValue()) {
                if (!Files.isRegularFile(source)) {
                    throw new IllegalArgumentException("Dokka is handed " + source.toAbsolutePath().normalize()
                            + " among its " + kind.getKey() + ", but it is no file - name an existing file");
                }
                String target = FILES + kind.getKey() + "/" + targets.size() + "/" + source.getFileName();
                bound.put(target, source);
                targets.add(target);
            }
            files.put(kind.getKey(), targets);
        }
        Options options = new Options(moduleName,
                files.get("includes"),
                Arrays.stream(Visibility.values()).filter(documentedVisibilities::contains).toList(),
                suppressObviousFunctions,
                suppressInheritedMembers,
                skipEmptyPackages,
                skipDeprecated,
                reportUndocumented,
                suppressedPackages,
                failOnWarning,
                jdkVersion,
                sourceLink,
                sourceLinkSuffix,
                new LinkedHashMap<>(externalLinks),
                files.get("assets"),
                files.get("styles"),
                footerMessage);
        SequencedSet<String> upstream = inherited.sequencedKeySet();
        buildExecutor.addStep(REQUIRED, new Requires(resolvers.containsKey("maven"), tool, format), upstream);
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(upstream);
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> documentInputs = new LinkedHashSet<>();
        documentInputs.add(DEPENDENCIES);
        for (Map.Entry<String, Path> file : bound.entrySet()) {
            String name = "file-" + (documentInputs.size() - 1);
            buildExecutor.addSource(name, new Bind(Map.of(Path.of(""), Path.of(file.getKey()))), file.getValue());
            documentInputs.add(name);
        }
        documentInputs.addAll(upstream);
        buildExecutor.addStep(DOCUMENTED,
                factory == null
                        ? new Document(terms, within, tool, group, options)
                        : new Document(terms, within, tool, group, options, factory),
                documentInputs);
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.equals(DOCUMENTED)) {
            return Optional.of(DOCUMENTED);
        }
        if (path.equals(DEPENDENCIES)) {
            return Optional.of(ARTIFACTS);
        }
        return Optional.empty();
    }

    private record Options(String moduleName,
                           List<String> includes,
                           List<Visibility> documentedVisibilities,
                           boolean suppressObviousFunctions,
                           boolean suppressInheritedMembers,
                           boolean skipEmptyPackages,
                           boolean skipDeprecated,
                           boolean reportUndocumented,
                           List<Pattern> suppressedPackages,
                           boolean failOnWarning,
                           Integer jdkVersion,
                           URI sourceLink,
                           String sourceLinkSuffix,
                           LinkedHashMap<URI, URI> externalLinks,
                           List<String> customAssets,
                           List<String> customStyles,
                           String footerMessage) implements Serializable {
    }

    private record Requires(boolean maven, String tool, Format format) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            if (maven) {
                for (String artifact : format == Format.JAVADOC
                        ? Stream.concat(CLI_ARTIFACTS.stream(), Stream.of(JAVADOC_PLUGIN)).toList()
                        : CLI_ARTIFACTS) {
                    requires.setProperty(tool + "/runtime/maven/" + MAVEN_GROUP + "/" + artifact + "/RELEASE", "");
                }
            }
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Document extends ProcessBuildStep {

        private final String within;
        private final String tool;
        private final String group;
        private final Options options;

        private Document(ProcessBuildStep.Terms terms,
                         String within,
                         String tool,
                         String group,
                         Options options) {
            this(terms, within, tool, group, options, ProcessHandler.OfProcess.ofJavaHome("bin/java"));
        }

        private Document(ProcessBuildStep.Terms terms,
                         String within,
                         String tool,
                         String group,
                         Options options,
                         Function<List<String>, ? extends ProcessHandler> factory) {
            super("dokka", factory, terms);
            this.within = within;
            this.tool = tool;
            this.group = group;
            this.options = options;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            return Javac.hasRelevantChange(arguments,
                    Set.of(".kt"),
                    Set.of("dokka"),
                    false)
                    || arguments.values().stream().anyMatch(argument -> argument.hasChanged(Path.of(FILES)));
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            Path documentation = context.next().resolve(Javadoc.JAVADOC);
            Path output = Files.createDirectories(within == null ? documentation : documentation.resolve(within));
            List<String> sources = new ArrayList<>(), jars = new ArrayList<>(), classpath = new ArrayList<>();
            SequencedMap<String, String> files = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path classes = argument.folder().resolve(BuildStep.CLASSES);
                if (Files.exists(classes)) {
                    classpath.add(classes.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    classpath.add(jar.toString());
                }
                for (String file : Stream.of(options.includes(), options.customAssets(), options.customStyles())
                        .flatMap(List::stream)
                        .toList()) {
                    Path bound = argument.folder().resolve(file);
                    if (Files.isRegularFile(bound)) {
                        files.putIfAbsent(file, bound.toString());
                    }
                }
                Path source = argument.folder().resolve(Bind.SOURCES);
                if (Files.exists(source)) {
                    boolean[] hasKotlin = new boolean[1];
                    Files.walkFileTree(source, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (file.getFileName().toString().endsWith(".kt")) {
                                hasKotlin[0] = true;
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    if (hasKotlin[0]) {
                        sources.add(source.toString());
                    }
                }
            }
            if (sources.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            String cli = null;
            List<String> plugins = new ArrayList<>();
            for (String jar : jars) {
                if (new File(jar).getName().contains("dokka-cli")) {
                    cli = jar;
                } else {
                    plugins.add(jar);
                }
            }
            if (cli == null || plugins.isEmpty()) {
                throw new IllegalStateException("No Dokka CLI and plugin jars were resolved in the " + tool + " group"
                        + " upstream of the Dokka step, which documents the Kotlin sources: resolve "
                        + MAVEN_GROUP + ":dokka-cli through a Maven repository");
            }
            for (String file : Stream.of(options.includes(), options.customAssets(), options.customStyles())
                    .flatMap(List::stream)
                    .toList()) {
                if (!files.containsKey(file)) {
                    throw new IllegalStateException("No input of the Dokka step binds " + file
                            + ", which it is configured to hand to Dokka");
                }
            }
            StringBuilder base = new StringBuilder();
            if (!options.customAssets().isEmpty()) {
                base.append("\"customAssets\": ")
                        .append(strings(options.customAssets().stream().map(files::get).toList()));
            }
            if (!options.customStyles().isEmpty()) {
                base.append(base.isEmpty() ? "" : ", ")
                        .append("\"customStyleSheets\": ")
                        .append(strings(options.customStyles().stream().map(files::get).toList()));
            }
            if (options.footerMessage() != null) {
                base.append(base.isEmpty() ? "" : ", ")
                        .append("\"footerMessage\": \"")
                        .append(Json.escaped(options.footerMessage()))
                        .append('"');
            }
            StringBuilder sourceSet = new StringBuilder()
                    .append("    \"displayName\": \"JVM\",\n")
                    .append("    \"sourceSetID\": {\"scopeId\": \"").append(Json.escaped(options.moduleName()))
                    .append("\", \"sourceSetName\": \"main\"},\n")
                    .append("    \"analysisPlatform\": \"jvm\",\n")
                    .append("    \"sourceRoots\": ").append(strings(sources)).append(",\n")
                    .append("    \"classpath\": ").append(strings(classpath)).append(",\n")
                    .append("    \"includes\": ")
                    .append(strings(options.includes().stream().map(files::get).toList())).append(",\n")
                    .append("    \"skipEmptyPackages\": ").append(options.skipEmptyPackages()).append(",\n")
                    .append("    \"skipDeprecated\": ").append(options.skipDeprecated()).append(",\n")
                    .append("    \"reportUndocumented\": ").append(options.reportUndocumented());
            List<String> versioned = sources.stream()
                    .map(root -> Path.of(root).resolve("META-INF").resolve("versions"))
                    .filter(Files::isDirectory)
                    .map(Path::toString)
                    .toList();
            if (!versioned.isEmpty()) {
                sourceSet.append(",\n    \"suppressedFiles\": ").append(strings(versioned));
            }
            if (!options.documentedVisibilities().isEmpty()) {
                sourceSet.append(",\n    \"documentedVisibilities\": ").append(strings(options.documentedVisibilities().stream().map(Visibility::name).toList()));
            }
            if (options.jdkVersion() != null) {
                sourceSet.append(",\n    \"jdkVersion\": ").append(options.jdkVersion());
            }
            if (options.sourceLink() != null) {
                sourceSet.append(",\n    \"sourceLinks\": [")
                        .append(sources.stream()
                                .map(root -> "{\"localDirectory\": \"" + Json.escaped(Path.of(root).toAbsolutePath().toString())
                                        + "\", \"remoteUrl\": \"" + Json.escaped(options.sourceLink().toString())
                                        + "\", \"remoteLineSuffix\": \"" + Json.escaped(options.sourceLinkSuffix())
                                        + "\"}")
                                .collect(Collectors.joining(", ")))
                        .append(']');
            }
            if (!options.externalLinks().isEmpty()) {
                sourceSet.append(",\n    \"externalDocumentationLinks\": [")
                        .append(options.externalLinks().entrySet().stream()
                                .map(link -> "{\"url\": \"" + Json.escaped(link.getKey().toString())
                                        + "\", \"packageListUrl\": \"" + Json.escaped(link.getValue().toString()) + "\"}")
                                .collect(Collectors.joining(", ")))
                        .append(']');
            }
            if (!options.suppressedPackages().isEmpty()) {
                sourceSet.append(",\n    \"perPackageOptions\": [")
                        .append(options.suppressedPackages().stream()
                                .map(pattern -> "{\"matchingRegex\": \"" + Json.escaped(pattern.pattern())
                                        + "\", \"suppress\": true, \"includeNonPublic\": false,"
                                        + " \"skipDeprecated\": false, \"documentedVisibilities\": []}")
                                .collect(Collectors.joining(", ")))
                        .append(']');
            }
            StringBuilder json = new StringBuilder("{\n")
                    .append("  \"moduleName\": \"").append(Json.escaped(options.moduleName())).append("\",\n")
                    .append("  \"outputDir\": \"").append(Json.escaped(output.toString())).append("\",\n")
                    .append("  \"failOnWarning\": ").append(options.failOnWarning()).append(",\n")
                    .append("  \"suppressObviousFunctions\": ").append(options.suppressObviousFunctions()).append(",\n")
                    .append("  \"suppressInheritedMembers\": ").append(options.suppressInheritedMembers()).append(",\n")
                    .append("  \"pluginsClasspath\": ").append(strings(plugins)).append(",\n");
            if (!base.isEmpty()) {
                json.append("  \"pluginsConfiguration\": [{\"fqPluginName\": \"org.jetbrains.dokka.base.DokkaBase\",")
                        .append(" \"serializationFormat\": \"JSON\", \"values\": \"")
                        .append(Json.escaped("{" + base + "}"))
                        .append("\"}],\n");
            }
            json.append("  \"sourceSets\": [{\n").append(sourceSet).append("\n  }]\n}\n");
            Path configuration = context.supplement().resolve("dokka.json");
            Files.writeString(configuration, json);
            return CompletableFuture.completedStage(List.of("-jar", cli, configuration.toString()));
        }

        private static String strings(List<String> values) {
            return values.stream()
                    .map(value -> "\"" + Json.escaped(value) + "\"")
                    .collect(Collectors.joining(", ", "[", "]"));
        }
    }

    public enum Format {

        HTML, JAVADOC
    }

    public enum Visibility {

        PUBLIC, PROTECTED, INTERNAL, PRIVATE, PACKAGE
    }
}
