package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class InferredSourceCodeQualityModule implements BuildExecutorModule {

    public static final String CHECKSTYLE = "checkstyle",
            PMD = "pmd",
            DETEKT = "detekt",
            KTLINT = "ktlint",
            SCALASTYLE = "scalastyle",
            SCALAFMT = "scalafmt",
            CODENARC = "codenarc";
    private static final Pattern CONFIG_LOC = Pattern.compile("\\$\\{config_loc}/([^\"'<>${}\\s]+)");

    private final SequencedSet<Path> configuration;
    private final ExternalModule checkstyleModule;
    private final boolean checkstyle;
    private final ExternalModule pmdModule;
    private final boolean pmd;
    private final ExternalModule detektModule;
    private final boolean detekt;
    private final ExternalModule ktlintModule;
    private final boolean ktlint;
    private final ExternalModule scalastyleModule;
    private final boolean scalastyle;
    private final ExternalModule scalafmtModule;
    private final boolean scalafmt;
    private final ExternalModule codenarcModule;
    private final boolean codenarc;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers) {
        this(configuration,
                ExternalModule.official(Environment.NONE, "checkstyle", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "pmd", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "detekt", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ktlint", "check", repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "scalastyle", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "scalafmt", "check", repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "codenarc", null, repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
    }

    public static InferredSourceCodeQualityModule ofEnvironment(Environment environment,
                                                                SequencedSet<Path> configuration,
                                                                Map<String, Repository> repositories,
                                                                Map<String, Resolver> resolvers) {
        InferredSourceCodeQualityModule module = new InferredSourceCodeQualityModule(configuration,
                ExternalModule.official(environment, "checkstyle", null, repositories, resolvers),
                true,
                ExternalModule.official(environment, "pmd", null, repositories, resolvers),
                true,
                ExternalModule.official(environment, "detekt", null, repositories, resolvers),
                true,
                ExternalModule.official(environment, "ktlint", "check", repositories, resolvers),
                true,
                ExternalModule.official(environment, "scalastyle", null, repositories, resolvers),
                true,
                ExternalModule.official(environment, "scalafmt", "check", repositories, resolvers),
                true,
                ExternalModule.official(environment, "codenarc", null, repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
        if (Enforcement.ofEnvironment(environment, "source.checkstyle") == Enforcement.IGNORE) {
            module = module.checkstyle(false);
        }
        if (Enforcement.ofEnvironment(environment, "source.pmd") == Enforcement.IGNORE) {
            module = module.pmd(false);
        }
        if (Enforcement.ofEnvironment(environment, "source.detekt") == Enforcement.IGNORE) {
            module = module.detekt(false);
        }
        if (Enforcement.ofEnvironment(environment, "source.ktlint") == Enforcement.IGNORE) {
            module = module.ktlint(false);
        }
        if (Enforcement.ofEnvironment(environment, "source.scalastyle") == Enforcement.IGNORE) {
            module = module.scalastyle(false);
        }
        if (Enforcement.ofEnvironment(environment, "source.scalafmt") == Enforcement.IGNORE) {
            module = module.scalafmt(false);
        }
        if (Enforcement.ofEnvironment(environment, "source.codenarc") == Enforcement.IGNORE) {
            module = module.codenarc(false);
        }
        return module;
    }

    private InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                            ExternalModule checkstyleModule,
                                            boolean checkstyle,
                                            ExternalModule pmdModule,
                                            boolean pmd,
                                            ExternalModule detektModule,
                                            boolean detekt,
                                            ExternalModule ktlintModule,
                                            boolean ktlint,
                                            ExternalModule scalastyleModule,
                                            boolean scalastyle,
                                            ExternalModule scalafmtModule,
                                            boolean scalafmt,
                                            ExternalModule codenarcModule,
                                            boolean codenarc,
                                            SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.checkstyleModule = checkstyleModule;
        this.checkstyle = checkstyle;
        this.pmdModule = pmdModule;
        this.pmd = pmd;
        this.detektModule = detektModule;
        this.detekt = detekt;
        this.ktlintModule = ktlintModule;
        this.ktlint = ktlint;
        this.scalastyleModule = scalastyleModule;
        this.scalastyle = scalastyle;
        this.scalafmtModule = scalafmtModule;
        this.scalafmt = scalafmt;
        this.codenarcModule = codenarcModule;
        this.codenarc = codenarc;
        this.custom = custom;
    }

    public InferredSourceCodeQualityModule checkstyle(boolean checkstyle) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule pmd(boolean pmd) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule detekt(boolean detekt) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule ktlint(boolean ktlint) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule scalastyle(boolean scalastyle) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule scalafmt(boolean scalafmt) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule codenarc(boolean codenarc) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceCodeQualityModule(configuration,
                checkstyleModule,
                checkstyle,
                pmdModule,
                pmd,
                detektModule,
                detekt,
                ktlintModule,
                ktlint,
                scalastyleModule,
                scalastyle,
                scalafmtModule,
                scalafmt,
                codenarcModule,
                codenarc,
                custom);
    }

    public InferredSourceCodeQualityModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredSourceCodeQualityModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path checkstyleFile = BuildStep.locate(configuration, "checkstyle.xml");
        if (checkstyleFile != null && checkstyle) {
            ExternalModule module = checkstyleModule.bound(ExternalModule.RULES, checkstyleFile.getFileName(), checkstyleFile);
            Matcher matcher = CONFIG_LOC.matcher(Files.readString(checkstyleFile));
            while (matcher.find()) {
                Path sibling = Path.of(matcher.group(1)).normalize();
                if (sibling.isAbsolute() || sibling.startsWith("..")) {
                    throw new IllegalArgumentException(checkstyleFile + " references ${config_loc}/" + matcher.group(1)
                            + ", which is not beside or below it - place the file in the folder of "
                            + checkstyleFile.getFileName());
                }
                if (!sibling.equals(checkstyleFile.getFileName())
                        && Files.isRegularFile(checkstyleFile.resolveSibling(sibling))) {
                    module = module.bound(ExternalModule.RULES, sibling, checkstyleFile.resolveSibling(sibling));
                }
            }
            Path expansions = checkstyleFile.resolveSibling("checkstyle.properties");
            if (Files.isRegularFile(expansions)) {
                SequencedMap<String, String> values = new LinkedHashMap<>();
                SequencedProperties.ofFiles(expansions).forEachProperty(values::put);
                module = module.properties(values);
            }
            buildExecutor.addModule(CHECKSTYLE, module, inherited.sequencedKeySet());
        }
        Path pmdFile = BuildStep.locate(configuration, "pmd.xml");
        if (pmdFile != null && pmd) {
            buildExecutor.addModule(PMD,
                    pmdModule.bound(ExternalModule.RULES, pmdFile.getFileName(), pmdFile),
                    inherited.sequencedKeySet());
        }
        Path detektFile = BuildStep.locate(configuration, "detekt.yml");
        if (detektFile != null && detekt) {
            buildExecutor.addModule(DETEKT,
                    detektModule.bound(ExternalModule.RULES, detektFile.getFileName(), detektFile),
                    inherited.sequencedKeySet());
        }
        Path ktlintFile = BuildStep.locate(configuration, ".editorconfig");
        if (ktlintFile != null && ktlint) {
            buildExecutor.addModule(KTLINT,
                    ktlintModule.bound(ExternalModule.RULES, ktlintFile.getFileName(), ktlintFile),
                    inherited.sequencedKeySet());
        }
        Path scalastyleFile = BuildStep.locate(configuration, "scalastyle-config.xml");
        if (scalastyleFile != null && scalastyle) {
            buildExecutor.addModule(SCALASTYLE,
                    scalastyleModule.bound(ExternalModule.RULES, scalastyleFile.getFileName(), scalastyleFile),
                    inherited.sequencedKeySet());
        }
        Path scalafmtFile = BuildStep.locate(configuration, ".scalafmt.conf");
        if (scalafmtFile != null && scalafmt) {
            buildExecutor.addModule(SCALAFMT,
                    scalafmtModule.bound(ExternalModule.RULES, scalafmtFile.getFileName(), scalafmtFile),
                    inherited.sequencedKeySet());
        }
        Path codenarcFile = BuildStep.locate(configuration, "codenarc.xml");
        if (codenarcFile != null && codenarc) {
            buildExecutor.addModule(CODENARC,
                    codenarcModule.bound(ExternalModule.RULES, codenarcFile.getFileName(), codenarcFile),
                    inherited.sequencedKeySet());
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
