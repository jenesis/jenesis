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
import build.jenesis.step.Dependencies;
import build.jenesis.step.FormatBuildStep;
import build.jenesis.step.ProcessBuildStep;

public class GoogleJavaFormatModule implements BuildExecutorModule {

    public static final String FORMAT = "format";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "com.google.googlejavaformat", MAVEN_ARTIFACT = "google-java-format";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String group;
    private final boolean verify;
    private final boolean aosp;
    private final boolean reflow;
    private final boolean javadoc;
    private final boolean sortImports;
    private final boolean removeUnusedImports;
    private final ProcessBuildStep.Terms terms;

    public GoogleJavaFormatModule(Map<String, Repository> repositories,
                                  Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "google-java-format",
             false,
             false,
             true,
             true,
             true,
             true,
             new ProcessBuildStep.Terms());
    }

    public static GoogleJavaFormatModule ofEnvironment(Environment environment,
                                                       Map<String, Repository> repositories,
                                                       Map<String, Resolver> resolvers) {
        return new GoogleJavaFormatModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "google-java-format",
                false,
                false,
                true,
                true,
                true,
                true,
                ProcessBuildStep.Terms.ofEnvironment(environment, "google-java-format"));
    }

    private GoogleJavaFormatModule(Dependencies dependencies,
                                   Pinning pinning,
                                   String group,
                                   boolean verify,
                                   boolean aosp,
                                   boolean reflow,
                                   boolean javadoc,
                                   boolean sortImports,
                                   boolean removeUnusedImports,
                                   ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.group = group;
        this.verify = verify;
        this.aosp = aosp;
        this.reflow = reflow;
        this.javadoc = javadoc;
        this.sortImports = sortImports;
        this.removeUnusedImports = removeUnusedImports;
        this.terms = terms;
    }

    public GoogleJavaFormatModule pinning(Pinning pinning) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule group(String group) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule verify(boolean verify) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule aosp(boolean aosp) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule reflow(boolean reflow) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule javadoc(boolean javadoc) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule sortImports(boolean sortImports) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule removeUnusedImports(boolean removeUnusedImports) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms);
    }

    public GoogleJavaFormatModule printing(BiConsumer<Boolean, String> printing) {
        return new GoogleJavaFormatModule(dependencies,
                pinning,
                group,
                verify,
                aosp,
                reflow,
                javadoc,
                sortImports,
                removeUnusedImports,
                terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(group), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(group),
                resolveInputs);
        SequencedSet<String> formatInputs = new LinkedHashSet<>();
        formatInputs.add(DEPENDENCIES);
        formatInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(FORMAT,
                new Format(terms, group, verify, aosp, reflow, javadoc, sortImports, removeUnusedImports),
                formatInputs);
    }

    private record Requires(String group) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(group + "/runtime/maven/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Format extends FormatBuildStep {

        private final boolean aosp, reflow, javadoc, sortImports, removeUnusedImports;

        private Format(ProcessBuildStep.Terms terms,
                       String group,
                       boolean verify,
                       boolean aosp,
                       boolean reflow,
                       boolean javadoc,
                       boolean sortImports,
                       boolean removeUnusedImports) {
            super("google-java-format", group, verify, terms);
            this.aosp = aosp;
            this.reflow = reflow;
            this.javadoc = javadoc;
            this.sortImports = sortImports;
            this.removeUnusedImports = removeUnusedImports;
        }

        @Override
        protected boolean isFormattable(Path file) {
            return file.toString().endsWith(".java")
                    && !file.getFileName().toString().equals("module-info.java");
        }

        @Override
        protected List<String> command(List<String> jars, Path config, List<String> files, boolean verify) {
            List<String> commands = new ArrayList<>(List.of(
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                    "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                    "-cp", String.join(File.pathSeparator, jars),
                    "com.google.googlejavaformat.java.Main"));
            if (aosp) {
                commands.add("--aosp");
            }
            if (!reflow) {
                commands.add("--skip-reflowing-long-strings");
            }
            if (!javadoc) {
                commands.add("--skip-javadoc-formatting");
            }
            if (!sortImports) {
                commands.add("--skip-sorting-imports");
            }
            if (!removeUnusedImports) {
                commands.add("--skip-removing-unused-imports");
            }
            if (verify) {
                commands.add("--dry-run");
                commands.add("--set-exit-if-changed");
            } else {
                commands.add("--replace");
            }
            commands.addAll(files);
            return commands;
        }
    }
}
