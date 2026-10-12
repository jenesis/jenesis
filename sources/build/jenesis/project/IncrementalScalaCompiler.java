package build.jenesis.project;

import module java.base;
import java.lang.reflect.Proxy;

public final class IncrementalScalaCompiler {

    static final String COMPILER = "--compiler",
            ANALYSIS = "--analysis",
            CLASSES = "--classes",
            CLASSPATH = "--classpath",
            SCALAC = "--scalac",
            JAVAC = "--javac",
            SOURCES = "--";
    private static final String BRIDGE = "dotty/tools/xsbt/CompilerBridge.class",
            MAIN = "dotty/tools/dotc/Main.class",
            INTERFACE = "xsbti/",
            LIBRARY = "scala/Predef.class",
            ZINC = "sbt.internal.inc.";

    private IncrementalScalaCompiler() {
    }

    public static void main(String[] args) throws Exception {
        List<Path> compiler = new ArrayList<>(), classpath = new ArrayList<>(), sources = new ArrayList<>();
        List<String> scalac = new ArrayList<>(), javac = new ArrayList<>();
        Path analysis = null, classes = null;
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case COMPILER -> compiler.add(absolute(args[++index]));
                case ANALYSIS -> analysis = absolute(args[++index]);
                case CLASSES -> classes = absolute(args[++index]);
                case CLASSPATH -> classpath.add(absolute(args[++index]));
                case SCALAC -> scalac.add(args[++index]);
                case JAVAC -> javac.add(args[++index]);
                case SOURCES -> {
                    while (++index < args.length) {
                        sources.add(absolute(args[index]));
                    }
                }
                default -> throw new IllegalArgumentException("Unknown argument " + args[index] + ": expected "
                        + List.of(COMPILER, ANALYSIS, CLASSES, CLASSPATH, SCALAC, JAVAC, SOURCES));
            }
        }
        if (compiler.isEmpty() || analysis == null || classes == null) {
            throw new IllegalArgumentException("Expected " + COMPILER + " <jar>, " + ANALYSIS + " <file> and "
                    + CLASSES + " <folder>");
        }
        Path bridge = null;
        String version = null, compilerVersion = null;
        List<Path> instance = new ArrayList<>(), library = new ArrayList<>();
        for (Path jar : compiler) {
            try (JarFile file = new JarFile(jar.toFile())) {
                Manifest manifest = file.getManifest();
                String implementation = manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
                if (file.getEntry(MAIN) != null) {
                    compilerVersion = implementation;
                }
                if (file.getEntry(BRIDGE) != null) {
                    bridge = jar;
                    version = implementation;
                } else if (file.stream().noneMatch(entry -> entry.getName().startsWith(INTERFACE))) {
                    instance.add(jar);
                    if (file.getEntry(LIBRARY) != null) {
                        library.add(jar);
                    }
                }
            }
        }
        if (bridge == null) {
            throw new IllegalStateException("No Zinc bridge among the Scala compiler's jars " + compiler
                    + ": resolve org.scala-lang:scala3-sbt-bridge at the compiler's version");
        }
        ClassLoader zinc = IncrementalScalaCompiler.class.getClassLoader();
        URL[] urls = new URL[instance.size()];
        for (int index = 0; index < urls.length; index++) {
            urls[index] = instance.get(index).toUri().toURL();
        }
        URLClassLoader loader = new URLClassLoader(urls, new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                return name.startsWith("xsbti.") ? zinc.loadClass(name) : super.loadClass(name, resolve);
            }
        });
        if (version != null && compilerVersion != null && !version.equals(compilerVersion)) {
            throw new IllegalStateException("The Zinc bridge " + version + " drives the Scala compiler " + compilerVersion
                    + ", but each needs the other's version: pin org.scala-lang:scala3-sbt-bridge and"
                    + " scala3-compiler_3 to one version");
        }
        String scalaVersion = version == null ? "3" : version;
        File[] all = instance.stream().map(Path::toFile).toArray(File[]::new),
                libraries = library.stream().map(Path::toFile).toArray(File[]::new),
                compilerOnly = instance.stream().filter(jar -> !library.contains(jar)).map(Path::toFile).toArray(File[]::new);
        Object scala = Proxy.newProxyInstance(zinc, new Class<?>[]{type("xsbti.compile.ScalaInstance")}, (proxy, method, arguments) -> switch (method.getName()) {
            case "version", "actualVersion" -> scalaVersion;
            case "loader", "loaderCompilerOnly", "loaderLibraryOnly" -> loader;
            case "libraryJars" -> libraries;
            case "compilerJars" -> compilerOnly;
            case "otherJars" -> new File[0];
            case "allJars" -> all;
            case "libraryJar" -> libraries[0];
            case "compilerJar" -> compilerOnly[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "Scala " + scalaVersion;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        Object logger = Proxy.newProxyInstance(zinc, new Class<?>[]{type("xsbti.Logger")}, (proxy, method, arguments) -> switch (method.getName()) {
            case "error", "warn" -> {
                System.err.println(((Supplier<?>) arguments[0]).get());
                yield null;
            }
            case "info" -> {
                System.out.println(((Supplier<?>) arguments[0]).get());
                yield null;
            }
            case "trace" -> {
                ((Throwable) ((Supplier<?>) arguments[0]).get()).printStackTrace();
                yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> "logger";
            default -> null;
        });
        Object converter = invoke(type(ZINC + "PlainVirtualFileConverter"), null, "converter");
        Object lookup = Proxy.newProxyInstance(zinc, new Class<?>[]{type("xsbti.compile.PerClasspathEntryLookup")}, (proxy, method, arguments) -> switch (method.getName()) {
            case "analysis" -> Optional.empty();
            case "definesClass" -> invoke(type(ZINC + "Locate"), null, "definesClass", arguments[0]);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> "lookup";
        });
        Object options = invoke(type("xsbti.compile.ClasspathOptionsUtil"), null, "manual");
        Object compiled = invoke(type(ZINC + "ZincUtil"), null, "scalaCompiler", scala, bridge);
        Object tools = invoke(type(ZINC + "javac.JavaTools"), null, "directOrFork", scala, options,
                type("scala.None$").getField("MODULE$").get(null));
        Object compilers = invoke(type(ZINC + "ZincUtil"), null, "compilers", tools, compiled);
        Files.createDirectories(classes);
        Object compile = invoke(type("xsbti.compile.CompileOptions"), null, "of");
        compile = invoke(compile.getClass(), compile, "withSources", files(converter, sources));
        List<Path> entries = new ArrayList<>();
        entries.add(classes);
        entries.addAll(classpath);
        compile = invoke(compile.getClass(), compile, "withClasspath", files(converter, entries));
        compile = invoke(compile.getClass(), compile, "withClassesDirectory", classes);
        compile = invoke(compile.getClass(), compile, "withScalacOptions", (Object) scalac.toArray(String[]::new));
        compile = invoke(compile.getClass(), compile, "withJavacOptions", (Object) javac.toArray(String[]::new));
        compile = invoke(compile.getClass(), compile, "withOrder", type("xsbti.compile.CompileOrder").getField("Mixed").get(null));
        compile = invoke(compile.getClass(), compile, "withConverter", Optional.of(converter));
        Object store = invoke(type(ZINC + "FileAnalysisStore"), null, "binary", analysis.toFile());
        Optional<?> contents = (Optional<?>) invoke(type("xsbti.compile.AnalysisStore"), store, "get");
        Object previous = contents.isPresent()
                ? invoke(type("xsbti.compile.PreviousResult"), null, "of",
                        Optional.of(invoke(type("xsbti.compile.AnalysisContents"), contents.get(), "getAnalysis")),
                        Optional.of(invoke(type("xsbti.compile.AnalysisContents"), contents.get(), "getMiniSetup")))
                : invoke(type("xsbti.compile.PreviousResult"), null, "of", Optional.empty(), Optional.empty());
        Object reporter = type(ZINC + "LoggedReporter")
                .getConstructor(int.class, type("xsbti.Logger"), type("scala.Function1"))
                .newInstance(100, logger, invoke(type(ZINC + "LoggedReporter"), null, "$lessinit$greater$default$3"));
        Object setup = invoke(type("xsbti.compile.Setup"), null, "of",
                lookup,
                false,
                analysis,
                type(ZINC + "FreshCompilerCache").getConstructor().newInstance(),
                invoke(type("xsbti.compile.IncOptions"), null, "of"),
                reporter,
                Optional.empty(),
                Optional.empty(),
                Array.newInstance(type("xsbti.T2"), 0));
        Object inputs = invoke(type("xsbti.compile.Inputs"), null, "of", compilers, compile, setup, previous);
        Object incremental = invoke(type(ZINC + "ZincUtil"), null, "defaultIncrementalCompiler");
        Object result;
        try {
            result = invoke(type("xsbti.compile.IncrementalCompiler"), incremental, "compile", inputs, logger);
        } catch (InvocationTargetException e) {
            if (type("xsbti.CompileFailed").isInstance(e.getCause())) {
                System.exit(1);
            }
            throw e;
        }
        invoke(type("xsbti.compile.AnalysisStore"), store, "set", invoke(type("xsbti.compile.AnalysisContents"), null, "create",
                invoke(type("xsbti.compile.CompileResult"), result, "analysis"),
                invoke(type("xsbti.compile.CompileResult"), result, "setup")));
    }

    private static Object files(Object converter, List<Path> paths) throws ReflectiveOperationException {
        Object files = Array.newInstance(type("xsbti.VirtualFile"), paths.size());
        for (int index = 0; index < paths.size(); index++) {
            Array.set(files, index, invoke(type(ZINC + "PlainVirtualFileConverter"), converter, "toVirtualFile", paths.get(index)));
        }
        return files;
    }

    private static Path absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, IncrementalScalaCompiler.class.getClassLoader());
    }

    private static Object invoke(Class<?> type, Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameters.length && matches; index++) {
                Class<?> parameter = parameters[index].isPrimitive()
                        ? MethodType.methodType(parameters[index]).wrap().returnType()
                        : parameters[index];
                matches = arguments[index] == null || parameter.isInstance(arguments[index]);
            }
            if (matches) {
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + " taking " + arguments.length + " arguments");
    }
}
