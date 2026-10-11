package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Findings;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JDepsModule implements BuildExecutorModule {

    public static final String CHECK = "check", CONFIGURATION = "jdeps.properties", ACCEPT = "accept";
    private static final String SETTING = "validator.jdeps", REPORT = BuildStep.REPORTS + "jdeps/jdeps.txt";
    private static final Pattern FINDING = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(JDK (?:removed )?internal API.*?)\\s*$"),
            REPLACEMENT = Pattern.compile("^(\\S+)\\s{2,}(\\S.*?)\\s*$");

    private final ProcessHandler.Factory factory;
    private final String group;
    private final boolean strict;
    private final ProcessBuildStep.Terms terms;

    public JDepsModule() {
        this(ProcessHandler.Factory.of(), "main", false, new ProcessBuildStep.Terms());
    }

    public static JDepsModule ofEnvironment(Environment environment) {
        return new JDepsModule(ProcessHandler.Factory.ofEnvironment(environment),
                "main",
                Enforcement.ofEnvironment(environment, SETTING) == Enforcement.STRICT,
                ProcessBuildStep.Terms.ofEnvironment(environment, "jdeps"));
    }

    private JDepsModule(ProcessHandler.Factory factory, String group, boolean strict, ProcessBuildStep.Terms terms) {
        this.factory = factory;
        this.group = group;
        this.strict = strict;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, CONFIGURATION);
    }

    public JDepsModule factory(ProcessHandler.Factory factory) {
        return new JDepsModule(factory, group, strict, terms);
    }

    public JDepsModule group(String group) {
        return new JDepsModule(factory, group, strict, terms);
    }

    public JDepsModule strict(boolean strict) {
        return new JDepsModule(factory, group, strict, terms);
    }

    public JDepsModule printing(BiConsumer<Boolean, String> printing) {
        return new JDepsModule(factory, group, strict, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(CHECK, new Check(factory.apply("jdeps", "bin/jdeps"), terms, group, strict),
                inherited.sequencedKeySet());
    }

    private static class Check extends ProcessBuildStep {

        private final String group;
        private final boolean strict;

        private Check(Function<List<String>, ? extends ProcessHandler> factory,
                      ProcessBuildStep.Terms terms,
                      String group,
                      boolean strict) {
            super("jdeps", factory, terms);
            this.group = group;
            this.strict = strict;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            if (code != 0) {
                return false;
            }
            List<String> accepted = List.of();
            for (BuildStepArgument argument : arguments.values()) {
                Path file = argument.folder().resolve(CONFIGURATION);
                if (!argument.removed() && Files.isRegularFile(file)) {
                    accepted = Objects.requireNonNullElse(SequencedProperties.ofFiles(file).entries(ACCEPT), List.of());
                }
            }
            List<String> lines = Files.readAllLines(context.supplement().resolve("output"));
            Map<String, String> replacements = new HashMap<>();
            boolean table = false;
            for (String line : lines) {
                Matcher matcher = REPLACEMENT.matcher(line);
                if (line.startsWith("JDK Internal API")) {
                    table = true;
                } else if (table && matcher.matches() && !line.startsWith("-")) {
                    replacements.put(matcher.group(1), matcher.group(2));
                }
            }
            SequencedSet<String> findings = new LinkedHashSet<>();
            for (String line : lines) {
                Matcher matcher = FINDING.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                String target = matcher.group(2);
                if (accepted.stream().anyMatch(prefix -> target.equals(prefix)
                        || target.startsWith(prefix + ".")
                        || target.startsWith(prefix + "$"))) {
                    continue;
                }
                String replacement = replacements.get(target);
                findings.add(matcher.group(1) + " -> " + target + ": " + matcher.group(3)
                        + (replacement == null ? "" : ", instead: " + replacement));
            }
            Path report = context.next().resolve(REPORT);
            Files.createDirectories(report.getParent());
            Files.write(report, findings);
            return new Findings("jdeps", report, findings.size())
                    .acceptable(code, context, false, strict, SETTING, terms.reporting());
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> classes = new ArrayList<>();
            SequencedSet<Path> jars = new LinkedHashSet<>();
            boolean modular = false;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(CONFIGURATION);
                if (Files.isRegularFile(file)) {
                    for (String key : SequencedProperties.ofFiles(file).stringPropertyNames()) {
                        if (!key.equals(ACCEPT)) {
                            throw new IllegalArgumentException(CONFIGURATION + " names " + key + ", but takes " + ACCEPT
                                    + "=<the JDK internal classes or packages the classes may use> alone");
                        }
                    }
                }
                jars.addAll(Dependencies.select(argument.folder(), group, "compile"));
                Path compiled = argument.folder().resolve(BuildStep.CLASSES);
                if (Files.isDirectory(compiled)) {
                    classes.add(compiled.toString());
                    modular |= Files.isRegularFile(compiled.resolve("module-info.class"));
                }
            }
            if (classes.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
            for (Path jar : jars) {
                (modular && PathPlacement.INFERRED.test(jar) ? modulePath : classPath).add(jar.toString());
            }
            List<String> commands = new ArrayList<>(List.of("--jdk-internals",
                    "--multi-release", Integer.toString(Runtime.version().feature())));
            if (!modulePath.isEmpty()) {
                commands.add("--module-path");
                commands.add(String.join(File.pathSeparator, modulePath));
            }
            if (!classPath.isEmpty()) {
                commands.add("--class-path");
                commands.add(String.join(File.pathSeparator, classPath));
            }
            commands.addAll(classes);
            return CompletableFuture.completedStage(commands);
        }
    }
}
