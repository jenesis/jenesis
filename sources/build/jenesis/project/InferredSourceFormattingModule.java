package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;

public class InferredSourceFormattingModule implements BuildExecutorModule {

    public static final String JAVA = "java", KTFMT = "ktfmt", KTLINT = "ktlint", SCALAFMT = "scalafmt";
    private static final String LICENSE = "license", TEXT = "text", FORMATTER = "formatter", PROFILE = "profile";

    private final SequencedSet<Path> configuration;
    private final boolean verify;
    private final LicenseHeaderModule licenseModule;
    private final Function<LicenseHeaderModule, BuildExecutorModule> license;
    private final ExternalModule googleJavaFormatModule;
    private final ExternalModule palantirJavaFormatModule;
    private final ExternalModule eclipseJavaFormatModule;
    private final boolean java;
    private final ExternalModule ktfmtModule;
    private final boolean ktfmt;
    private final ExternalModule ktlintModule;
    private final boolean ktlint;
    private final ExternalModule scalafmtModule;
    private final boolean scalafmt;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration,
                true,
                new LicenseHeaderModule(),
                value -> value,
                ExternalModule.official(Environment.NONE, "googlejavaformat", null, repositories, resolvers),
                ExternalModule.official(Environment.NONE, "palantir", null, repositories, resolvers),
                ExternalModule.official(Environment.NONE, "eclipse", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ktfmt", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ktlint", "format", repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "scalafmt", "format", repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
    }

    public static InferredSourceFormattingModule ofEnvironment(Environment environment,
                                                               SequencedSet<Path> configuration,
                                                               Map<String, Repository> repositories,
                                                               Map<String, Resolver> resolvers) {
        InferredSourceFormattingModule module = new InferredSourceFormattingModule(configuration,
                true,
                new LicenseHeaderModule(),
                value -> value,
                ExternalModule.official(environment, "googlejavaformat", null, repositories, resolvers),
                ExternalModule.official(environment, "palantir", null, repositories, resolvers),
                ExternalModule.official(environment, "eclipse", null, repositories, resolvers),
                true,
                ExternalModule.official(environment, "ktfmt", null, repositories, resolvers),
                true,
                ExternalModule.official(environment, "ktlint", "format", repositories, resolvers),
                true,
                ExternalModule.official(environment, "scalafmt", "format", repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
        Boolean rewrite = environment.flagOrNull("format.rewrite");
        if (rewrite != null) {
            module = module.verify(!rewrite);
        }
        Boolean license = environment.flagOrNull("format.license");
        if (license != null) {
            module = module.license(license ? value -> value : null);
        }
        if (!environment.flag("format.java", true)) {
            module = module.java(false);
        }
        if (!environment.flag("format.ktfmt", true)) {
            module = module.ktfmt(false);
        }
        if (!environment.flag("format.ktlint", true)) {
            module = module.ktlint(false);
        }
        if (!environment.flag("format.scalafmt", true)) {
            module = module.scalafmt(false);
        }
        return module;
    }

    private InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                           boolean verify,
                                           LicenseHeaderModule licenseModule,
                                           Function<LicenseHeaderModule, BuildExecutorModule> license,
                                           ExternalModule googleJavaFormatModule,
                                           ExternalModule palantirJavaFormatModule,
                                           ExternalModule eclipseJavaFormatModule,
                                           boolean java,
                                           ExternalModule ktfmtModule,
                                           boolean ktfmt,
                                           ExternalModule ktlintModule,
                                           boolean ktlint,
                                           ExternalModule scalafmtModule,
                                           boolean scalafmt,
                                           SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.verify = verify;
        this.licenseModule = licenseModule;
        this.license = license;
        this.googleJavaFormatModule = googleJavaFormatModule;
        this.palantirJavaFormatModule = palantirJavaFormatModule;
        this.eclipseJavaFormatModule = eclipseJavaFormatModule;
        this.java = java;
        this.ktfmtModule = ktfmtModule;
        this.ktfmt = ktfmt;
        this.ktlintModule = ktlintModule;
        this.ktlint = ktlint;
        this.scalafmtModule = scalafmtModule;
        this.scalafmt = scalafmt;
        this.custom = custom;
    }

    public InferredSourceFormattingModule verify(boolean verify) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule license(Function<LicenseHeaderModule, BuildExecutorModule> license) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule java(boolean java) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule ktfmt(boolean ktfmt) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule ktlint(boolean ktlint) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule scalafmt(boolean scalafmt) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                googleJavaFormatModule,
                palantirJavaFormatModule,
                eclipseJavaFormatModule,
                java,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
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
        if (javaFormat != null && java) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(javaFormat).forEachProperty(values::put);
            String formatter = values.remove(FORMATTER);
            ExternalModule module = switch (formatter == null ? null : formatter.trim()) {
                case "google" -> googleJavaFormatModule;
                case "palantir" -> palantirJavaFormatModule;
                case "eclipse" -> {
                    String profile = values.remove(PROFILE);
                    if (profile == null) {
                        yield eclipseJavaFormatModule;
                    }
                    Path relative = Path.of(profile.trim()).normalize();
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
                    yield eclipseJavaFormatModule.bound(PROFILE, Path.of("eclipse-formatter.xml"), resolved);
                }
                case null -> {
                    if (!values.isEmpty()) {
                        throw new IllegalArgumentException("javaformat.properties names " + values.sequencedKeySet()
                                + " but no formatter - add formatter=google, palantir or eclipse");
                    }
                    yield null;
                }
                default -> throw new IllegalArgumentException("javaformat.properties names formatter=" + formatter
                        + ", which is no Java formatter - google, palantir or eclipse");
            };
            if (module != null) {
                buildExecutor.addModule(JAVA, module.properties(values), inputs);
            }
        }
        Path ktfmtFile = ktfmt ? BuildStep.locate(configuration, "ktfmt.properties") : null;
        Path editorConfig = BuildStep.locate(configuration, ".editorconfig");
        if (ktfmtFile != null && ktlint && editorConfig != null) {
            throw new IllegalArgumentException(ktfmtFile + " selects ktfmt while " + editorConfig + " selects ktlint"
                    + " to format the same Kotlin sources - set jenesis.format.ktlint=false to format with ktfmt"
                    + " alone, or remove ktfmt.properties");
        }
        if (ktfmtFile != null && ktfmt) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(ktfmtFile).forEachProperty(values::put);
            buildExecutor.addModule(KTFMT, ktfmtModule.properties(values), inputs);
        }
        if (editorConfig != null && ktlint) {
            buildExecutor.addModule(KTLINT,
                    ktlintModule.bound(ExternalModule.RULES, editorConfig.getFileName(), editorConfig),
                    inputs);
        }
        Path scalafmtFile = BuildStep.locate(configuration, ".scalafmt.conf");
        if (scalafmtFile != null && scalafmt) {
            buildExecutor.addModule(SCALAFMT,
                    scalafmtModule.bound(ExternalModule.RULES, scalafmtFile.getFileName(), scalafmtFile),
                    inputs);
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inputs);
        }
    }
}
