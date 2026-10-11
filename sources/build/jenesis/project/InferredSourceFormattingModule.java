package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;

public class InferredSourceFormattingModule implements BuildExecutorModule {

    private static final String JAVA = "java",
            KTLINT = "ktlint",
            SCALAFMT = "scalafmt";
    private static final String FORMATTER = "formatter", STYLE = "style", REFLOW = "reflow", JAVADOC = "javadoc",
            IMPORTS = "imports", SORT = "sort", REMOVE = "remove", NONE = "none", PROFILE = "profile";
    private static final Set<String> GOOGLE_KEYS = Set.of(FORMATTER, STYLE, REFLOW, JAVADOC, IMPORTS);
    private static final Set<String> PALANTIR_KEYS = Set.of(FORMATTER, REFLOW, IMPORTS);
    private static final Set<String> ECLIPSE_KEYS = Set.of(FORMATTER, PROFILE);

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final boolean verify;
    private final GoogleJavaFormatModule googleModule;
    private final PalantirJavaFormatModule palantirModule;
    private final EclipseJavaFormatModule eclipseModule;
    private final KtlintFormatModule ktlintModule;
    private final ScalafmtFormatModule scalafmtModule;
    private final Function<BuildExecutorModule, BuildExecutorModule> java;
    private final Function<KtlintFormatModule, BuildExecutorModule> ktlint;
    private final Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                true,
                new GoogleJavaFormatModule(repositories, resolvers),
                new PalantirJavaFormatModule(repositories, resolvers),
                new EclipseJavaFormatModule(repositories, resolvers),
                new KtlintFormatModule(repositories, resolvers),
                new ScalafmtFormatModule(repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
    }

    public static InferredSourceFormattingModule ofEnvironment(Environment environment,
                                                               SequencedSet<Path> configuration,
                                                               Map<String, Repository> repositories,
                                                               Map<String, Resolver> resolvers) {
        InferredSourceFormattingModule module = new InferredSourceFormattingModule(configuration,
                null,
                true,
                GoogleJavaFormatModule.ofEnvironment(environment, repositories, resolvers),
                PalantirJavaFormatModule.ofEnvironment(environment, repositories, resolvers),
                EclipseJavaFormatModule.ofEnvironment(environment, repositories, resolvers),
                KtlintFormatModule.ofEnvironment(environment, repositories, resolvers),
                ScalafmtFormatModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
        Boolean rewrite = environment.flagOrNull("format.rewrite");
        if (rewrite != null) {
            module = module.verify(!rewrite);
        }
        Boolean java = environment.flagOrNull("format.java");
        if (java != null) {
            module = module.java(java ? value -> value : null);
        }
        Boolean ktlint = environment.flagOrNull("format.ktlint");
        if (ktlint != null) {
            module = module.ktlint(ktlint ? value -> value : null);
        }
        Boolean scalafmt = environment.flagOrNull("format.scalafmt");
        if (scalafmt != null) {
            module = module.scalafmt(scalafmt ? value -> value : null);
        }
        return module;
    }

    private InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                           Pinning pinning,
                                           boolean verify,
                                           GoogleJavaFormatModule googleModule,
                                           PalantirJavaFormatModule palantirModule,
                                           EclipseJavaFormatModule eclipseModule,
                                           KtlintFormatModule ktlintModule,
                                           ScalafmtFormatModule scalafmtModule,
                                           Function<BuildExecutorModule, BuildExecutorModule> java,
                                           Function<KtlintFormatModule, BuildExecutorModule> ktlint,
                                           Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt,
                                           SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.verify = verify;
        this.googleModule = googleModule;
        this.palantirModule = palantirModule;
        this.eclipseModule = eclipseModule;
        this.ktlintModule = ktlintModule;
        this.scalafmtModule = scalafmtModule;
        this.java = java;
        this.ktlint = ktlint;
        this.scalafmt = scalafmt;
        this.custom = custom;
    }

    public InferredSourceFormattingModule pinning(Pinning pinning) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule verify(boolean verify) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule java(Function<BuildExecutorModule, BuildExecutorModule> java) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule ktlint(Function<KtlintFormatModule, BuildExecutorModule> ktlint) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule scalafmt(Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredSourceFormattingModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path javaFormat = BuildStep.locate(configuration, "javaformat.properties");
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                JAVA,
                java,
                javaFormat,
                properties -> switch (properties.value(FORMATTER)) {
                    case "google" -> {
                        Set<String> imports = javaFormat(properties, "google-java-format", GOOGLE_KEYS);
                        yield googleModule.pinning(pinning)
                                .verify(verify)
                                .aosp(switch (properties.value(STYLE, "google")) {
                                    case "google" -> false;
                                    case "aosp" -> true;
                                    default -> throw new IllegalArgumentException("javaformat.properties names "
                                            + STYLE + "=" + properties.value(STYLE)
                                            + ", which google-java-format does not know - google or aosp");
                                })
                                .reflow(properties.flag(REFLOW, true))
                                .javadoc(properties.flag(JAVADOC, true))
                                .sortImports(imports.contains(SORT))
                                .removeUnusedImports(imports.contains(REMOVE));
                    }
                    case "palantir" -> {
                        Set<String> imports = javaFormat(properties, "palantir-java-format", PALANTIR_KEYS);
                        yield palantirModule.pinning(pinning)
                                .verify(verify)
                                .reflow(properties.flag(REFLOW, true))
                                .sortImports(imports.contains(SORT))
                                .removeUnusedImports(imports.contains(REMOVE));
                    }
                    case "eclipse" -> {
                        javaFormat(properties, "the Eclipse formatter", ECLIPSE_KEYS);
                        String profile = properties.value(PROFILE);
                        if (profile == null) {
                            yield eclipseModule.pinning(pinning).verify(verify);
                        }
                        Path relative = Path.of(profile).normalize();
                        if (relative.isAbsolute() || relative.startsWith("..")) {
                            throw new IllegalArgumentException("javaformat.properties names " + PROFILE + "=" + profile
                                    + ", which is not beside or below it - place the profile in the folder of"
                                    + " javaformat.properties");
                        }
                        Path resolved = javaFormat.resolveSibling(relative);
                        if (!Files.isRegularFile(resolved)) {
                            throw new IllegalArgumentException("javaformat.properties names " + PROFILE + "=" + profile
                                    + ", but " + resolved + " is no file - export the formatter profile from Eclipse"
                                    + " there");
                        }
                        yield eclipseModule.pinning(pinning).verify(verify).profile(resolved);
                    }
                    case null -> {
                        if (!properties.stringPropertyNames().isEmpty()) {
                            throw new IllegalArgumentException("javaformat.properties names "
                                    + new TreeSet<>(properties.stringPropertyNames())
                                    + " but no formatter - add formatter=google, palantir or eclipse");
                        }
                        yield null;
                    }
                    default -> throw new IllegalArgumentException("javaformat.properties names formatter="
                            + properties.value(FORMATTER) + ", which is no Java formatter - google, palantir or eclipse");
                });
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                KTLINT,
                ktlint,
                KtlintFormatModule.configurationFile(configuration),
                () -> ktlintModule.pinning(pinning).verify(verify));
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                SCALAFMT,
                scalafmt,
                ScalafmtFormatModule.configurationFile(configuration),
                () -> scalafmtModule.pinning(pinning).verify(verify));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    private static Set<String> javaFormat(SequencedProperties properties, String formatter, Set<String> keys) {
        for (String key : properties.stringPropertyNames()) {
            if (!keys.contains(key)) {
                throw new IllegalArgumentException("javaformat.properties names " + key + ", which " + formatter
                        + " does not take - its keys are " + new TreeSet<>(keys));
            }
        }
        List<String> imports = properties.entries(IMPORTS);
        if (imports == null) {
            return Set.of(SORT, REMOVE);
        } else if (imports.equals(List.of(NONE))) {
            return Set.of();
        }
        for (String entry : imports) {
            if (!entry.equals(SORT) && !entry.equals(REMOVE)) {
                throw new IllegalArgumentException("javaformat.properties names " + IMPORTS + "="
                        + properties.value(IMPORTS) + ", but " + formatter + " takes " + SORT + ", " + REMOVE
                        + " or both, comma-separated, for what it does to the imports, or " + NONE);
            }
        }
        return Set.copyOf(imports);
    }
}
