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
            KTFMT = "ktfmt",
            SCALAFMT = "scalafmt",
            LICENSE = "license";
    private static final String FORMATTER = "formatter", STYLE = "style", REFLOW = "reflow", JAVADOC = "javadoc", IMPORTS = "imports", PROFILE = "profile";
    private static final String SORT = "sort", REMOVE = "remove", NONE = "none";
    private static final String TEXT = "text";
    private static final Set<String> GOOGLE_KEYS = Set.of(FORMATTER, STYLE, REFLOW, JAVADOC, IMPORTS);
    private static final Set<String> PALANTIR_KEYS = Set.of(FORMATTER, REFLOW, IMPORTS);
    private static final Set<String> ECLIPSE_KEYS = Set.of(FORMATTER, PROFILE);
    private static final Set<String> KTFMT_KEYS = Set.of(STYLE, IMPORTS);

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final boolean verify;
    private final GoogleJavaFormatModule googleModule;
    private final PalantirJavaFormatModule palantirModule;
    private final EclipseJavaFormatModule eclipseModule;
    private final KtlintFormatModule ktlintModule;
    private final KtfmtFormatModule ktfmtModule;
    private final ScalafmtFormatModule scalafmtModule;
    private final LicenseHeaderModule licenseModule;
    private final Function<BuildExecutorModule, BuildExecutorModule> java;
    private final Function<KtlintFormatModule, BuildExecutorModule> ktlint;
    private final Function<KtfmtFormatModule, BuildExecutorModule> ktfmt;
    private final Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt;
    private final Function<LicenseHeaderModule, BuildExecutorModule> license;
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
                new KtfmtFormatModule(repositories, resolvers),
                new ScalafmtFormatModule(repositories, resolvers),
                new LicenseHeaderModule(),
                value -> value,
                value -> value,
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
                KtfmtFormatModule.ofEnvironment(environment, repositories, resolvers),
                ScalafmtFormatModule.ofEnvironment(environment, repositories, resolvers),
                new LicenseHeaderModule(),
                value -> value,
                value -> value,
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
        Boolean ktfmt = environment.flagOrNull("format.ktfmt");
        if (ktfmt != null) {
            module = module.ktfmt(ktfmt ? value -> value : null);
        }
        Boolean scalafmt = environment.flagOrNull("format.scalafmt");
        if (scalafmt != null) {
            module = module.scalafmt(scalafmt ? value -> value : null);
        }
        Boolean license = environment.flagOrNull("format.license");
        if (license != null) {
            module = module.license(license ? value -> value : null);
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
                                           KtfmtFormatModule ktfmtModule,
                                           ScalafmtFormatModule scalafmtModule,
                                           LicenseHeaderModule licenseModule,
                                           Function<BuildExecutorModule, BuildExecutorModule> java,
                                           Function<KtlintFormatModule, BuildExecutorModule> ktlint,
                                           Function<KtfmtFormatModule, BuildExecutorModule> ktfmt,
                                           Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt,
                                           Function<LicenseHeaderModule, BuildExecutorModule> license,
                                           SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.verify = verify;
        this.googleModule = googleModule;
        this.palantirModule = palantirModule;
        this.eclipseModule = eclipseModule;
        this.ktlintModule = ktlintModule;
        this.ktfmtModule = ktfmtModule;
        this.scalafmtModule = scalafmtModule;
        this.licenseModule = licenseModule;
        this.java = java;
        this.ktlint = ktlint;
        this.ktfmt = ktfmt;
        this.scalafmt = scalafmt;
        this.license = license;
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
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
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
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
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
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
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
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
                custom);
    }

    public InferredSourceFormattingModule ktfmt(Function<KtfmtFormatModule, BuildExecutorModule> ktfmt) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
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
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
                custom);
    }

    public InferredSourceFormattingModule license(Function<LicenseHeaderModule, BuildExecutorModule> license) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                eclipseModule,
                ktlintModule,
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
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
                ktfmtModule,
                scalafmtModule,
                licenseModule,
                java,
                ktlint,
                ktfmt,
                scalafmt,
                license,
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
        SequencedSet<String> inputs = new LinkedHashSet<>(inherited.sequencedKeySet());
        if (Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                LICENSE,
                license,
                LicenseHeaderModule.configurationFile(configuration),
                properties -> {
                    for (String key : properties.stringPropertyNames()) {
                        if (!key.equals(TEXT)) {
                            throw new IllegalArgumentException(LicenseHeaderModule.CONFIGURATION + " names " + key
                                    + ", but takes " + TEXT + "=<the header> alone");
                        }
                    }
                    return licenseModule.verify(verify).text(properties.value(TEXT));
                })) {
            inputs.add(LICENSE);
        }
        Path javaFormat = BuildStep.locate(configuration, "javaformat.properties");
        Bind.configuredByProperties(buildExecutor,
                inputs,
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
        Path ktfmtFile = ktfmt == null ? null : KtfmtFormatModule.configurationFile(configuration);
        if (ktfmtFile != null && ktlint != null && KtlintFormatModule.configurationFile(configuration) != null) {
            throw new IllegalArgumentException(ktfmtFile + " selects ktfmt while "
                    + KtlintFormatModule.configurationFile(configuration) + " selects ktlint to format the same Kotlin"
                    + " sources - set jenesis.format.ktlint=false to format with ktfmt alone, or remove ktfmt.properties");
        }
        Bind.configuredByProperties(buildExecutor,
                inputs,
                KTFMT,
                ktfmt,
                ktfmtFile,
                properties -> {
                    for (String key : properties.stringPropertyNames()) {
                        if (!KTFMT_KEYS.contains(key)) {
                            throw new IllegalArgumentException("ktfmt.properties names " + key
                                    + ", which ktfmt does not take - its keys are " + new TreeSet<>(KTFMT_KEYS));
                        }
                    }
                    List<String> imports = properties.entries(IMPORTS);
                    if (imports != null
                            && !imports.equals(List.of(SORT))
                            && !Set.copyOf(imports).equals(Set.of(SORT, REMOVE))) {
                        throw new IllegalArgumentException("ktfmt.properties names " + IMPORTS + "="
                                + properties.value(IMPORTS) + ", but ktfmt always sorts the imports - " + SORT + ","
                                + REMOVE + " to remove the unused ones as well, or " + SORT + " to keep them");
                    }
                    return ktfmtModule.pinning(pinning)
                            .verify(verify)
                            .style(switch (properties.value(STYLE, "meta")) {
                                case "meta" -> KtfmtFormatModule.Style.META;
                                case "google" -> KtfmtFormatModule.Style.GOOGLE;
                                case "kotlinlang" -> KtfmtFormatModule.Style.KOTLINLANG;
                                default -> throw new IllegalArgumentException("ktfmt.properties names " + STYLE + "="
                                        + properties.value(STYLE) + ", which ktfmt does not know - meta, google or"
                                        + " kotlinlang");
                            })
                            .removeUnusedImports(imports == null || imports.contains(REMOVE));
                });
        Bind.configured(buildExecutor,
                inputs,
                KTLINT,
                ktlint,
                KtlintFormatModule.configurationFile(configuration),
                () -> ktlintModule.pinning(pinning).verify(verify));
        Bind.configured(buildExecutor,
                inputs,
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
