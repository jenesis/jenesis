package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;

public class InferredDocumentationChainModule implements BuildExecutorModule {

    public static final String JAVADOC = "javadoc", GROOVYDOC = "groovydoc", SCALADOC = "scaladoc", DOKKA = "dokka";
    public static final String DOCUMENT = "document", AGGREGATE = "aggregate";
    private static final String SCAN = "scan", SCAN_FILE = "scan.properties";

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Javadoc javadocStep;
    private final DokkaDocumentationModule dokkaModule;
    private final ScalaDocumentationModule scaladocModule;
    private final GroovyDocumentationModule groovydocModule;
    private final UnaryOperator<Javadoc> javadoc;
    private final UnaryOperator<DokkaDocumentationModule> dokka;
    private final UnaryOperator<ScalaDocumentationModule> scaladoc;
    private final UnaryOperator<GroovyDocumentationModule> groovydoc;

    public InferredDocumentationChainModule(Map<String, Repository> repositories,
                                            Map<String, Resolver> resolvers) {
        this(repositories,
             resolvers,
             null,
             new Javadoc(ProcessHandler.Factory.of()),
             new DokkaDocumentationModule(repositories, resolvers),
             new ScalaDocumentationModule(repositories, resolvers),
             new GroovyDocumentationModule(repositories, resolvers),
             step -> step,
             value -> value,
             value -> value,
             value -> value);
    }

    public static InferredDocumentationChainModule ofEnvironment(Environment environment,
                                                                 Map<String, Repository> repositories,
                                                                 Map<String, Resolver> resolvers) {
        return new InferredDocumentationChainModule(repositories,
                resolvers,
                null,
                Javadoc.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment)),
                DokkaDocumentationModule.ofEnvironment(environment, repositories, resolvers),
                ScalaDocumentationModule.ofEnvironment(environment, repositories, resolvers),
                GroovyDocumentationModule.ofEnvironment(environment, repositories, resolvers),
                step -> step,
                value -> value,
                value -> value,
                value -> value);
    }

    private InferredDocumentationChainModule(Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers,
                                             Pinning pinning,
                                             Javadoc javadocStep,
                                             DokkaDocumentationModule dokkaModule,
                                             ScalaDocumentationModule scaladocModule,
                                             GroovyDocumentationModule groovydocModule,
                                             UnaryOperator<Javadoc> javadoc,
                                             UnaryOperator<DokkaDocumentationModule> dokka,
                                             UnaryOperator<ScalaDocumentationModule> scaladoc,
                                             UnaryOperator<GroovyDocumentationModule> groovydoc) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.javadocStep = javadocStep;
        this.dokkaModule = dokkaModule;
        this.scaladocModule = scaladocModule;
        this.groovydocModule = groovydocModule;
        this.javadoc = javadoc;
        this.dokka = dokka;
        this.scaladoc = scaladoc;
        this.groovydoc = groovydoc;
    }

    public InferredDocumentationChainModule pinning(Pinning pinning) {
        return new InferredDocumentationChainModule(repositories, resolvers, pinning,
                javadocStep, dokkaModule, scaladocModule, groovydocModule,
                javadoc, dokka, scaladoc, groovydoc);
    }

    public InferredDocumentationChainModule javadoc(UnaryOperator<Javadoc> javadoc) {
        return new InferredDocumentationChainModule(repositories, resolvers, pinning,
                javadocStep, dokkaModule, scaladocModule, groovydocModule,
                append(this.javadoc, javadoc), dokka, scaladoc, groovydoc);
    }

    public InferredDocumentationChainModule dokka(UnaryOperator<DokkaDocumentationModule> dokka) {
        return new InferredDocumentationChainModule(repositories, resolvers, pinning,
                javadocStep, dokkaModule, scaladocModule, groovydocModule,
                javadoc, append(this.dokka, dokka), scaladoc, groovydoc);
    }

    public InferredDocumentationChainModule scaladoc(UnaryOperator<ScalaDocumentationModule> scaladoc) {
        return new InferredDocumentationChainModule(repositories, resolvers, pinning,
                javadocStep, dokkaModule, scaladocModule, groovydocModule,
                javadoc, dokka, append(this.scaladoc, scaladoc), groovydoc);
    }

    public InferredDocumentationChainModule groovydoc(UnaryOperator<GroovyDocumentationModule> groovydoc) {
        return new InferredDocumentationChainModule(repositories, resolvers, pinning,
                javadocStep, dokkaModule, scaladocModule, groovydocModule,
                javadoc, dokka, scaladoc, append(this.groovydoc, groovydoc));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(SCAN, new Scan(), inherited.sequencedKeySet());
        SequencedSet<String> documentInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
        documentInputs.add(SCAN);
        buildExecutor.addModule(DOCUMENT,
                new Document(repositories,
                        resolvers,
                        pinning,
                        javadocStep,
                        dokkaModule,
                        scaladocModule,
                        groovydocModule,
                        javadoc,
                        dokka,
                        scaladoc,
                        groovydoc),
                documentInputs);
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(SCAN) ? Optional.empty() : Optional.of(path);
    }

    private static class Scan implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            boolean[] flags = new boolean[4];
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path sources = argument.folder().resolve(BuildStep.SOURCES);
                if (!Files.exists(sources)) {
                    continue;
                }
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".java")) {
                            flags[0] = true;
                        } else if (name.endsWith(".kt")) {
                            flags[1] = true;
                        } else if (name.endsWith(".scala")) {
                            flags[2] = true;
                        } else if (name.endsWith(".groovy")) {
                            flags[3] = true;
                        }
                        return flags[0] && flags[1] && flags[2] && flags[3]
                                ? FileVisitResult.TERMINATE
                                : FileVisitResult.CONTINUE;
                    }
                });
                if (flags[0] && flags[1] && flags[2] && flags[3]) {
                    break;
                }
            }
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty(JAVADOC, Boolean.toString(flags[0]));
            properties.setProperty(DOKKA, Boolean.toString(flags[1]));
            properties.setProperty(SCALADOC, Boolean.toString(flags[2]));
            properties.setProperty(GROOVYDOC, Boolean.toString(flags[3]));
            properties.store(context.next().resolve(SCAN_FILE));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Document(Map<String, Repository> repositories,
                            Map<String, Resolver> resolvers,
                            Pinning pinning,
                            Javadoc javadocStep,
                            DokkaDocumentationModule dokkaModule,
                            ScalaDocumentationModule scaladocModule,
                            GroovyDocumentationModule groovydocModule,
                            UnaryOperator<Javadoc> javadoc,
                            UnaryOperator<DokkaDocumentationModule> dokka,
                            UnaryOperator<ScalaDocumentationModule> scaladoc,
                            UnaryOperator<GroovyDocumentationModule> groovydoc)
            implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            Path scanFolder = inherited.get(PREVIOUS + SCAN);
            if (scanFolder == null) {
                throw new IllegalStateException("Document sub-module is missing its upstream scan input");
            }
            SequencedProperties scan = SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE));
            boolean hasJava = scan.flag(JAVADOC) && javadoc != null;
            boolean hasGroovy = scan.flag(GROOVYDOC) && groovydoc != null;
            boolean hasScala = scan.flag(SCALADOC) && scaladoc != null;
            boolean hasKotlin = scan.flag(DOKKA) && dokka != null;

            SequencedSet<String> sourceInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
            sourceInputs.remove(PREVIOUS + SCAN);

            SequencedSet<String> outputs = new LinkedHashSet<>();
            if (hasKotlin && !hasScala && !hasGroovy) {
                document(buildExecutor, sourceInputs, outputs, DOKKA,
                         dokka.apply(dokkaModule.pinning(pinning)));
            } else if (hasGroovy && !hasScala && !hasKotlin) {
                document(buildExecutor, sourceInputs, outputs, GROOVYDOC,
                         groovydoc.apply(groovydocModule
                                .pinning(pinning)
                                .includeJava(hasJava)));
            } else if (hasScala && !hasJava && !hasKotlin && !hasGroovy) {
                document(buildExecutor, sourceInputs, outputs, SCALADOC,
                         scaladoc.apply(scaladocModule.pinning(pinning)));
            } else if (hasJava && !hasKotlin && !hasScala && !hasGroovy) {
                document(buildExecutor, sourceInputs, outputs, JAVADOC,
                         javadoc.apply(javadocStep.classpath(true)));
            } else {
                if (hasJava) {
                    document(buildExecutor, sourceInputs, outputs, JAVADOC,
                             javadoc.apply(javadocStep.classpath(true)));
                }
                if (hasKotlin) {
                    document(buildExecutor, sourceInputs, outputs, DOKKA,
                             dokka.apply(dokkaModule
                                    .pinning(pinning)
                                    .within(DOKKA)));
                }
                if (hasScala) {
                    document(buildExecutor, sourceInputs, outputs, SCALADOC,
                             scaladoc.apply(scaladocModule
                                    .pinning(pinning)
                                    .within(SCALADOC)));
                }
                if (hasGroovy) {
                    document(buildExecutor, sourceInputs, outputs, GROOVYDOC,
                             groovydoc.apply(groovydocModule
                                    .pinning(pinning)
                                    .within(GROOVYDOC)));
                }
            }
            buildExecutor.addStep(AGGREGATE, new Aggregate(), outputs);
        }

        private static void document(BuildExecutor buildExecutor,
                                     SequencedSet<String> inputs,
                                     SequencedSet<String> outputs,
                                     String name,
                                     BuildExecutorModule module) {
            if (module != null) {
                buildExecutor.addModule(name, module, inputs);
                outputs.add(name);
            }
        }

        private static void document(BuildExecutor buildExecutor,
                                     SequencedSet<String> inputs,
                                     SequencedSet<String> outputs,
                                     String name,
                                     BuildStep step) {
            if (step != null) {
                buildExecutor.addStep(name, step, inputs);
                outputs.add(name);
            }
        }
    }

    private static class Aggregate implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path combined = Files.createDirectories(context.next().resolve(Javadoc.JAVADOC));
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path source = argument.folder().resolve(Javadoc.JAVADOC);
                if (!Files.exists(source)) {
                    continue;
                }
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(combined.resolve(source.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        BuildStep.linkOrCopy(combined.resolve(source.relativize(file)), file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            if (!Files.exists(combined.resolve("index.html"))) {
                SequencedSet<String> rendered = new TreeSet<>();
                try (Stream<Path> entries = Files.list(combined)) {
                    for (Path entry : entries.toList()) {
                        if (Files.isDirectory(entry) && Files.exists(entry.resolve("index.html"))) {
                            rendered.add(entry.getFileName().toString());
                        }
                    }
                }
                StringBuilder body = new StringBuilder();
                body.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                        .append("<title>API documentation</title></head><body>\n")
                        .append("<h1>API documentation</h1>\n");
                if (rendered.isEmpty()) {
                    body.append("<p>No rendered API documentation is available for this module.</p>\n");
                } else {
                    body.append("<p>API documentation is available per language:</p>\n<ul>\n");
                    for (String name : rendered) {
                        body.append("<li><a href=\"").append(name).append("/index.html\">")
                                .append(name).append("</a></li>\n");
                    }
                    body.append("</ul>\n");
                }
                body.append("</body></html>\n");
                Files.writeString(combined.resolve("index.html"), body.toString());
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static <T> UnaryOperator<T> append(UnaryOperator<T> previous, UnaryOperator<T> next) {
        return previous == null || next == null ? null : value -> {
            T configured = previous.apply(value);
            return configured == null ? null : next.apply(configured);
        };
    }
}
