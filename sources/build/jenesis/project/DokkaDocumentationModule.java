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
    private static final String REQUIRED = "required",
            ARTIFACTS = "artifacts",
            DEPENDENCIES = "dependencies",
            FILES = "dokka/",
            EXTERNAL_LINK = "external-link.",
            PACKAGE_LIST = ".package-list";
    private static final String MAVEN_GROUP = "org.jetbrains.dokka", JAVADOC_PLUGIN = "javadoc-plugin";
    private static final List<String> CLI_ARTIFACTS = List.of(
            "dokka-cli", "dokka-base", "analysis-kotlin-symbols");
    private static final Set<String> KEYS = Set.of("module-name",
            "includes",
            "format",
            "documented-visibilities",
            "suppress-obvious-functions",
            "suppress-inherited-members",
            "skip-empty-packages",
            "skip-deprecated",
            "report-undocumented",
            "suppressed-packages",
            "fail-on-warning",
            "jdk-version",
            "source-link",
            "source-link-suffix",
            "custom-assets",
            "custom-styles",
            "footer-message");
    private static final Set<String> VISIBILITIES = Set.of("public", "protected", "internal", "private", "package");
    private static final Set<String> FORMATS = Set.of("html", "javadoc");

    private final Map<String, Resolver> resolvers;
    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final String within;
    private final Function<List<String>, ? extends ProcessHandler> factory;
    private final ProcessBuildStep.Terms terms;
    private final SequencedProperties config;
    private final Path location;

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
             new SequencedProperties(),
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
                new SequencedProperties(),
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
                                     SequencedProperties config,
                                     Path location) {
        this.resolvers = resolvers;
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.within = within;
        this.factory = factory;
        this.terms = terms;
        this.config = config;
        this.location = location;
    }

    public DokkaDocumentationModule factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    public DokkaDocumentationModule pinning(Pinning pinning) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    public DokkaDocumentationModule tool(String tool) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    public DokkaDocumentationModule group(String group) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    public DokkaDocumentationModule within(String within) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    public DokkaDocumentationModule printing(BiConsumer<Boolean, String> printing) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms.printing(printing), config, location);
    }

    public DokkaDocumentationModule config(SequencedProperties config) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    public DokkaDocumentationModule location(Path location) {
        return new DokkaDocumentationModule(resolvers, dependencies, pinning, tool, group, within, factory, terms, config, location);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        for (String key : config.stringPropertyNames()) {
            String link = key.startsWith(EXTERNAL_LINK) ? key.substring(EXTERNAL_LINK.length()) : null;
            if (link != null && link.endsWith(PACKAGE_LIST)) {
                link = link.substring(0, link.length() - PACKAGE_LIST.length());
                if (config.value(EXTERNAL_LINK + link) == null) {
                    throw new IllegalArgumentException("The dokka property " + key + " names the package-list of no"
                            + " link - declare " + EXTERNAL_LINK + link + "=<url> beside it");
                }
            }
            if (!KEYS.contains(key) && (link == null || link.isEmpty() || link.contains("."))) {
                throw new IllegalArgumentException("Unknown dokka property: " + key + " (expected one of "
                        + new TreeSet<>(KEYS) + ", " + EXTERNAL_LINK + "<name> or " + EXTERNAL_LINK + "<name>"
                        + PACKAGE_LIST + ")");
            }
        }
        String format = config.value("format", "html");
        if (!FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unknown dokka format: " + format + " (expected one of "
                    + new TreeSet<>(FORMATS) + ")");
        }
        List<String> visibilities = new ArrayList<>();
        for (String visibility : Objects.requireNonNullElse(config.entries("documented-visibilities"), List.<String>of())) {
            if (!VISIBILITIES.contains(visibility.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Unknown dokka visibility in documented-visibilities: " + visibility
                        + " (expected one of " + new TreeSet<>(VISIBILITIES) + ")");
            }
            visibilities.add(visibility.toUpperCase(Locale.ROOT));
        }
        String jdk = config.value("jdk-version");
        if (jdk != null && !jdk.matches("[1-9][0-9]{0,3}")) {
            throw new IllegalArgumentException("The dokka property jdk-version is " + jdk + ", but names the feature"
                    + " release of the JDK whose documentation Dokka links to, such as 17");
        }
        String sourceLink = config.value("source-link");
        if (sourceLink == null && config.value("source-link-suffix") != null) {
            throw new IllegalArgumentException("The dokka property source-link-suffix names the line suffix of a"
                    + " source link, but source-link names none - declare source-link=<url of the source folder>");
        }
        SequencedMap<String, String> externalLinks = new LinkedHashMap<>();
        for (String key : new TreeSet<>(config.stringPropertyNames())) {
            if (key.startsWith(EXTERNAL_LINK) && !key.endsWith(PACKAGE_LIST)) {
                String url = config.value(key);
                if (url == null) {
                    throw new IllegalArgumentException("The dokka property " + key + " names no URL - name the root"
                            + " of the documentation to link to");
                }
                externalLinks.put(url, config.value(key + PACKAGE_LIST,
                        (url.endsWith("/") ? url : url + "/") + "package-list"));
            }
        }
        SequencedMap<String, Path> bound = new LinkedHashMap<>();
        SequencedMap<String, List<String>> files = new LinkedHashMap<>();
        for (String key : List.of("includes", "custom-assets", "custom-styles")) {
            List<String> targets = new ArrayList<>();
            for (String value : Objects.requireNonNullElse(config.entries(key), List.<String>of())) {
                Path source = location == null ? Path.of(value) : location.resolve(value);
                if (!Files.isRegularFile(source)) {
                    throw new IllegalArgumentException("The dokka property " + key + " names " + value + ", but "
                            + source.toAbsolutePath().normalize() + " is no file - name a file relative to the"
                            + " module's folder" + (location == null ? "" : " " + location));
                }
                String target = FILES + key + "/" + targets.size() + "/" + source.getFileName();
                bound.put(target, source);
                targets.add(target);
            }
            files.put(key, targets);
        }
        Options options = new Options(config.value("module-name", "documentation"),
                format,
                files.get("includes"),
                visibilities,
                config.flag("suppress-obvious-functions", true),
                config.flag("suppress-inherited-members", false),
                config.flag("skip-empty-packages", true),
                config.flag("skip-deprecated", false),
                config.flag("report-undocumented", false),
                Objects.requireNonNullElse(config.entries("suppressed-packages"), List.of()),
                config.flag("fail-on-warning", false),
                jdk == null ? null : Integer.valueOf(jdk),
                sourceLink,
                config.value("source-link-suffix", "#L"),
                externalLinks,
                files.get("custom-assets"),
                files.get("custom-styles"),
                config.value("footer-message"));
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
                           String format,
                           List<String> includes,
                           List<String> documentedVisibilities,
                           boolean suppressObviousFunctions,
                           boolean suppressInheritedMembers,
                           boolean skipEmptyPackages,
                           boolean skipDeprecated,
                           boolean reportUndocumented,
                           List<String> suppressedPackages,
                           boolean failOnWarning,
                           Integer jdkVersion,
                           String sourceLink,
                           String sourceLinkSuffix,
                           SequencedMap<String, String> externalLinks,
                           List<String> customAssets,
                           List<String> customStyles,
                           String footerMessage) implements Serializable {
    }

    private record Requires(boolean maven, String tool, String format) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            if (maven) {
                for (String artifact : format.equals("javadoc")
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
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return Javac.hasRelevantChange(arguments,
                    Set.of(".kt"),
                    Set.of("dokka.properties"),
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
                            + ", which dokka.properties names");
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
            if (!options.documentedVisibilities().isEmpty()) {
                sourceSet.append(",\n    \"documentedVisibilities\": ").append(strings(options.documentedVisibilities()));
            }
            if (options.jdkVersion() != null) {
                sourceSet.append(",\n    \"jdkVersion\": ").append(options.jdkVersion());
            }
            if (options.sourceLink() != null) {
                sourceSet.append(",\n    \"sourceLinks\": [")
                        .append(sources.stream()
                                .map(root -> "{\"localDirectory\": \"" + Json.escaped(Path.of(root).toAbsolutePath().toString())
                                        + "\", \"remoteUrl\": \"" + Json.escaped(options.sourceLink())
                                        + "\", \"remoteLineSuffix\": \"" + Json.escaped(options.sourceLinkSuffix())
                                        + "\"}")
                                .collect(Collectors.joining(", ")))
                        .append(']');
            }
            if (!options.externalLinks().isEmpty()) {
                sourceSet.append(",\n    \"externalDocumentationLinks\": [")
                        .append(options.externalLinks().entrySet().stream()
                                .map(link -> "{\"url\": \"" + Json.escaped(link.getKey())
                                        + "\", \"packageListUrl\": \"" + Json.escaped(link.getValue()) + "\"}")
                                .collect(Collectors.joining(", ")))
                        .append(']');
            }
            if (!options.suppressedPackages().isEmpty()) {
                sourceSet.append(",\n    \"perPackageOptions\": [")
                        .append(options.suppressedPackages().stream()
                                .map(pattern -> "{\"matchingRegex\": \"" + Json.escaped(pattern)
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
}
