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
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.FormatBuildStep;
import build.jenesis.step.ProcessBuildStep;

public class EclipseJavaFormatModule implements BuildExecutorModule {

    public static final String FORMAT = "format", PROFILE = "eclipse-formatter.xml";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies", CONFIGURATION = "profile";
    private static final String MAVEN_GROUP = "org.eclipse.jdt", MAVEN_ARTIFACT = "org.eclipse.jdt.core";
    private static final String LAUNCHER = "EclipseFormatter.java", SOURCE = """
            import module java.xml;
            import org.eclipse.jdt.core.JavaCore;
            import org.eclipse.jdt.core.ToolFactory;
            import org.eclipse.jdt.core.formatter.CodeFormatter;
            import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
            import org.eclipse.jface.text.Document;
            import org.eclipse.text.edits.TextEdit;

            void main(String[] arguments) throws Exception {
                Map<String, String> options = new HashMap<>(DefaultCodeFormatterConstants.getEclipseDefaultSettings());
                if (!arguments[1].isEmpty()) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    NodeList profiles = factory.newDocumentBuilder()
                            .parse(Path.of(arguments[1]).toFile())
                            .getElementsByTagName("profile");
                    if (profiles.getLength() != 1) {
                        throw new IllegalArgumentException("The Eclipse formatter profile holds " + profiles.getLength()
                                + " profiles rather than one - export only the profile to format with");
                    }
                    NodeList settings = ((Element) profiles.item(0)).getElementsByTagName("setting");
                    for (int index = 0; index < settings.getLength(); index++) {
                        Element setting = (Element) settings.item(index);
                        options.put(setting.getAttribute("id"), setting.getAttribute("value"));
                    }
                }
                String release = JavaCore.latestSupportedJavaVersion();
                options.put(JavaCore.COMPILER_SOURCE, release);
                options.put(JavaCore.COMPILER_COMPLIANCE, release);
                options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, release);
                CodeFormatter formatter = ToolFactory.createCodeFormatter(options, ToolFactory.M_FORMAT_EXISTING);
                boolean verify = arguments[0].equals("verify");
                List<String> unformatted = new ArrayList<>();
                for (String name : List.of(arguments).subList(2, arguments.length)) {
                    Path file = Path.of(name);
                    String source = Files.readString(file);
                    TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS,
                            source,
                            0,
                            source.length(),
                            0,
                            source.contains("\\r\\n") ? "\\r\\n" : "\\n");
                    if (edit == null) {
                        throw new IllegalStateException("The Eclipse formatter cannot parse " + name);
                    }
                    Document document = new Document(source);
                    edit.apply(document);
                    String formatted = document.get();
                    if (!formatted.equals(source)) {
                        unformatted.add(name);
                        if (!verify) {
                            Files.writeString(file, formatted);
                        }
                    }
                }
                if (verify && !unformatted.isEmpty()) {
                    unformatted.forEach(name -> System.out.println(name + " is not formatted as the profile says"));
                    System.exit(1);
                }
            }
            """;

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String group;
    private final boolean verify;
    private final Path profile;
    private final ProcessBuildStep.Terms terms;

    public EclipseJavaFormatModule(Map<String, Repository> repositories,
                                   Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "eclipse-java-format",
             false,
             null,
             new ProcessBuildStep.Terms());
    }

    public static EclipseJavaFormatModule ofEnvironment(Environment environment,
                                                        Map<String, Repository> repositories,
                                                        Map<String, Resolver> resolvers) {
        return new EclipseJavaFormatModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "eclipse-java-format",
                false,
                null,
                ProcessBuildStep.Terms.ofEnvironment(environment, "eclipse-java-format"));
    }

    private EclipseJavaFormatModule(Dependencies dependencies,
                                    Pinning pinning,
                                    String group,
                                    boolean verify,
                                    Path profile,
                                    ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.group = group;
        this.verify = verify;
        this.profile = profile;
        this.terms = terms;
    }

    public EclipseJavaFormatModule pinning(Pinning pinning) {
        return new EclipseJavaFormatModule(dependencies, pinning, group, verify, profile, terms);
    }

    public EclipseJavaFormatModule group(String group) {
        return new EclipseJavaFormatModule(dependencies, pinning, group, verify, profile, terms);
    }

    public EclipseJavaFormatModule verify(boolean verify) {
        return new EclipseJavaFormatModule(dependencies, pinning, group, verify, profile, terms);
    }

    public EclipseJavaFormatModule profile(Path profile) {
        return new EclipseJavaFormatModule(dependencies, pinning, group, verify, profile, terms);
    }

    public EclipseJavaFormatModule printing(BiConsumer<Boolean, String> printing) {
        return new EclipseJavaFormatModule(dependencies, pinning, group, verify, profile, terms.printing(printing));
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
        if (profile != null) {
            buildExecutor.addSource(CONFIGURATION, new Bind(Map.of(Path.of(""), Path.of(PROFILE))), profile);
            formatInputs.add(CONFIGURATION);
        }
        formatInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(FORMAT, new Format(terms, group, verify), formatInputs);
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

        private Format(ProcessBuildStep.Terms terms, String group, boolean verify) {
            super("eclipse-java-format", group, verify, terms);
        }

        @Override
        protected boolean isFormattable(Path file) {
            return file.toString().endsWith(".java")
                    && !file.getFileName().toString().equals("module-info.java");
        }

        @Override
        protected Path config(Path folder) {
            Path candidate = folder.resolve(PROFILE);
            return Files.isRegularFile(candidate) ? candidate : null;
        }

        @Override
        protected List<String> command(BuildStepContext context,
                                       List<String> jars,
                                       Path config,
                                       List<String> files,
                                       boolean verify) throws IOException {
            Path launcher = Files.writeString(context.supplement().resolve(LAUNCHER), SOURCE);
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    launcher.toString(),
                    verify ? "verify" : "rewrite",
                    config == null ? "" : config.toString()));
            commands.addAll(files);
            return commands;
        }
    }
}
