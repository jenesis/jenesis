package build.jenesis.project;

import module java.base;
import module java.xml;
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
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JaCoCoModule implements BuildExecutorModule {

    public static final String REPORT = "report";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final boolean classes;
    private final SequencedMap<Counter, Double> minimum;
    private final SequencedMap<Counter, Long> maximumMissed;
    private final ProcessBuildStep.Terms terms;

    public JaCoCoModule(Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "jacoco",
             false,
             Collections.emptyNavigableMap(),
             Collections.emptyNavigableMap(),
             new ProcessBuildStep.Terms());
    }

    public static JaCoCoModule ofEnvironment(Environment environment,
                                             Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers) {
        return new JaCoCoModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "jacoco",
                false,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                ProcessBuildStep.Terms.ofEnvironment(environment, "jacoco"));
    }

    private JaCoCoModule(Dependencies dependencies,
                         Pinning pinning,
                         String tool,
                         boolean classes,
                         SequencedMap<Counter, Double> minimum,
                         SequencedMap<Counter, Long> maximumMissed,
                         ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.classes = classes;
        this.minimum = minimum;
        this.maximumMissed = maximumMissed;
        this.terms = terms;
    }

    public JaCoCoModule pinning(Pinning pinning) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, minimum, maximumMissed, terms);
    }

    public JaCoCoModule tool(String tool) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, minimum, maximumMissed, terms);
    }

    public JaCoCoModule classes(boolean classes) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, minimum, maximumMissed, terms);
    }

    public JaCoCoModule minimum(SequencedMap<Counter, Double> minimum) {
        minimum.forEach((counter, ratio) -> {
            if (!(ratio >= 0 && ratio <= 1)) {
                throw new IllegalArgumentException("The minimum covered ratio " + ratio + " of " + counter.label()
                        + " is no ratio, expected a number from 0 to 1, such as 0.8");
            }
        });
        return new JaCoCoModule(dependencies, pinning, tool, classes, Collections.unmodifiableSequencedMap(
                new LinkedHashMap<>(minimum)), maximumMissed, terms);
    }

    public JaCoCoModule minimum(Counter counter, double ratio) {
        SequencedMap<Counter, Double> added = new LinkedHashMap<>(minimum);
        added.put(counter, ratio);
        return minimum(added);
    }

    public JaCoCoModule maximumMissed(SequencedMap<Counter, Long> maximumMissed) {
        maximumMissed.forEach((counter, missed) -> {
            if (missed < 0) {
                throw new IllegalArgumentException("The maximum of " + missed + " missed " + counter.label()
                        + " is no count, expected a whole number of at least 0");
            }
        });
        return new JaCoCoModule(dependencies, pinning, tool, classes, minimum, Collections.unmodifiableSequencedMap(
                new LinkedHashMap<>(maximumMissed)), terms);
    }

    public JaCoCoModule maximumMissed(Counter counter, long missed) {
        SequencedMap<Counter, Long> added = new LinkedHashMap<>(maximumMissed);
        added.put(counter, missed);
        return maximumMissed(added);
    }

    public JaCoCoModule printing(BiConsumer<Boolean, String> printing) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, minimum, maximumMissed, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(tool), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> reportInputs = new LinkedHashSet<>();
        reportInputs.add(DEPENDENCIES);
        reportInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(REPORT, new Report(terms, tool, classes, new LinkedHashMap<>(minimum), new LinkedHashMap<>(maximumMissed)), reportInputs);
    }

    private record Requires(String tool) implements BuildStep {

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
            requires.setProperty(tool + "/runtime/maven/org.jacoco/org.jacoco.cli/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Report extends ProcessBuildStep {

        private final String tool;
        private final boolean classes;
        private final LinkedHashMap<Counter, Double> minimum;
        private final LinkedHashMap<Counter, Long> maximumMissed;

        private Report(ProcessBuildStep.Terms terms,
                       String tool,
                       boolean classes,
                       LinkedHashMap<Counter, Double> minimum,
                       LinkedHashMap<Counter, Long> maximumMissed) {
            super("jacoco", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.classes = classes;
            this.minimum = minimum;
            this.maximumMissed = maximumMissed;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            if (code != 0 || minimum.isEmpty() && maximumMissed.isEmpty()) {
                return code == 0;
            }
            Map<String, long[]> totals = new HashMap<>();
            Path xml = context.next().resolve(BuildStep.REPORTS + "jacoco").resolve("jacoco.xml");
            if (!parsed(xml, new DefaultHandler() {

                private int depth;

                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
                    if (++depth == 2 && qName.equals("counter")) {
                        totals.put(attributes.getValue("type").toLowerCase(Locale.ROOT), new long[]{
                                Long.parseLong(attributes.getValue("missed")),
                                Long.parseLong(attributes.getValue("covered"))});
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    depth--;
                }
            })) {
                throw new IllegalStateException("The JaCoCo report " + xml + " is not well-formed XML, so the coverage"
                        + " limits cannot be checked");
            }
            List<String> violations = new ArrayList<>();
            minimum.forEach((counter, limit) -> {
                long[] total = totals.getOrDefault(counter.name().toLowerCase(Locale.ROOT), new long[2]);
                if (total[0] + total[1] > 0) {
                    double ratio = (double) total[1] / (total[0] + total[1]);
                    if (ratio < limit) {
                        violations.add("a covered ratio of " + limit + " of " + counter.label() + " is not met: "
                                + total[1] + " of " + (total[0] + total[1]) + " covered, a ratio of "
                                + String.format(Locale.ROOT, "%.4f", ratio));
                    }
                }
            });
            maximumMissed.forEach((counter, limit) -> {
                long[] total = totals.getOrDefault(counter.name().toLowerCase(Locale.ROOT), new long[2]);
                if (total[0] > limit) {
                    violations.add("at most " + limit + " missed " + counter.label() + " is not met: " + total[0]
                            + " missed");
                }
            });
            if (!violations.isEmpty()) {
                throw new IllegalStateException("The test coverage falls short of its limits, whose report is "
                        + xml.getParent().resolve("index.html") + ":\n  " + String.join("\n  ", violations));
            }
            return true;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), sources = new ArrayList<>();
            SequencedSet<String> covering = new LinkedHashSet<>();
            SequencedMap<String, Path> covered = new LinkedHashMap<>();
            Path data = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                Path exec = argument.folder().resolve("jacoco.exec");
                if (Files.isRegularFile(exec)) {
                    data = exec;
                }
                Path compiled = argument.folder().resolve(BuildStep.CLASSES),
                        source = argument.folder().resolve(BuildStep.SOURCES);
                if (classes && Files.isDirectory(compiled)) {
                    covering.add(compiled.toString());
                }
                if (classes && Files.isDirectory(source)) {
                    sources.add(source.toString());
                }
                Dependencies.internal(argument.folder()).forEach(covered::putIfAbsent);
            }
            for (Path jar : covered.values()) {
                covering.add(jar.toString());
            }
            if (data == null || covering.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No JaCoCo CLI jars resolved upstream of the JaCoCo report step");
            }
            Path report = Files.createDirectories(context.next().resolve(BuildStep.REPORTS + "jacoco"));
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "org.jacoco.cli.internal.Main", "report", data.toString()));
            for (String directory : covering) {
                commands.add("--classfiles");
                commands.add(directory);
            }
            for (String directory : sources) {
                commands.add("--sourcefiles");
                commands.add(directory);
            }
            commands.add("--html");
            commands.add(report.toString());
            commands.add("--xml");
            commands.add(report.resolve("jacoco.xml").toString());
            return CompletableFuture.completedStage(commands);
        }
    }

    public enum Counter {

        INSTRUCTION, BRANCH, LINE, COMPLEXITY, METHOD, CLASS;

        private String label() {
            return switch (this) {
                case INSTRUCTION -> "instructions";
                case BRANCH -> "branches";
                case LINE -> "lines";
                case COMPLEXITY -> "complexity";
                case METHOD -> "methods";
                case CLASS -> "classes";
            };
        }
    }
}
