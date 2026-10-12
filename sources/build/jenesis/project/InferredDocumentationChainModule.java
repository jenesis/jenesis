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
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;

public class InferredDocumentationChainModule implements BuildExecutorModule {

    public static final String JAVADOC = "javadoc", GROOVYDOC = "groovydoc", SCALADOC = "scaladoc", DOKKA = "dokka";
    public static final String DOCUMENT = "document", AGGREGATE = "aggregate";
    private static final String SCAN = "scan", SCAN_FILE = "scan.properties";
    private static final String MODULE_NAME = "module-name",
            INCLUDES = "includes",
            FORMAT = "format",
            DOCUMENTED_VISIBILITIES = "documented-visibilities",
            SUPPRESS_OBVIOUS_FUNCTIONS = "suppress-obvious-functions",
            SUPPRESS_INHERITED_MEMBERS = "suppress-inherited-members",
            SKIP_EMPTY_PACKAGES = "skip-empty-packages",
            SKIP_DEPRECATED = "skip-deprecated",
            REPORT_UNDOCUMENTED = "report-undocumented",
            SUPPRESSED_PACKAGES = "suppressed-packages",
            FAIL_ON_WARNING = "fail-on-warning",
            JDK_VERSION = "jdk-version",
            SOURCE_LINK = "source-link",
            SOURCE_LINK_SUFFIX = "source-link-suffix",
            CUSTOM_ASSETS = "custom-assets",
            CUSTOM_STYLES = "custom-styles",
            FOOTER_MESSAGE = "footer-message",
            EXTERNAL_LINK = "external-link.",
            PACKAGE_LIST = ".package-list";
    private static final Set<String> DOKKA_KEYS = Set.of(MODULE_NAME,
            INCLUDES,
            FORMAT,
            DOCUMENTED_VISIBILITIES,
            SUPPRESS_OBVIOUS_FUNCTIONS,
            SUPPRESS_INHERITED_MEMBERS,
            SKIP_EMPTY_PACKAGES,
            SKIP_DEPRECATED,
            REPORT_UNDOCUMENTED,
            SUPPRESSED_PACKAGES,
            FAIL_ON_WARNING,
            JDK_VERSION,
            SOURCE_LINK,
            SOURCE_LINK_SUFFIX,
            CUSTOM_ASSETS,
            CUSTOM_STYLES,
            FOOTER_MESSAGE);

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final SequencedSet<Path> configuration;
    private final Path location;
    private final Javadoc javadocStep;
    private final DokkaDocumentationModule dokkaModule;
    private final ScalaDocumentationModule scaladocModule;
    private final GroovyDocumentationModule groovydocModule;
    private final Function<Javadoc, BuildStep> javadoc;
    private final Function<DokkaDocumentationModule, BuildExecutorModule> dokka;
    private final Function<ScalaDocumentationModule, BuildExecutorModule> scaladoc;
    private final Function<GroovyDocumentationModule, BuildExecutorModule> groovydoc;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredDocumentationChainModule(Map<String, Repository> repositories,
                                            Map<String, Resolver> resolvers) {
        this(repositories,
                resolvers,
                null,
                Collections.emptyNavigableSet(),
                null,
                new Javadoc(ProcessHandler.Factory.of()),
                new DokkaDocumentationModule(repositories, resolvers),
                new ScalaDocumentationModule(repositories, resolvers),
                new GroovyDocumentationModule(repositories, resolvers),
                step -> step,
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
    }

    public static InferredDocumentationChainModule ofEnvironment(Environment environment,
                                                                 Map<String, Repository> repositories,
                                                                 Map<String, Resolver> resolvers) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                null,
                Collections.emptyNavigableSet(),
                null,
                Javadoc.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment)),
                DokkaDocumentationModule.ofEnvironment(environment, repositories, resolvers),
                ScalaDocumentationModule.ofEnvironment(environment, repositories, resolvers),
                GroovyDocumentationModule.ofEnvironment(environment, repositories, resolvers),
                step -> step,
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
    }

    private InferredDocumentationChainModule(Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers,
                                             Pinning pinning,
                                             SequencedSet<Path> configuration,
                                             Path location,
                                             Javadoc javadocStep,
                                             DokkaDocumentationModule dokkaModule,
                                             ScalaDocumentationModule scaladocModule,
                                             GroovyDocumentationModule groovydocModule,
                                             Function<Javadoc, BuildStep> javadoc,
                                             Function<DokkaDocumentationModule, BuildExecutorModule> dokka,
                                             Function<ScalaDocumentationModule, BuildExecutorModule> scaladoc,
                                             Function<GroovyDocumentationModule, BuildExecutorModule> groovydoc,
                                             SequencedMap<String, BuildExecutorModule> custom) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.configuration = configuration;
        this.location = location;
        this.javadocStep = javadocStep;
        this.dokkaModule = dokkaModule;
        this.scaladocModule = scaladocModule;
        this.groovydocModule = groovydocModule;
        this.javadoc = javadoc;
        this.dokka = dokka;
        this.scaladoc = scaladoc;
        this.groovydoc = groovydoc;
        this.custom = custom;
    }

    public InferredDocumentationChainModule pinning(Pinning pinning) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule configuration(SequencedSet<Path> configuration) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule location(Path location) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule javadoc(Function<Javadoc, BuildStep> javadoc) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule dokka(Function<DokkaDocumentationModule, BuildExecutorModule> dokka) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule scaladoc(Function<ScalaDocumentationModule, BuildExecutorModule> scaladoc) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule groovydoc(Function<GroovyDocumentationModule, BuildExecutorModule> groovydoc) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                pinning,
                configuration,
                location,
                javadocStep,
                dokkaModule,
                scaladocModule,
                groovydocModule,
                javadoc,
                dokka,
                scaladoc,
                groovydoc,
                custom);
    }

    public InferredDocumentationChainModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredDocumentationChainModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path dokkaProperties = BuildStep.locate(configuration, "dokka.properties");
        buildExecutor.addStep(SCAN, new Scan(), inherited.sequencedKeySet());
        SequencedSet<String> documentInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
        documentInputs.add(SCAN);
        buildExecutor.addModule(DOCUMENT,
                new Document(repositories,
                        resolvers,
                        pinning,
                        javadocStep,
                        dokkaProperties == null ? dokkaModule : configured(dokkaModule, dokkaProperties, location),
                        scaladocModule,
                        groovydocModule,
                        javadoc,
                        dokka,
                        scaladoc,
                        groovydoc),
                documentInputs);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(SCAN) ? Optional.empty() : Optional.of(path);
    }

    private static DokkaDocumentationModule configured(DokkaDocumentationModule module, Path file, Path location)
            throws IOException {
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            String link = key.startsWith(EXTERNAL_LINK) ? key.substring(EXTERNAL_LINK.length()) : null;
            if (link != null && link.endsWith(PACKAGE_LIST)) {
                link = link.substring(0, link.length() - PACKAGE_LIST.length());
                if (properties.value(EXTERNAL_LINK + link) == null) {
                    throw new IllegalArgumentException("The dokka property " + key + " of " + file + " names the"
                            + " package-list of no link - declare " + EXTERNAL_LINK + link + "=<url> beside it");
                }
            }
            if (!DOKKA_KEYS.contains(key) && (link == null || link.isEmpty() || link.contains("."))) {
                throw new IllegalArgumentException("Unknown dokka property: " + key + " in " + file + " (expected one of "
                        + new TreeSet<>(DOKKA_KEYS) + ", " + EXTERNAL_LINK + "<name> or " + EXTERNAL_LINK + "<name>"
                        + PACKAGE_LIST + ")");
            }
        }
        String moduleName = properties.value(MODULE_NAME);
        if (moduleName != null) {
            module = module.moduleName(moduleName);
        }
        String format = properties.value(FORMAT);
        if (format != null) {
            module = module.format(Arrays.stream(DokkaDocumentationModule.Format.values())
                    .filter(candidate -> candidate.name().toLowerCase(Locale.ROOT).equals(format))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown dokka format: " + format + " in " + file
                            + " (expected one of " + Arrays.stream(DokkaDocumentationModule.Format.values())
                                    .map(candidate -> candidate.name().toLowerCase(Locale.ROOT))
                                    .toList() + ")")));
        }
        List<String> visibilities = properties.entries(DOCUMENTED_VISIBILITIES);
        if (visibilities != null) {
            Set<DokkaDocumentationModule.Visibility> documented = new LinkedHashSet<>();
            for (String visibility : visibilities) {
                documented.add(Arrays.stream(DokkaDocumentationModule.Visibility.values())
                        .filter(candidate -> candidate.name().equalsIgnoreCase(visibility))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown dokka visibility in "
                                + DOCUMENTED_VISIBILITIES + " of " + file + ": " + visibility + " (expected one of "
                                + Arrays.stream(DokkaDocumentationModule.Visibility.values())
                                        .map(candidate -> candidate.name().toLowerCase(Locale.ROOT))
                                        .sorted()
                                        .toList() + ")")));
            }
            module = module.documentedVisibilities(documented);
        }
        Boolean suppressObviousFunctions = properties.flagOrNull(SUPPRESS_OBVIOUS_FUNCTIONS);
        if (suppressObviousFunctions != null) {
            module = module.suppressObviousFunctions(suppressObviousFunctions);
        }
        Boolean suppressInheritedMembers = properties.flagOrNull(SUPPRESS_INHERITED_MEMBERS);
        if (suppressInheritedMembers != null) {
            module = module.suppressInheritedMembers(suppressInheritedMembers);
        }
        Boolean skipEmptyPackages = properties.flagOrNull(SKIP_EMPTY_PACKAGES);
        if (skipEmptyPackages != null) {
            module = module.skipEmptyPackages(skipEmptyPackages);
        }
        Boolean skipDeprecated = properties.flagOrNull(SKIP_DEPRECATED);
        if (skipDeprecated != null) {
            module = module.skipDeprecated(skipDeprecated);
        }
        Boolean reportUndocumented = properties.flagOrNull(REPORT_UNDOCUMENTED);
        if (reportUndocumented != null) {
            module = module.reportUndocumented(reportUndocumented);
        }
        Boolean failOnWarning = properties.flagOrNull(FAIL_ON_WARNING);
        if (failOnWarning != null) {
            module = module.failOnWarning(failOnWarning);
        }
        List<String> suppressed = properties.entries(SUPPRESSED_PACKAGES);
        if (suppressed != null) {
            List<Pattern> patterns = new ArrayList<>();
            for (String pattern : suppressed) {
                try {
                    patterns.add(Pattern.compile(pattern));
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("The dokka property " + SUPPRESSED_PACKAGES + " of " + file
                            + " names " + pattern + ", which is no regular expression of package names: "
                            + e.getDescription());
                }
            }
            module = module.suppressedPackages(patterns);
        }
        String jdk = properties.value(JDK_VERSION);
        if (jdk != null) {
            if (!jdk.matches("[1-9][0-9]{0,3}")) {
                throw new IllegalArgumentException("The dokka property " + JDK_VERSION + " of " + file + " is " + jdk
                        + ", but names the feature release of the JDK whose documentation Dokka links to, such as 17");
            }
            module = module.jdkVersion(Integer.valueOf(jdk));
        }
        String sourceLink = properties.value(SOURCE_LINK), sourceLinkSuffix = properties.value(SOURCE_LINK_SUFFIX);
        if (sourceLink == null && sourceLinkSuffix != null) {
            throw new IllegalArgumentException("The dokka property " + SOURCE_LINK_SUFFIX + " of " + file + " names"
                    + " the line suffix of a source link, but " + SOURCE_LINK + " names none - declare " + SOURCE_LINK
                    + "=<url of the source folder>");
        }
        if (sourceLink != null) {
            module = module.sourceLink(url(file, SOURCE_LINK, sourceLink));
        }
        if (sourceLinkSuffix != null) {
            module = module.sourceLinkSuffix(sourceLinkSuffix);
        }
        for (String key : new TreeSet<>(properties.stringPropertyNames())) {
            if (key.startsWith(EXTERNAL_LINK) && !key.endsWith(PACKAGE_LIST)) {
                String url = properties.value(key), packageList = properties.value(key + PACKAGE_LIST);
                if (url == null) {
                    throw new IllegalArgumentException("The dokka property " + key + " of " + file + " names no URL"
                            + " - name the root of the documentation to link to");
                }
                module = packageList == null
                        ? module.externalLink(url(file, key, url))
                        : module.externalLink(url(file, key, url), url(file, key + PACKAGE_LIST, packageList));
            }
        }
        SequencedMap<String, List<Path>> files = new LinkedHashMap<>();
        for (String key : List.of(INCLUDES, CUSTOM_ASSETS, CUSTOM_STYLES)) {
            List<Path> sources = new ArrayList<>();
            for (String value : Objects.requireNonNullElse(properties.entries(key), List.<String>of())) {
                Path source = location == null ? Path.of(value) : location.resolve(value);
                if (!Files.isRegularFile(source)) {
                    throw new IllegalArgumentException("The dokka property " + key + " of " + file + " names " + value
                            + ", but " + source.toAbsolutePath().normalize() + " is no file - name a file relative to"
                            + " the module's folder" + (location == null ? "" : " " + location));
                }
                sources.add(source);
            }
            files.put(key, sources);
        }
        String footerMessage = properties.value(FOOTER_MESSAGE);
        return (footerMessage == null ? module : module.footerMessage(footerMessage))
                .includes(files.get(INCLUDES))
                .customAssets(files.get(CUSTOM_ASSETS))
                .customStyles(files.get(CUSTOM_STYLES));
    }

    private static URI url(Path file, String key, String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The dokka property " + key + " of " + file + " names " + value
                    + ", which is no URL: " + e.getReason());
        }
    }

    private static class Scan implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            boolean[] flags = new boolean[4];
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path sources = argument.folder().resolve(BuildStep.SOURCES);
                if (!Files.exists(sources)) {
                    continue;
                }
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".java")) {
                            flags[0] = true;
                        } else if (name.endsWith(".kt")) {
                            flags[1] = true;
                        } else if (name.endsWith(".scala")) {
                            flags[2] = true;
                        } else if (name.endsWith(".groovy")) {
                            flags[3] = true;
                        }
                        return flags[0] && flags[1] && flags[2] && flags[3]
                                ? FileVisitResult.TERMINATE
                                : FileVisitResult.CONTINUE;
                    }
                });
                if (flags[0] && flags[1] && flags[2] && flags[3]) {
                    break;
                }
            }
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty(JAVADOC, Boolean.toString(flags[0]));
            properties.setProperty(DOKKA, Boolean.toString(flags[1]));
            properties.setProperty(SCALADOC, Boolean.toString(flags[2]));
            properties.setProperty(GROOVYDOC, Boolean.toString(flags[3]));
            properties.store(context.next().resolve(SCAN_FILE));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Document(Map<String, Repository> repositories,
                            Map<String, Resolver> resolvers,
                            Pinning pinning,
                            Javadoc javadocStep,
                            DokkaDocumentationModule dokkaModule,
                            ScalaDocumentationModule scaladocModule,
                            GroovyDocumentationModule groovydocModule,
                            Function<Javadoc, BuildStep> javadoc,
                            Function<DokkaDocumentationModule, BuildExecutorModule> dokka,
                            Function<ScalaDocumentationModule, BuildExecutorModule> scaladoc,
                            Function<GroovyDocumentationModule, BuildExecutorModule> groovydoc)
            implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            Path scanFolder = inherited.get(PREVIOUS + SCAN);
            if (scanFolder == null) {
                throw new IllegalStateException("Document sub-module is missing its upstream scan input");
            }
            SequencedProperties scan = SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE));
            boolean hasJava = scan.flag(JAVADOC) && javadoc != null;
            boolean hasGroovy = scan.flag(GROOVYDOC) && groovydoc != null;
            boolean hasScala = scan.flag(SCALADOC) && scaladoc != null;
            boolean hasKotlin = scan.flag(DOKKA) && dokka != null;

            SequencedSet<String> sourceInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
            sourceInputs.remove(PREVIOUS + SCAN);

            SequencedSet<String> outputs = new LinkedHashSet<>();
            if (hasKotlin && !hasScala && !hasGroovy) {
                document(buildExecutor,
                         sourceInputs,
                         outputs,
                         DOKKA,
                         dokka.apply(dokkaModule.pinning(pinning)));
            } else if (hasGroovy && !hasScala && !hasKotlin) {
                document(buildExecutor,
                         sourceInputs,
                         outputs,
                         GROOVYDOC,
                         groovydoc.apply(groovydocModule
                                .pinning(pinning)
                                .includeJava(hasJava)));
            } else if (hasScala && !hasJava && !hasKotlin && !hasGroovy) {
                document(buildExecutor,
                         sourceInputs,
                         outputs,
                         SCALADOC,
                         scaladoc.apply(scaladocModule.pinning(pinning)));
            } else if (hasJava && !hasKotlin && !hasScala && !hasGroovy) {
                document(buildExecutor,
                         sourceInputs,
                         outputs,
                         JAVADOC,
                         javadoc.apply(javadocStep));
            } else {
                if (hasJava) {
                    document(buildExecutor,
                             sourceInputs,
                             outputs,
                             JAVADOC,
                             javadoc.apply(javadocStep.classpath(true)));
                }
                if (hasKotlin) {
                    document(buildExecutor,
                             sourceInputs,
                             outputs,
                             DOKKA,
                             dokka.apply(dokkaModule
                                    .pinning(pinning)
                                    .within(DOKKA)));
                }
                if (hasScala) {
                    document(buildExecutor,
                             sourceInputs,
                             outputs,
                             SCALADOC,
                             scaladoc.apply(scaladocModule
                                    .pinning(pinning)
                                    .within(SCALADOC)));
                }
                if (hasGroovy) {
                    document(buildExecutor,
                             sourceInputs,
                             outputs,
                             GROOVYDOC,
                             groovydoc.apply(groovydocModule
                                    .pinning(pinning)
                                    .within(GROOVYDOC)));
                }
            }
            buildExecutor.addStep(AGGREGATE, new Aggregate(), outputs);
        }

        private static void document(BuildExecutor buildExecutor,
                                     SequencedSet<String> inputs,
                                     SequencedSet<String> outputs,
                                     String name,
                                     BuildExecutorModule module) {
            if (module != null) {
                buildExecutor.addModule(name, module, inputs);
                outputs.add(name);
            }
        }

        private static void document(BuildExecutor buildExecutor,
                                     SequencedSet<String> inputs,
                                     SequencedSet<String> outputs,
                                     String name,
                                     BuildStep step) {
            if (step != null) {
                buildExecutor.addStep(name, step, inputs);
                outputs.add(name);
            }
        }
    }

    private static class Aggregate implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path combined = Files.createDirectories(context.next().resolve(Javadoc.JAVADOC));
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path source = argument.folder().resolve(Javadoc.JAVADOC);
                if (!Files.exists(source)) {
                    continue;
                }
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(combined.resolve(source.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        BuildStep.linkOrCopy(combined.resolve(source.relativize(file)), file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            if (!Files.exists(combined.resolve("index.html"))) {
                SequencedSet<String> rendered = new TreeSet<>();
                try (Stream<Path> entries = Files.list(combined)) {
                    for (Path entry : entries.toList()) {
                        if (Files.isDirectory(entry) && Files.exists(entry.resolve("index.html"))) {
                            rendered.add(entry.getFileName().toString());
                        }
                    }
                }
                StringBuilder body = new StringBuilder();
                body.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                        .append("<title>API documentation</title></head><body>\n")
                        .append("<h1>API documentation</h1>\n");
                if (rendered.isEmpty()) {
                    body.append("<p>No rendered API documentation is available for this module.</p>\n");
                } else {
                    body.append("<p>API documentation is available per language:</p>\n<ul>\n");
                    for (String name : rendered) {
                        body.append("<li><a href=\"").append(name).append("/index.html\">")
                                .append(name).append("</a></li>\n");
                    }
                    body.append("</ul>\n");
                }
                body.append("</body></html>\n");
                Files.writeString(combined.resolve("index.html"), body.toString());
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
