package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;

public class JMod extends ProcessBuildStep {

    public static final String JMODS = "jmods/", CONFIG = "jmodconfig/", LIBRARIES = "jmodlibs/",
            COMMANDS = "jmodcmds/";
    private static final SequencedSet<String> LEGAL = Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(
            "META-INF/NOTICE", "META-INF/LICENSE", "META-INF/license/", "META-INF/licenses/", "LICENSE", "about.html")));

    private final OffsetDateTime timestamp;
    private final String group;
    private final SequencedSet<String> legal;
    private final boolean strict;

    public JMod(ProcessHandler.Factory factory) {
        this(factory.apply("jmod", "bin/jmod"),
             BuildStep.timestamp(),
             "main",
             LEGAL,
             false,
             Terms.of("jmod"));
    }

    public static JMod ofEnvironment(Environment environment,
                                     ProcessHandler.Factory factory) {
        List<String> legal = environment.entries("jmod.legal");
        return new JMod(factory.apply("jmod", "bin/jmod"),
                BuildStep.timestamp(environment),
                "main",
                legal == null ? LEGAL : new LinkedHashSet<>(legal),
                environment.flag("jmod.strict", false),
                Terms.ofEnvironment(environment, "jmod"));
    }

    private JMod(Function<List<String>, ? extends ProcessHandler> factory,
                 OffsetDateTime timestamp,
                 String group,
                 SequencedSet<String> legal,
                 boolean strict,
                 Terms terms) {
        super("jmod", factory, terms);
        this.timestamp = timestamp;
        this.group = group;
        this.legal = legal;
        this.strict = strict;
    }

    public JMod verbose(BiConsumer<Boolean, String> printing) {
        return new JMod(factory, timestamp, group, legal, strict, terms.printing(printing));
    }

    public JMod timestamp(OffsetDateTime timestamp) {
        return new JMod(factory, timestamp, group, legal, strict, terms);
    }

    public JMod group(String group) {
        return new JMod(factory, timestamp, group, legal, strict, terms);
    }

    public JMod legal(SequencedSet<String> legal) {
        return new JMod(factory, timestamp, group, legal, strict, terms);
    }

    public JMod strict(boolean strict) {
        return new JMod(factory, timestamp, group, legal, strict, terms);
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        List<String> classPath = new ArrayList<>(), config = new ArrayList<>(), libs = new ArrayList<>(), cmds = new ArrayList<>();
        String moduleName = null;
        SequencedMap<Path, Path> notices = new LinkedHashMap<>();
        Path notice = context.supplement().resolve("legal");
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (Stream<Path> jars = Files.list(artifacts)) {
                    jars.filter(jar -> jar.getFileName().toString().endsWith(".jar")).sorted().forEach(jar -> notices.put(jar, notice));
                }
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "runtime")) {
                String name = jar.getFileName().toString();
                notices.putIfAbsent(jar, notice.resolve(name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name));
            }
            Path classes = argument.folder().resolve(BuildStep.CLASSES);
            if (Files.isDirectory(classes)) {
                classPath.add(classes.toString());
                Path moduleInfo = classes.resolve("module-info.class");
                if (moduleName == null && Files.exists(moduleInfo)) {
                    try (InputStream in = Files.newInputStream(moduleInfo)) {
                        moduleName = ModuleDescriptor.read(in).name();
                    }
                }
            }
            collect(argument.folder().resolve(CONFIG), config);
            collect(argument.folder().resolve(LIBRARIES), libs);
            collect(argument.folder().resolve(COMMANDS), cmds);
        }
        if (moduleName == null) {
            return CompletableFuture.completedStage(null);
        }
        for (Map.Entry<Path, Path> jar : notices.entrySet()) {
            boolean found = false;
            try (JarFile file = new JarFile(jar.getKey().toFile())) {
                for (JarEntry entry : (Iterable<JarEntry>) file.stream()::iterator) {
                    String name = entry.getName(), lower = name.toLowerCase(Locale.ROOT);
                    String relative = null;
                    for (String candidate : legal) {
                        String expected = candidate.toLowerCase(Locale.ROOT);
                        if (expected.endsWith("/") ? lower.startsWith(expected) : lower.equals(expected)
                                || lower.startsWith(expected + ".") && lower.indexOf('/', expected.length()) == -1) {
                            relative = expected.endsWith("/")
                                    ? name.substring(expected.length())
                                    : name.substring(name.lastIndexOf('/') + 1);
                            break;
                        }
                    }
                    if (entry.isDirectory() || relative == null || relative.isEmpty()) {
                        continue;
                    }
                    Path target = BuildStep.resolveContained(jar.getValue(), relative);
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) {
                        try (InputStream in = file.getInputStream(entry)) {
                            Files.copy(in, target);
                        }
                    }
                    found = true;
                }
            }
            if (!found && strict) {
                throw new IllegalStateException(jar.getKey().getFileName() + " carries none of " + legal
                        + " for the legal notices of " + moduleName + " - add them to it, name the files it"
                        + " carries with -Djenesis.jmod.legal, or build with -Djenesis.jmod.strict=false");
            }
        }
        List<String> commands = new ArrayList<>(List.of("create"));
        if (timestamp != null) {
            commands.add("--date=" + timestamp);
        }
        option(commands, "--class-path", classPath);
        option(commands, "--config", config);
        option(commands, "--libs", libs);
        option(commands, "--cmds", cmds);
        if (Files.isDirectory(notice)) {
            option(commands, "--legal-notices", List.of(notice.toString()));
        }
        commands.add(Files.createDirectory(context.next().resolve(JMODS)).resolve(moduleName + ".jmod").toString());
        return CompletableFuture.completedStage(commands);
    }

    private static void collect(Path folder, List<String> into) {
        if (Files.isDirectory(folder)) {
            into.add(folder.toString());
        }
    }

    private static void option(List<String> commands, String name, List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        for (String entry : paths) {
            if (entry.indexOf(File.pathSeparatorChar) != -1) {
                throw new IllegalArgumentException(
                        "Path entry contains separator '" + File.pathSeparator + "': " + entry);
            }
        }
        commands.add(name);
        commands.add(String.join(File.pathSeparator, paths));
    }
}
