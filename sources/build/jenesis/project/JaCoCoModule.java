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
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies",
            MINIMUM = "minimum.", MAXIMUM_MISSED = "maximum.missed.";
    private static final List<String> COUNTERS = List.of("instruction", "branch", "line", "complexity", "method", "class");

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final boolean classes;
    private final SequencedMap<String, String> limits;
    private final ProcessBuildStep.Terms terms;

    public JaCoCoModule(Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "jacoco",
             false,
             new LinkedHashMap<>(),
             new ProcessBuildStep.Terms());
    }

    public static JaCoCoModule ofEnvironment(Environment environment,
                                             Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers) {
        return new JaCoCoModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "jacoco",
                false,
                new LinkedHashMap<>(),
                ProcessBuildStep.Terms.ofEnvironment(environment, "jacoco"));
    }

    private JaCoCoModule(Dependencies dependencies,
                         Pinning pinning,
                         String tool,
                         boolean classes,
                         SequencedMap<String, String> limits,
                         ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.classes = classes;
        this.limits = limits;
        this.terms = terms;
    }

    public JaCoCoModule pinning(Pinning pinning) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, limits, terms);
    }

    public JaCoCoModule tool(String tool) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, limits, terms);
    }

    public JaCoCoModule classes(boolean classes) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, limits, terms);
    }

    public JaCoCoModule limits(SequencedMap<String, String> limits) {
        SequencedMap<String, String> validated = new LinkedHashMap<>();
        limits.forEach((key, value) -> {
            String counter = key.startsWith(MINIMUM) ? key.substring(MINIMUM.length())
                    : key.startsWith(MAXIMUM_MISSED) ? key.substring(MAXIMUM_MISSED.length())
                    : null;
            if (counter == null || !COUNTERS.contains(counter)) {
                throw new IllegalArgumentException("Unknown key " + key + " in jacoco.properties, expected "
                        + MINIMUM + "<counter> or " + MAXIMUM_MISSED + "<counter> for a counter of " + COUNTERS);
            }
            try {
                if (key.startsWith(MINIMUM)) {
                    double ratio = Double.parseDouble(value.trim());
                    if (!(ratio >= 0 && ratio <= 1)) {
                        throw new NumberFormatException(value);
                    }
                } else if (Long.parseLong(value.trim()) < 0) {
                    throw new NumberFormatException(value);
                }
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("The value " + value + " of " + key + " in jacoco.properties is "
                        + (key.startsWith(MINIMUM)
                                ? "no covered ratio, expected a number from 0 to 1, such as 0.8"
                                : "no count of missed items, expected a whole number of at least 0"));
            }
            validated.put(key, value.trim());
        });
        return new JaCoCoModule(dependencies, pinning, tool, classes, validated, terms);
    }

    public JaCoCoModule printing(BiConsumer<Boolean, String> printing) {
        return new JaCoCoModule(dependencies, pinning, tool, classes, limits, terms.printing(printing));
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
        buildExecutor.addStep(REPORT, new Report(terms, tool, classes, new LinkedHashMap<>(limits)), reportInputs);
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
        private final LinkedHashMap<String, String> limits;

        private Report(ProcessBuildStep.Terms terms, String tool, boolean classes, LinkedHashMap<String, String> limits) {
            super("jacoco", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.classes = classes;
            this.limits = limits;
        }

        @Override
        public boolean shouldReproduce() {
            return false;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            if (code != 0 || limits.isEmpty()) {
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
                throw new IllegalStateException("The JaCoCo report " + xml + " is not well-formed XML, so the limits of"
                        + " jacoco.properties cannot be checked");
            }
            List<String> violations = new ArrayList<>();
            limits.forEach((key, value) -> {
                boolean minimum = key.startsWith(MINIMUM);
                long[] total = totals.getOrDefault(key.substring(minimum ? MINIMUM.length() : MAXIMUM_MISSED.length()),
                        new long[2]);
                if (minimum && total[0] + total[1] > 0) {
                    double ratio = (double) total[1] / (total[0] + total[1]);
                    if (ratio < Double.parseDouble(value)) {
                        violations.add(key + "=" + value + " is not met: " + total[1] + " of " + (total[0] + total[1])
                                + " covered, a ratio of " + String.format(Locale.ROOT, "%.4f", ratio));
                    }
                } else if (!minimum && total[0] > Long.parseLong(value)) {
                    violations.add(key + "=" + value + " is not met: " + total[0] + " missed");
                }
            });
            if (!violations.isEmpty()) {
                throw new IllegalStateException("The test coverage falls short of jacoco.properties, whose report is "
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
}
