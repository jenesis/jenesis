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

    public static final String CHECK = "check";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String SETTING = "validator.forbiddenapis";
    private static final String REPORT = BuildStep.REPORTS + "forbiddenapis/forbiddenapis.txt";
    private static final String MAVEN_GROUP = "de.thetaphi", MAVEN_ARTIFACT = "forbiddenapis";
    private static final Set<String> VERSIONED = Set.of("jdk-unsafe", "jdk-deprecated", "jdk-internal");

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String group;
    private final boolean strict;
    private final List<String> bundled;
    private final List<Path> signatures;
    private final List<String> suppress;
    private final List<String> includes;
    private final List<String> excludes;
    private final List<String> warn;
    private final boolean allowMissingClasses;
    private final boolean ignoreSignaturesOfMissingClasses;
    private final ProcessBuildStep.Terms terms;

    public ForbiddenApisModule(Map<String, Repository> repositories,
                               Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "forbiddenapis",
             "main",
             false,
             List.of(),
             List.of(),
             List.of(),
             List.of(),
             List.of(),
             List.of(),
             false,
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
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                ProcessBuildStep.Terms.ofEnvironment(environment, "forbiddenapis"));
    }

    private ForbiddenApisModule(Dependencies dependencies,
                                Pinning pinning,
                                String tool,
                                String group,
                                boolean strict,
                                List<String> bundled,
                                List<Path> signatures,
                                List<String> suppress,
                                List<String> includes,
                                List<String> excludes,
                                List<String> warn,
                                boolean allowMissingClasses,
                                boolean ignoreSignaturesOfMissingClasses,
                                ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.group = group;
        this.strict = strict;
        this.bundled = bundled;
        this.signatures = signatures;
        this.suppress = suppress;
        this.includes = includes;
        this.excludes = excludes;
        this.warn = warn;
        this.allowMissingClasses = allowMissingClasses;
        this.ignoreSignaturesOfMissingClasses = ignoreSignaturesOfMissingClasses;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, "forbiddenapis.properties");
    }

    public ForbiddenApisModule pinning(Pinning pinning) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule tool(String tool) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule group(String group) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule strict(boolean strict) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule bundled(List<String> bundled) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, List.copyOf(bundled), signatures,
                suppress, includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule signatures(List<Path> signatures) {
        for (Path signature : signatures) {
            Path normalized = signature.normalize();
            if (signature.isAbsolute() || normalized.startsWith("..") || normalized.toString().isEmpty()) {
                throw new IllegalArgumentException("The signatures file " + signature + " is no path below the folders"
                        + " the check reads - name it relative to the folder it is bound into");
            }
        }
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, List.copyOf(signatures),
                suppress, includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule suppress(List<String> suppress) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures,
                List.copyOf(suppress), includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses,
                terms);
    }

    public ForbiddenApisModule includes(List<String> includes) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                List.copyOf(includes), excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule excludes(List<String> excludes) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, List.copyOf(excludes), warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule warn(List<String> warn) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, List.copyOf(warn), allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule allowMissingClasses(boolean allowMissingClasses) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule ignoreSignaturesOfMissingClasses(boolean ignoreSignaturesOfMissingClasses) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses, terms);
    }

    public ForbiddenApisModule printing(BiConsumer<Boolean, String> printing) {
        return new ForbiddenApisModule(dependencies, pinning, tool, group, strict, bundled, signatures, suppress,
                includes, excludes, warn, allowMissingClasses, ignoreSignaturesOfMissingClasses,
                terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (bundled.isEmpty() && signatures.isEmpty()) {
            throw new IllegalStateException("forbiddenapis is given no signatures to check against - name bundled"
                    + " signatures, as jdk-unsafe, or files of signatures the check is handed");
        }
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
        buildExecutor.addStep(CHECK, new Check(terms,
                tool,
                group,
                strict,
                bundled,
                signatures,
                suppress,
                includes,
                excludes,
                warn,
                allowMissingClasses,
                ignoreSignaturesOfMissingClasses), checkInputs);
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
        private final List<String> bundled;
        private final List<Path> signatures;
        private final List<String> suppress;
        private final List<String> includes;
        private final List<String> excludes;
        private final List<String> warn;
        private final boolean allowMissingClasses;
        private final boolean ignoreSignaturesOfMissingClasses;

        private Check(ProcessBuildStep.Terms terms,
                      String tool,
                      String group,
                      boolean strict,
                      List<String> bundled,
                      List<Path> signatures,
                      List<String> suppress,
                      List<String> includes,
                      List<String> excludes,
                      List<String> warn,
                      boolean allowMissingClasses,
                      boolean ignoreSignaturesOfMissingClasses) {
            super("forbiddenapis", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.group = group;
            this.strict = strict;
            this.bundled = bundled;
            this.signatures = signatures;
            this.suppress = suppress;
            this.includes = includes;
            this.excludes = excludes;
            this.warn = warn;
            this.allowMissingClasses = allowMissingClasses;
            this.ignoreSignaturesOfMissingClasses = ignoreSignaturesOfMissingClasses;
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
            SequencedMap<Path, Path> signed = new LinkedHashMap<>();
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
                for (Path signature : signatures) {
                    Path candidate = argument.folder().resolve(signature);
                    if (Files.isRegularFile(candidate)) {
                        signed.putIfAbsent(signature, candidate);
                    }
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
            for (Path signature : signatures) {
                if (!signed.containsKey(signature)) {
                    throw new IllegalStateException("No signatures file " + signature + " bound upstream of the"
                            + " forbiddenapis step - bind it into a folder the step reads");
                }
            }
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
            for (String name : bundled) {
                commands.add("--bundledsignatures");
                commands.add(VERSIONED.contains(name) ? name + "-" + release : name);
            }
            for (Path file : signed.values()) {
                commands.add("--signaturesfile");
                commands.add(file.toString());
            }
            for (String annotation : suppress) {
                commands.add("--suppressannotation");
                commands.add(annotation);
            }
            for (String pattern : includes) {
                commands.add("--includes");
                commands.add(pattern);
            }
            for (String pattern : excludes) {
                commands.add("--excludes");
                commands.add(pattern);
            }
            for (String signature : warn) {
                commands.add("--signatureswithseveritywarn");
                commands.add(signature);
            }
            if (allowMissingClasses) {
                commands.add("--allowmissingclasses");
            }
            if (ignoreSignaturesOfMissingClasses) {
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
