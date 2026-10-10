package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import build.jenesis.module.ModuleInfoParser;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class Javadoc extends ProcessBuildStep {

    public static final String JAVADOC = "javadoc/";
    private static final ModuleInfoParser MODULE_INFO_PARSER = new ModuleInfoParser();

    private final String within;
    private final String group;
    private final boolean classpath;
    private final boolean timestamped;

    public Javadoc(ProcessHandler.Factory factory) {
        this(factory.apply("javadoc", "bin/javadoc"), null, "main", false, false, new Terms());
    }

    public static Javadoc ofEnvironment(Environment environment,
                                        ProcessHandler.Factory factory) {
        return new Javadoc(factory.apply("javadoc", "bin/javadoc"),
                null,
                "main",
                false,
                BuildStep.timestamp(environment) == null,
                Terms.ofEnvironment(environment, "javadoc"));
    }

    private Javadoc(Function<List<String>, ? extends ProcessHandler> factory,
                    String within,
                    String group,
                    boolean classpath,
                    boolean timestamped,
                    Terms terms) {
        super("javadoc", factory, terms);
        this.within = within;
        this.group = group;
        this.classpath = classpath;
        this.timestamped = timestamped;
    }

    public Javadoc within(String within) {
        return new Javadoc(factory, within, group, classpath, timestamped, terms);
    }

    public Javadoc group(String group) {
        return new Javadoc(factory, within, group, classpath, timestamped, terms);
    }

    public Javadoc classpath(boolean classpath) {
        return new Javadoc(factory, within, group, classpath, timestamped, terms);
    }

    public Javadoc timestamped(boolean timestamped) {
        return new Javadoc(factory, within, group, classpath, timestamped, terms);
    }

    public Javadoc verbose(BiConsumer<Boolean, String> printing) {
        return new Javadoc(factory, within, group, classpath, timestamped, terms.printing(printing));
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        Path documentation = within == null
                ? Files.createDirectory(context.next().resolve(JAVADOC))
                : Files.createDirectories(context.next().resolve(JAVADOC).resolve(within));
        List<String> files = new ArrayList<>(),
                path = new ArrayList<>(),
                commands = new ArrayList<>(List.of(
                        "-d", documentation.toString(),
                        "-quiet",
                        "-tag", "jenesis.release:a:Release:",
                        "-tag", "jenesis.main:a:Main class:",
                        "-tag", "jenesis.test:a:Tests the module:",
                        "-tag", "jenesis.pin:a:Pinned dependencies:",
                        "-tag", "jenesis.alias:a:Module aliases:",
                        "-tag", "jenesis.exclude:a:Excluded dependencies:",
                        "-tag", "jenesis.override:a:Overridden modules:",
                        "-tag", "jenesis.attach:a:Attached agents:",
                        "-tag", "jenesis.native:a:Native access:",
                        "-tag", "jenesis.bom:a:Imported bills of materials:",
                        "-tag", "jenesis.plugin:a:Compiler plugins:",
                        "-tag", "jenesis.layer:a:Isolated layers:",
                        "-tag", "jenesis.signature:a:Signing keys:"));
        if (!timestamped) {
            commands.add("-notimestamp");
        }
        if (properties.values().stream().noneMatch(folder -> folder.keySet().stream()
                .anyMatch(key -> key.startsWith("-Xdoclint")))) {
            commands.add("-Xdoclint:none");
        }
        List<String> excluded = properties.values().stream()
                .map(folder -> folder.get("-exclude"))
                .filter(Objects::nonNull)
                .flatMap(value -> Stream.of(value.split("[:\n]")))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
        String preview = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path javac = argument.folder().resolve(ProcessBuildStep.PROCESS + "javac.properties");
            if (preview == null && Files.isRegularFile(javac)) {
                SequencedProperties release = SequencedProperties.ofFiles(javac);
                if (release.containsKey("--enable-preview")) {
                    preview = release.value("--release");
                }
            }
            Path sources = argument.folder().resolve(BuildStep.SOURCES),
                    classes = argument.folder().resolve(BuildStep.CLASSES);
            if (Files.exists(classes)) {
                path.add(classes.toString());
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifacts)) {
                    for (Path file : stream) {
                        path.add(file.toString());
                    }
                }
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                path.add(jar.toString());
            }
            if (Files.exists(sources)) {
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.toString(),
                                owner = sources.relativize(file.getParent()).toString().replace(File.separatorChar, '.');
                        if (name.endsWith(".java")
                                && !(classpath && name.endsWith(File.separator + "module-info.java"))
                                && excluded.stream().noneMatch(prefix -> owner.equals(prefix) || owner.startsWith(prefix + "."))) {
                            files.add(name);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        if (preview != null) {
            commands.addAll(List.of("--enable-preview", "--release", preview));
        }
        files.sort(null);
        if (properties.values().stream().noneMatch(folder -> folder.keySet().stream()
                .anyMatch(key -> key.equals("-package") || key.equals("-private") || key.startsWith("--show-types")))
                && !declaresPublicType(files)) {
            return CompletableFuture.completedStage(null);
        }
        path.sort(null);
        String moduleInfo = classpath ? null : files.stream()
                .filter(file -> file.endsWith(File.separator + "module-info.java"))
                .findFirst()
                .orElse(null);
        String module = moduleInfo == null ? null : MODULE_INFO_PARSER.identify(Path.of(moduleInfo)).coordinate();
        if (!path.isEmpty()) {
            for (String entry : path) {
                if (entry.indexOf(File.pathSeparatorChar) != -1) {
                    throw new IllegalArgumentException(
                            "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                }
            }
            List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>(), patched = new ArrayList<>();
            for (String entry : path) {
                ModuleDescriptor descriptor = module == null ? null : PathPlacement.moduleDescriptor(Path.of(entry));
                (descriptor == null
                        ? classPath
                        : descriptor.name().equals(module) ? patched : modulePath).add(entry);
            }
            for (Map.Entry<String, List<String>> paths : List.of(
                    Map.entry("--module-path", modulePath),
                    Map.entry("--class-path", classPath),
                    Map.entry("--patch-module", patched)
            )) {
                if (!paths.getValue().isEmpty()) {
                    commands.add(paths.getKey());
                    commands.add((paths.getKey().equals("--patch-module") ? module + "=" : "")
                            + String.join(File.pathSeparator, paths.getValue()));
                }
            }
        }
        commands.addAll(files);
        return CompletableFuture.completedStage(List.of("@" + argumentFile(context.supplement().resolve("javadoc.args"),
                commands)));
    }

    private static boolean declaresPublicType(List<String> files) throws IOException {
        if (files.isEmpty()) {
            return false;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(_ -> { }, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(Writer.nullWriter(),
                    manager,
                    _ -> { },
                    null,
                    null,
                    manager.getJavaFileObjectsFromStrings(files));
            for (CompilationUnitTree unit : task.parse()) {
                for (Tree type : unit.getTypeDecls()) {
                    if (type instanceof ClassTree declaration
                            && declaration.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
