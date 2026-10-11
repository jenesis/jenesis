package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Findings;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JDeprScanModule implements BuildExecutorModule {

    public static final String CHECK = "check", CONFIGURATION = "jdeprscan.properties",
            REMOVAL = "removal", RELEASE = "release";
    private static final String SETTING = "validator.jdeprscan", REPORT = BuildStep.REPORTS + "jdeprscan/jdeprscan.txt";

    private final String group;
    private final boolean strict;
    private final ProcessBuildStep.Terms terms;

    public JDeprScanModule() {
        this("main", false, new ProcessBuildStep.Terms());
    }

    public static JDeprScanModule ofEnvironment(Environment environment) {
        return new JDeprScanModule("main",
                Enforcement.ofEnvironment(environment, SETTING) == Enforcement.STRICT,
                ProcessBuildStep.Terms.ofEnvironment(environment, "jdeprscan"));
    }

    private JDeprScanModule(String group, boolean strict, ProcessBuildStep.Terms terms) {
        this.group = group;
        this.strict = strict;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, CONFIGURATION);
    }

    public JDeprScanModule group(String group) {
        return new JDeprScanModule(group, strict, terms);
    }

    public JDeprScanModule strict(boolean strict) {
        return new JDeprScanModule(group, strict, terms);
    }

    public JDeprScanModule printing(BiConsumer<Boolean, String> printing) {
        return new JDeprScanModule(group, strict, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(CHECK, new Check(terms, group, strict), inherited.sequencedKeySet());
    }

    private static class Check extends ProcessBuildStep {

        private final String group;
        private final boolean strict;

        private Check(ProcessBuildStep.Terms terms, String group, boolean strict) {
            super("jdeprscan", ProcessHandler.OfProcess.ofJavaHome("bin/jdeprscan"), terms);
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
            List<String> findings = new ArrayList<>(), unresolved = new ArrayList<>();
            for (String line : Files.readAllLines(context.supplement().resolve("output"))) {
                if (line.startsWith("class ") && line.contains(" deprecated ")) {
                    findings.add(line.substring("class ".length()));
                } else if (line.startsWith("error: ")) {
                    unresolved.add(line.substring("error: ".length()));
                }
            }
            if (!unresolved.isEmpty()) {
                throw new IllegalStateException("jdeprscan cannot scan every class, as their dependencies are not"
                        + " all on the module's compile class path:\n  " + String.join("\n  ", unresolved));
            }
            Path report = context.next().resolve(REPORT);
            Files.createDirectories(report.getParent());
            Files.write(report, findings);
            return new Findings("jdeprscan", report, findings.size())
                    .acceptable(code, context, false, strict, SETTING, terms.reporting());
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> classes = new ArrayList<>();
            SequencedSet<String> jars = new LinkedHashSet<>();
            SequencedProperties configuration = new SequencedProperties();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(CONFIGURATION);
                if (Files.isRegularFile(file)) {
                    configuration = SequencedProperties.ofFiles(file);
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    jars.add(jar.toString());
                }
                Path compiled = argument.folder().resolve(BuildStep.CLASSES);
                if (Files.isDirectory(compiled)) {
                    classes.add(compiled.toString());
                }
            }
            for (String key : configuration.stringPropertyNames()) {
                if (!key.equals(REMOVAL) && !key.equals(RELEASE)) {
                    throw new IllegalArgumentException(CONFIGURATION + " names " + key + ", but takes " + REMOVAL
                            + "=false to report every deprecated API rather than those deprecated for removal, and "
                            + RELEASE + "=<the Java release whose deprecations to check>");
                }
            }
            if (classes.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            List<String> commands = new ArrayList<>();
            if (configuration.flag(REMOVAL, true)) {
                commands.add("--for-removal");
            }
            String release = configuration.value(RELEASE);
            if (release != null) {
                if (!release.matches("[0-9]+")) {
                    throw new IllegalArgumentException(CONFIGURATION + " names the release " + release
                            + ", but takes a Java release as a number, as 21");
                }
                commands.add("--release");
                commands.add(release);
            }
            if (!jars.isEmpty()) {
                commands.add("--class-path");
                commands.add(String.join(File.pathSeparator, jars));
            }
            commands.addAll(classes);
            return CompletableFuture.completedStage(commands);
        }
    }
}
