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
import build.jenesis.step.Findings;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class ForbiddenApisModule implements BuildExecutorModule {

    public static final String CHECK = "check", CONFIGURATION = "forbiddenapis.properties";
    public static final String BUNDLED = "bundled", SIGNATURE_FILES = "signatures", SUPPRESS = "suppress",
            INCLUDES = "includes", EXCLUDES = "excludes", WARN = "warn",
            ALLOW_MISSING_CLASSES = "allowmissingclasses",
            IGNORE_SIGNATURES_OF_MISSING_CLASSES = "ignoresignaturesofmissingclasses";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String SETTING = "validator.forbiddenapis";
    private static final String REPORT = BuildStep.REPORTS + "forbiddenapis/forbiddenapis.txt";
    private static final String MAVEN_GROUP = "de.thetaphi", MAVEN_ARTIFACT = "forbiddenapis";
    private static final Set<String> KEYS = Set.of(BUNDLED, SIGNATURE_FILES, SUPPRESS, INCLUDES, EXCLUDES, WARN,
            ALLOW_MISSING_CLASSES, IGNORE_SIGNATURES_OF_MISSING_CLASSES);
    private static final Map<String, String> OPTIONS = Map.of(SUPPRESS, "--suppressannotation",
            INCLUDES, "--includes",
            EXCLUDES, "--excludes",
            WARN, "--signatureswithseveritywarn");
    private static final Set<String> VERSIONED = Set.of("jdk-unsafe", "jdk-deprecated", "jdk-internal");

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final boolean strict;
    private final ProcessBuildStep.Terms terms;

    public ForbiddenApisModule(Map<String, Repository> repositories,
                               Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "forbiddenapis",
             "main",
             false,
             new ProcessBuildStep.Terms());
    }

    public static ForbiddenApisModule ofEnvironment(Environment environment,
                                                    Map<String, Repository> repositories,
                                                    Map<String, Resolver> resolvers) {
        return new ForbiddenApisModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "forbiddenapis",
                "main",
                Enforcement.ofEnvironment(environment, SETTING) == Enforcement.STRICT,
                ProcessBuildStep.Terms.ofEnvironment(environment, "forbiddenapis"));
    }

    private ForbiddenApisModule(Dependencies dependencies,
                                Pinning pinning,
                                String tool,
                                String group,
                                boolean strict,
                                ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.strict = strict;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, CONFIGURATION);
    }

    public static SequencedSet<Path> siblings(Path configurationFile) throws IOException {
        SequencedSet<Path> siblings = new LinkedHashSet<>();
        if (configurationFile == null) {
            return siblings;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(configurationFile);
        for (String key : properties.stringPropertyNames()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException(configurationFile + " names " + key + ", but takes "
                        + String.join(", ", new TreeSet<>(KEYS)));
            }
        }
        properties.flag(ALLOW_MISSING_CLASSES, false);
        properties.flag(IGNORE_SIGNATURES_OF_MISSING_CLASSES, false);
        for (String file : Objects.requireNonNullElse(properties.entries(SIGNATURE_FILES), List.<String>of())) {
            Path sibling = Path.of(file).normalize();
            if (sibling.isAbsolute() || sibling.startsWith("..")) {
                throw new IllegalArgumentException(configurationFile + " names the signatures " + file
                        + ", which is not beside or below it - place the file in the folder of " + CONFIGURATION);
            }
            if (!Files.isRegularFile(configurationFile.resolveSibling(sibling))) {
                throw new IllegalArgumentException(configurationFile + " names the signatures " + file
                        + ", which does not exist beside it");
            }
            siblings.add(sibling);
        }
        if (siblings.isEmpty() && properties.value(BUNDLED) == null) {
            throw new IllegalArgumentException(configurationFile + " names no signatures - set " + BUNDLED
                    + "=<bundled signatures, as jdk-unsafe,jdk-deprecated> or " + SIGNATURE_FILES
                    + "=<files of signatures beside it>");
        }
        return siblings;
    }

    public ForbiddenApisModule pinning(Pinning pinning) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, terms);
    }

    public ForbiddenApisModule tool(String tool) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, terms);
    }

    public ForbiddenApisModule group(String group) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, terms);
    }

    public ForbiddenApisModule strict(boolean strict) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, terms);
    }

    public ForbiddenApisModule printing(BiConsumer<Boolean, String> printing) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, terms.printing(printing));
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
        SequencedSet<String> checkInputs = new LinkedHashSet<>();
        checkInputs.add(DEPENDENCIES);
        checkInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(CHECK, new Check(terms, tool, group, strict), checkInputs);
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
            requires.setProperty(tool + "/runtime/maven/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Check extends ProcessBuildStep {

        private final String tool;
        private final String group;
        private final boolean strict;

        private Check(ProcessBuildStep.Terms terms,
                      String tool,
                      String group,
                      boolean strict) {
            super("forbiddenapis", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.group = group;
            this.strict = strict;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            if (code != 0 && code != 1) {
                return false;
            }
            List<String> findings = new ArrayList<>();
            Path error = context.supplement().resolve("error");
            if (Files.isRegularFile(error)) {
                for (String line : Files.readAllLines(error)) {
                    if (line.startsWith("ERROR: Forbidden ")) {
                        findings.add(line.substring("ERROR: ".length()));
                    } else if (line.startsWith("ERROR:   in ") && !findings.isEmpty()) {
                        findings.set(findings.size() - 1, findings.getLast() + " " + line.substring("ERROR:   ".length()));
                    }
                }
            }
            Path report = context.next().resolve(REPORT);
            Files.createDirectories(report.getParent());
            Files.write(report, findings);
            return new Findings("forbiddenapis", report, findings.size())
                    .acceptable(code, context, true, strict, SETTING, terms.reporting());
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), classes = new ArrayList<>(), auxiliary = new ArrayList<>();
            Path config = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    auxiliary.add(jar.toString());
                }
                Path candidate = argument.folder().resolve(CONFIGURATION);
                if (Files.isRegularFile(candidate)) {
                    config = candidate;
                }
                Path compiled = argument.folder().resolve(BuildStep.CLASSES);
                if (Files.isDirectory(compiled)) {
                    classes.add(compiled.toString());
                }
            }
            if (classes.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No forbiddenapis jars resolved upstream of the forbiddenapis step");
            }
            if (config == null) {
                throw new IllegalStateException("No " + CONFIGURATION + " bound upstream of the forbiddenapis step");
            }
            SequencedProperties configuration = SequencedProperties.ofFiles(config);
            String release = null;
            for (String folder : classes) {
                try (Stream<Path> files = Files.walk(Path.of(folder))) {
                    Path file = files.filter(candidate -> candidate.toString().endsWith(".class"))
                            .filter(candidate -> !Path.of(folder).relativize(candidate).startsWith("META-INF"))
                            .findFirst()
                            .orElse(null);
                    if (file != null) {
                        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
                            input.readInt();
                            input.readUnsignedShort();
                            int major = input.readUnsignedShort() - 44;
                            release = major <= 8 ? "1." + major : Integer.toString(major);
                        }
                        break;
                    }
                }
            }
            if (release == null) {
                return CompletableFuture.completedStage(null);
            }
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "de.thetaphi.forbiddenapis.cli.CliMain"));
            for (String bundled : Objects.requireNonNullElse(configuration.entries(BUNDLED), List.<String>of())) {
                commands.add("--bundledsignatures");
                commands.add(VERSIONED.contains(bundled) ? bundled + "-" + release : bundled);
            }
            for (String file : Objects.requireNonNullElse(configuration.entries(SIGNATURE_FILES), List.<String>of())) {
                commands.add("--signaturesfile");
                commands.add(config.resolveSibling(file).toString());
            }
            for (String key : List.of(SUPPRESS, INCLUDES, EXCLUDES, WARN)) {
                for (String value : Objects.requireNonNullElse(configuration.entries(key), List.<String>of())) {
                    commands.add(OPTIONS.get(key));
                    commands.add(value);
                }
            }
            if (configuration.flag(ALLOW_MISSING_CLASSES, false)) {
                commands.add("--allowmissingclasses");
            }
            if (configuration.flag(IGNORE_SIGNATURES_OF_MISSING_CLASSES, false)) {
                commands.add("--ignoresignaturesofmissingclasses");
            }
            if (!auxiliary.isEmpty()) {
                commands.add("--classpath");
                commands.add(String.join(File.pathSeparator, auxiliary));
            }
            for (String folder : classes) {
                commands.add("--dir");
                commands.add(folder);
            }
            return CompletableFuture.completedStage(commands);
        }
    }
}
