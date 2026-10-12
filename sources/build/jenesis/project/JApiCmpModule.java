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
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JApiCmpModule implements BuildExecutorModule {

    public static final String COMPARE = "compare";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "com.github.siom79.japicmp", MAVEN_ARTIFACT = "japicmp";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final String baseline;
    private final Access access;
    private final List<String> include;
    private final List<String> exclude;
    private final Set<Format> formats;
    private final boolean ignoreMissingClasses;
    private final boolean onlyIncompatible;
    private final boolean onlyModified;
    private final boolean semanticVersioning;
    private final Set<Failure> errorOn;
    private final ProcessBuildStep.Terms terms;

    public JApiCmpModule(Map<String, Repository> repositories,
                         Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "japicmp",
             "main",
             null,
             null,
             List.of(),
             List.of(),
             Collections.unmodifiableSet(EnumSet.of(Format.XML)),
             true,
             false,
             false,
             false,
             Collections.unmodifiableSet(EnumSet.noneOf(Failure.class)),
             new ProcessBuildStep.Terms());
    }

    public static JApiCmpModule ofEnvironment(Environment environment,
                                              Map<String, Repository> repositories,
                                              Map<String, Resolver> resolvers) {
        return new JApiCmpModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "japicmp",
                "main",
                null,
                null,
                List.of(),
                List.of(),
                Collections.unmodifiableSet(EnumSet.of(Format.XML)),
                true,
                false,
                false,
                false,
                Collections.unmodifiableSet(EnumSet.noneOf(Failure.class)),
                ProcessBuildStep.Terms.ofEnvironment(environment, "japicmp"));
    }

    private JApiCmpModule(Dependencies dependencies,
                          Pinning pinning,
                          String tool,
                          String group,
                          String baseline,
                          Access access,
                          List<String> include,
                          List<String> exclude,
                          Set<Format> formats,
                          boolean ignoreMissingClasses,
                          boolean onlyIncompatible,
                          boolean onlyModified,
                          boolean semanticVersioning,
                          Set<Failure> errorOn,
                          ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.baseline = baseline;
        this.access = access;
        this.include = include;
        this.exclude = exclude;
        this.formats = formats;
        this.ignoreMissingClasses = ignoreMissingClasses;
        this.onlyIncompatible = onlyIncompatible;
        this.onlyModified = onlyModified;
        this.semanticVersioning = semanticVersioning;
        this.errorOn = errorOn;
        this.terms = terms;
    }

    public JApiCmpModule pinning(Pinning pinning) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule tool(String tool) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule group(String group) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule baseline(String baseline) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule access(Access access) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule include(List<String> include) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, List.copyOf(include), exclude,
                formats, ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule exclude(List<String> exclude) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, List.copyOf(exclude),
                formats, ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule formats(Set<Format> formats) {
        EnumSet<Format> copied = EnumSet.noneOf(Format.class);
        copied.addAll(formats);
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude,
                Collections.unmodifiableSet(copied), ignoreMissingClasses, onlyIncompatible, onlyModified,
                semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule ignoreMissingClasses(boolean ignoreMissingClasses) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule onlyIncompatible(boolean onlyIncompatible) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule onlyModified(boolean onlyModified) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule semanticVersioning(boolean semanticVersioning) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn, terms);
    }

    public JApiCmpModule errorOn(Set<Failure> errorOn) {
        EnumSet<Failure> copied = EnumSet.noneOf(Failure.class);
        copied.addAll(errorOn);
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning,
                Collections.unmodifiableSet(copied), terms);
    }

    public JApiCmpModule printing(BiConsumer<Boolean, String> printing) {
        return new JApiCmpModule(dependencies, pinning, tool, group, baseline, access, include, exclude, formats,
                ignoreMissingClasses, onlyIncompatible, onlyModified, semanticVersioning, errorOn,
                terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED,
                new Baseline(tool, baseline, List.of(MAVEN_GROUP + "/" + MAVEN_ARTIFACT), "japicmp.properties"),
                inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> compareInputs = new LinkedHashSet<>();
        compareInputs.add(DEPENDENCIES);
        compareInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(COMPARE, new Compare(terms,
                tool,
                group,
                access,
                include,
                exclude,
                List.copyOf(formats),
                ignoreMissingClasses,
                onlyIncompatible,
                onlyModified,
                semanticVersioning,
                List.copyOf(errorOn)), compareInputs);
    }

    private static class Compare extends ProcessBuildStep {

        private final String tool;
        private final String group;
        private final Access access;
        private final List<String> include;
        private final List<String> exclude;
        private final List<Format> formats;
        private final boolean ignoreMissingClasses;
        private final boolean onlyIncompatible;
        private final boolean onlyModified;
        private final boolean semanticVersioning;
        private final List<Failure> errorOn;

        private Compare(ProcessBuildStep.Terms terms,
                        String tool,
                        String group,
                        Access access,
                        List<String> include,
                        List<String> exclude,
                        List<Format> formats,
                        boolean ignoreMissingClasses,
                        boolean onlyIncompatible,
                        boolean onlyModified,
                        boolean semanticVersioning,
                        List<Failure> errorOn) {
            super("japicmp", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.group = group;
            this.access = access;
            this.include = include;
            this.exclude = exclude;
            this.formats = formats;
            this.ignoreMissingClasses = ignoreMissingClasses;
            this.onlyIncompatible = onlyIncompatible;
            this.onlyModified = onlyModified;
            this.semanticVersioning = semanticVersioning;
            this.errorOn = errorOn;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), baselines = new ArrayList<>(), classPath = new ArrayList<>();
            Path artifact = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "baseline")) {
                    baselines.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    classPath.add(jar.toString());
                }
                Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
                if (Files.isDirectory(artifacts)) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts, "*.jar")) {
                        for (Path file : files) {
                            artifact = file;
                        }
                    }
                }
            }
            if (artifact == null) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No japicmp jars resolved upstream of the japicmp step");
            }
            if (baselines.size() != 1) {
                throw new IllegalStateException("Expected exactly one baseline artifact upstream of the japicmp step: "
                        + baselines);
            }
            Path report = Files.createDirectories(context.next().resolve(BuildStep.REPORTS + "japicmp"));
            ForkedOptions forked = forked(properties);
            List<String> commands = new ArrayList<>(forked.jvm()), options = forked.program();
            commands.addAll(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "japicmp.JApiCmp",
                    "-o", baselines.getFirst(),
                    "-n", artifact.toString()));
            for (Format format : formats) {
                commands.add("--" + format.name().toLowerCase(Locale.ROOT) + "-file");
                commands.add(report.resolve("japicmp-report." + format.name().toLowerCase(Locale.ROOT)).toString());
            }
            if (access != null) {
                commands.add("-a");
                commands.add(access.name().toLowerCase(Locale.ROOT));
            }
            if (!include.isEmpty()) {
                commands.add("-i");
                commands.add(String.join(";", include));
            }
            if (!exclude.isEmpty()) {
                commands.add("-e");
                commands.add(String.join(";", exclude));
            }
            if (!classPath.isEmpty()) {
                String joined = String.join(File.pathSeparator, classPath);
                commands.add("--old-classpath");
                commands.add(joined);
                commands.add("--new-classpath");
                commands.add(joined);
            }
            if (ignoreMissingClasses) {
                commands.add("--ignore-missing-classes");
            }
            if (onlyIncompatible) {
                commands.add("-b");
            }
            if (onlyModified) {
                commands.add("-m");
            }
            if (semanticVersioning) {
                commands.add("-s");
            }
            for (Failure failure : errorOn) {
                commands.add("--error-on-" + failure.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            }
            commands.addAll(options);
            return CompletableFuture.completedStage(commands);
        }
    }

    public enum Access {

        PUBLIC, PROTECTED, PACKAGE, PRIVATE
    }

    public enum Format {

        XML, HTML
    }

    public enum Failure {

        BINARY_INCOMPATIBILITY, SOURCE_INCOMPATIBILITY, MODIFICATIONS, SEMANTIC_INCOMPATIBILITY
    }
}
