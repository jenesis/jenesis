package build.jenesis.project;

import module java.base;
import module jdk.compiler;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.JDeps;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import java.util.jar.Attributes;
import javax.tools.ToolProvider;

public class ModularizeModule implements BuildExecutorModule {

    public static final String PSEUDO = "build.jenesis.pseudo.module";

    private static final String PREPARE = "prepare", DESCRIBE = "describe", MODULARIZE = "modularize";

    private static final String STAGED = "staged.properties";

    private final ProcessHandler.Factory factory;
    private final boolean synthetic;

    public ModularizeModule(ProcessHandler.Factory factory, boolean synthetic) {
        this.factory = factory;
        this.synthetic = synthetic;
    }

    public static Boolean configured(Path properties) throws IOException {
        if (properties == null) {
            return null;
        }
        String mode = SequencedProperties.ofFiles(properties).getProperty("mode", "declared");
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "declared" -> false;
            case "synthetic" -> true;
            case "none" -> null;
            default -> throw new IllegalArgumentException("Unknown module mode: " + mode);
        };
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(PREPARE, new Prepare(synthetic), inherited.sequencedKeySet());
        buildExecutor.addStep(DESCRIBE, new JDeps(factory), PREPARE);
        SequencedSet<String> inputs = new LinkedHashSet<>();
        inputs.add(PREPARE);
        inputs.add(DESCRIBE);
        inputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(MODULARIZE, new Modularize(), inputs);
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(MODULARIZE) ? Optional.of("") : Optional.empty();
    }

    private static String module(Path jar) {
        String file = jar.getFileName().toString();
        return file.substring(0, file.length() - ".jar".length());
    }

    private static ModuleDescriptor derive(Path jar) {
        Set<ModuleReference> references;
        try {
            references = ModuleFinder.of(jar).findAll();
        } catch (FindException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalArgumentException(jar.getFileName()
                    + " cannot be resolved to a named module: "
                    + cause.getMessage(), e);
        }
        if (references.size() != 1) {
            throw new IllegalArgumentException(jar.getFileName() + " does not describe a single module");
        }
        return references.iterator().next().descriptor();
    }

    private static class Prepare implements BuildStep {

        @SuppressWarnings("unused")
        private final String version = Runtime.version().toString();

        private final boolean synthetic;

        private Prepare(boolean synthetic) {
            this.synthetic = synthetic;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                    Path.of(DEPENDENCIES),
                    Path.of(Dependencies.RESOLVED)));
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path modules = Files.createDirectory(context.next().resolve(JDeps.MODULES));
            Path analyzed = Files.createDirectory(context.next().resolve(JDeps.ANALYZED));
            SequencedProperties staged = new SequencedProperties();
            SequencedMap<Path, String> placed = new LinkedHashMap<>();
            SequencedMap<String, Path> claimed = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path index = argument.folder().resolve(DEPENDENCIES);
                if (!Files.exists(index)) {
                    continue;
                }
                SequencedProperties properties = SequencedProperties.ofFiles(index);
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    int space = value.indexOf(' ');
                    Path jar = argument.folder()
                            .resolve(space < 0 ? value : value.substring(0, space))
                            .normalize();
                    if (!Files.exists(jar)) {
                        continue;
                    }
                    String relative = placed.get(jar);
                    if (relative == null) {
                        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                        String module;
                        if (descriptor == null) {
                            if (!synthetic) {
                                throw new IllegalArgumentException("No module name is declared by "
                                        + key
                                        + ": require an upstream Automatic-Module-Name, declare @jenesis.alias"
                                        + " for the artifact, or set mode=synthetic in modules.properties to"
                                        + " derive a name of the form "
                                        + PSEUDO
                                        + "<hash>");
                            }
                            module = PSEUDO + pseudonym(jar);
                        } else {
                            module = descriptor.name();
                        }
                        boolean named = descriptor != null && !descriptor.isAutomatic();
                        Path previous = claimed.putIfAbsent(module, jar);
                        if (previous != null && !Files.isSameFile(previous, jar)) {
                            throw new IllegalArgumentException("Module " + module
                                    + " is claimed by both "
                                    + previous.getFileName()
                                    + " and "
                                    + jar.getFileName());
                        }
                        relative = (named ? JDeps.MODULES : JDeps.ANALYZED) + module + ".jar";
                        Path target = (named ? modules : analyzed).resolve(module + ".jar");
                        if (!Files.exists(target)) {
                            BuildStep.linkOrCopy(target, jar);
                        }
                        placed.put(jar, relative);
                    }
                    staged.setProperty(key, relative);
                }
            }
            staged.store(context.next().resolve(STAGED));
            SequencedProperties options = new SequencedProperties();
            options.setProperty("--multi-release", String.valueOf(Runtime.version().feature()));
            options.setProperty("--ignore-missing-deps", "");
            options.store(Files.createDirectories(context.next().resolve(ProcessBuildStep.PROCESS))
                    .resolve("jdeps.properties"));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static String pseudonym(Path jar) throws IOException {
            return HexFormat.of().formatHex(new HashDigestFunction("SHA-256").hash(jar), 0, 16);
        }
    }

    private static class Modularize implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                    Path.of(STAGED),
                    Path.of(JDeps.ANALYZED),
                    Path.of(JDeps.MODULES),
                    Path.of(JDeps.DESCRIPTORS),
                    Path.of(DEPENDENCIES)));
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<Path, SequencedProperties> stagings = new LinkedHashMap<>();
            SequencedMap<String, String> checksums = new LinkedHashMap<>();
            SequencedSet<Path> descriptors = new LinkedHashSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path staged = argument.folder().resolve(STAGED);
                if (Files.exists(staged)) {
                    stagings.put(argument.folder(), SequencedProperties.ofFiles(staged));
                }
                Path generated = argument.folder().resolve(JDeps.DESCRIPTORS);
                if (Files.isDirectory(generated)) {
                    descriptors.add(generated);
                }
                Path index = argument.folder().resolve(DEPENDENCIES);
                if (Files.exists(index)) {
                    SequencedProperties.ofFiles(index).forEachProperty((key, value) -> {
                        int space = value.indexOf(' ');
                        checksums.putIfAbsent(key, space < 0 ? "" : value.substring(space + 1).trim());
                    });
                }
            }
            Path target = Files.createDirectory(context.next().resolve(Dependencies.RESOLVED));
            boolean synthesizing = stagings.values().stream()
                    .flatMap(staging -> staging.stringPropertyNames().stream().map(staging::getProperty))
                    .anyMatch(relative -> relative.startsWith(JDeps.ANALYZED));
            SequencedMap<String, String> owners = synthesizing ? owners(stagings) : new LinkedHashMap<>();
            SequencedMap<Path, Path> emitted = new LinkedHashMap<>();
            SequencedProperties index = new SequencedProperties();
            for (Map.Entry<Path, SequencedProperties> staging : stagings.entrySet()) {
                for (String key : staging.getValue().stringPropertyNames()) {
                    String relative = staging.getValue().getProperty(key);
                    Path source = staging.getKey().resolve(relative).normalize();
                    if (!Files.exists(source)) {
                        throw new IllegalStateException("Staged module " + relative + " does not exist for " + key);
                    }
                    boolean synthesized = relative.startsWith(JDeps.ANALYZED);
                    Path file = emitted.get(source);
                    if (file == null) {
                        file = target.resolve(source.getFileName().toString());
                        if (emitted.containsValue(file)) {
                            throw new IllegalStateException("Module " + module(file) + " is staged more than once");
                        }
                        if (synthesized) {
                            inject(source, file, synthesize(source, descriptors, owners));
                        } else {
                            BuildStep.linkOrCopy(file, source);
                        }
                        emitted.put(source, file);
                    }
                    String checksum = synthesized ? "" : checksums.getOrDefault(key, "");
                    String value = context.next().relativize(file).toString().replace(File.separatorChar, '/');
                    index.setProperty(key, checksum.isEmpty() ? value : value + " " + checksum);
                }
            }
            index.store(context.next().resolve(DEPENDENCIES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static SequencedMap<String, String> owners(SequencedMap<Path, SequencedProperties> stagings) {
            SequencedMap<String, String> owners = new LinkedHashMap<>();
            for (ModuleReference reference : ModuleFinder.ofSystem().findAll()) {
                for (String name : reference.descriptor().packages()) {
                    owners.putIfAbsent(name, reference.descriptor().name());
                }
            }
            SequencedSet<Path> jars = new LinkedHashSet<>();
            for (Map.Entry<Path, SequencedProperties> staging : stagings.entrySet()) {
                for (String key : staging.getValue().stringPropertyNames()) {
                    jars.add(staging.getKey().resolve(staging.getValue().getProperty(key)).normalize());
                }
            }
            for (Path jar : jars) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                boolean named = descriptor != null && !descriptor.isAutomatic();
                for (String name : (named ? descriptor : derive(jar)).packages()) {
                    owners.putIfAbsent(name, named ? descriptor.name() : module(jar));
                }
            }
            return owners;
        }

        private static byte[] synthesize(Path jar,
                                         SequencedSet<Path> descriptors,
                                         SequencedMap<String, String> owners) throws IOException {
            String module = module(jar);
            ModuleDescriptor derived = derive(jar);
            SequencedSet<String> packages = new TreeSet<>(derived.packages());
            SequencedMap<String, Integer> requires = requires(module, descriptors, packages.isEmpty());
            SequencedSet<String> uses = new TreeSet<>();
            for (String service : services(jar)) {
                String owner = service.substring(0, Math.max(0, service.lastIndexOf('.')));
                if (packages.contains(owner) || owners.containsKey(owner)) {
                    uses.add(service);
                }
            }
            return ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(module), builder -> {
                builder.moduleFlags(ClassFile.ACC_OPEN);
                builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
                requires.forEach((name, flags) -> builder.requires(ModuleDesc.of(name), flags, null));
                packages.forEach(name -> builder.exports(PackageDesc.of(name), 0));
                uses.forEach(service -> builder.uses(ClassDesc.of(service)));
                for (ModuleDescriptor.Provides provides : derived.provides()) {
                    List<ClassDesc> providers = provides.providers().stream()
                            .filter(provider -> packages.contains(
                                    provider.substring(0, Math.max(0, provider.lastIndexOf('.')))))
                            .map(ClassDesc::of)
                            .toList();
                    if (!providers.isEmpty()) {
                        builder.provides(ClassDesc.of(provides.service()), providers.toArray(ClassDesc[]::new));
                    }
                }
            }), builder -> {
                builder.withVersion(ClassFile.JAVA_9_VERSION, 0);
                if (!packages.isEmpty()) {
                    builder.with(ModulePackagesAttribute.ofNames(packages.stream().map(PackageDesc::of).toList()));
                }
            });
        }

        private static SequencedMap<String, Integer> requires(String module,
                                                              SequencedSet<Path> descriptors,
                                                              boolean optional) throws IOException {
            Path source = null;
            for (Path folder : descriptors) {
                Path candidate = folder.resolve(module);
                if (!Files.isDirectory(candidate)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(candidate)) {
                    source = files.filter(file -> file.getFileName().toString().equals("module-info.java"))
                            .sorted()
                            .findFirst()
                            .orElse(null);
                }
                if (source != null) {
                    break;
                }
            }
            if (source == null) {
                if (optional) {
                    return new LinkedHashMap<>();
                }
                throw new IllegalStateException("No module descriptor was generated for " + module);
            }
            Path file = source;
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            JavacTask javac = (JavacTask) compiler.getTask(new PrintWriter(Writer.nullWriter()),
                    compiler.getStandardFileManager(null, null, null),
                    null,
                    null,
                    null,
                    List.of(new SimpleJavaFileObject(file.toUri(), JavaFileObject.Kind.SOURCE) {
                        @Override
                        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                            return Files.readString(file);
                        }
                    }));
            SequencedMap<String, Integer> requires = new LinkedHashMap<>();
            for (CompilationUnitTree unit : javac.parse()) {
                ModuleTree tree = unit.getModule();
                if (tree == null) {
                    continue;
                }
                for (DirectiveTree directive : tree.getDirectives()) {
                    if (directive instanceof RequiresTree required) {
                        String name = required.getModuleName().toString();
                        if (name.equals("java.base")) {
                            continue;
                        }
                        requires.put(name, (required.isTransitive() ? ClassFile.ACC_TRANSITIVE : 0)
                                | (required.isStatic() ? ClassFile.ACC_STATIC_PHASE : 0));
                    }
                }
            }
            return requires;
        }

        private static SequencedSet<String> services(Path jar) throws IOException {
            SequencedSet<String> services = new LinkedHashSet<>();
            try (JarFile file = new JarFile(jar.toFile(), false, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
                for (JarEntry entry : (Iterable<JarEntry>) file.versionedStream()::iterator) {
                    if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) {
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream input = file.getInputStream(entry)) {
                        bytes = input.readAllBytes();
                    }
                    ClassModel model;
                    try {
                        model = ClassFile.of().parse(bytes);
                    } catch (IllegalArgumentException _) {
                        continue;
                    }
                    boolean loads = false;
                    for (PoolEntry pooled : model.constantPool()) {
                        if (pooled instanceof MemberRefEntry reference
                                && reference.owner().asInternalName().equals("java/util/ServiceLoader")) {
                            loads = true;
                            break;
                        }
                    }
                    if (!loads) {
                        continue;
                    }
                    for (MethodModel method : model.methods()) {
                        method.code().ifPresent(code -> {
                            ClassDesc loaded = null;
                            for (CodeElement element : code) {
                                if (element instanceof ConstantInstruction constant
                                        && constant.constantValue() instanceof ClassDesc description) {
                                    loaded = description;
                                } else if (element instanceof InvokeInstruction invoke
                                        && invoke.owner().asInternalName().equals("java/util/ServiceLoader")
                                        && (invoke.name().stringValue().equals("load")
                                        || invoke.name().stringValue().equals("loadInstalled"))) {
                                    if (loaded != null && !loaded.isArray() && !loaded.isPrimitive()) {
                                        services.add(loaded.packageName().isEmpty()
                                                ? loaded.displayName()
                                                : loaded.packageName() + "." + loaded.displayName());
                                    }
                                    loaded = null;
                                }
                            }
                        });
                    }
                }
            }
            return services;
        }

        private static void inject(Path source, Path target, byte[] descriptor) throws IOException {
            try (JarFile jar = new JarFile(source.toFile(), false, ZipFile.OPEN_READ)) {
                boolean signed = jar.stream().map(JarEntry::getName).anyMatch(Modularize::signature);
                try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
                    for (JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
                        String name = entry.getName();
                        if (name.equals("module-info.class") || signature(name)) {
                            continue;
                        }
                        byte[] replacement = null;
                        if (signed && name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) {
                            Manifest manifest;
                            try (InputStream input = jar.getInputStream(entry)) {
                                manifest = new Manifest(input);
                            }
                            manifest.getEntries().values().forEach(attributes -> attributes.keySet()
                                    .removeIf(attribute -> attribute.toString().endsWith("-Digest")
                                            || attribute.toString().endsWith("-Digest-Manifest")));
                            manifest.getEntries().values().removeIf(Attributes::isEmpty);
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            manifest.write(buffer);
                            replacement = buffer.toByteArray();
                        }
                        JarEntry copy = new JarEntry(name);
                        copy.setTime(entry.getTime());
                        out.putNextEntry(copy);
                        if (replacement != null) {
                            out.write(replacement);
                        } else if (!entry.isDirectory()) {
                            try (InputStream input = jar.getInputStream(entry)) {
                                input.transferTo(out);
                            }
                        }
                        out.closeEntry();
                    }
                    JarEntry entry = new JarEntry("module-info.class");
                    entry.setTime(0L);
                    out.putNextEntry(entry);
                    out.write(descriptor);
                    out.closeEntry();
                }
            }
        }

        private static boolean signature(String name) {
            if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) {
                return false;
            }
            String file = name.substring("META-INF/".length()).toUpperCase(Locale.ROOT);
            return file.endsWith(".SF")
                    || file.endsWith(".DSA")
                    || file.endsWith(".RSA")
                    || file.endsWith(".EC")
                    || file.startsWith("SIG-");
        }
    }
}
