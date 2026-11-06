package build.jenesis.step;

import module java.base;
import build.jenesis.SequencedProperties;
import build.jenesis.daemon.Worker;
import java.lang.classfile.Attributes;

final class JavaWorker {

    private static final Set<String> TAKING = Set.of("-p",
            "--module-path",
            "--upgrade-module-path",
            "--add-modules",
            "--limit-modules",
            "-m",
            "--module",
            "--add-reads",
            "--add-exports",
            "--add-opens",
            "--patch-module",
            "--enable-native-access",
            "--source",
            "-d",
            "--describe-module",
            "-jar");
    private static final Set<String> CLASS_PATH = Set.of("-cp", "-classpath", "--class-path"),
            MODULE_PATH = Set.of("-p", "--module-path"),
            MODULE = Set.of("-m", "--module"),
            VALUED = Set.of("--add-modules", "--add-opens", "--add-exports", "--add-reads", "--enable-native-access");
    private static final Set<String> COMPILED = Set.of("--add-modules=",
            "--add-opens=",
            "--add-exports=",
            "--add-reads=",
            "--enable-native-access=");
    private static final Map<String, String> HEAP = Map.of("-Xmx", "-R:MaxHeapSize=",
            "-Xms", "-R:MinHeapSize=",
            "-Xss", "-R:StackSize=");
    private static final Map<Path, Lock> TRAINING = new ConcurrentHashMap<>();
    private static final Semaphore COMPILING = new Semaphore(1);
    private static final int MISSING = 172;
    private static final Map<Key, Deque<Running>> IDLE = new ConcurrentHashMap<>();
    private static final Map<Key, Shared> SHARED = new HashMap<>();
    private static final List<Closeable> NESTED = new ArrayList<>();
    private static Path compiled;
    private static int scopes;

    private JavaWorker() {
    }

    static Call call(List<String> commands) {
        Path launcher = Path.of(commands.getFirst());
        String name = launcher.getFileName().toString(), tool = name.replaceFirst("\\.exe$", "");
        Path java = launcher.resolveSibling(name.endsWith(".exe") ? "java.exe" : "java");
        if (!tool.equals("java") && launcher.getParent() != null && Files.isRegularFile(java)) {
            List<String> options = new ArrayList<>(), arguments = new ArrayList<>(List.of(tool));
            for (String command : commands.subList(1, commands.size())) {
                if (command.startsWith("-J")) {
                    options.add(command.substring(2));
                } else {
                    arguments.add(command);
                }
            }
            return new Call(java.toString(), List.copyOf(options), "", "", Worker.TOOL, List.copyOf(arguments));
        }
        List<String> options = new ArrayList<>();
        String classpath = null;
        int index = 1;
        for (; index < commands.size(); index++) {
            String command = commands.get(index);
            if (CLASS_PATH.contains(command)) {
                if (classpath != null || index + 1 >= commands.size()) {
                    return null;
                }
                classpath = commands.get(++index);
            } else if (command.startsWith("--class-path=")) {
                if (classpath != null) {
                    return null;
                }
                classpath = command.substring("--class-path=".length());
            } else if (!command.startsWith("-")) {
                break;
            } else if (TAKING.contains(command) || command.startsWith("@")) {
                return null;
            } else {
                options.add(command);
            }
        }
        if (classpath == null || index >= commands.size() || commands.get(index).startsWith("@")) {
            return null;
        }
        return new Call(commands.getFirst(),
                List.copyOf(options),
                classpath,
                "",
                commands.get(index),
                List.copyOf(commands.subList(index + 1, commands.size())));
    }

    static Call imaged(List<String> commands) throws IOException {
        Path launcher = Path.of(commands.getFirst());
        String name = launcher.getFileName().toString(), tool = name.replaceFirst("\\.exe$", "");
        if (!tool.equals("java")) {
            return launcher.getParent() != null
                    && Files.isRegularFile(launcher.resolveSibling(name.endsWith(".exe") ? "java.exe" : "java"))
                    && java.util.spi.ToolProvider.findFirst(tool).isPresent() ? call(commands) : null;
        }
        List<String> expanded = SequencedProperties.arguments(commands.subList(1, commands.size())
                .toArray(String[]::new)), options = new ArrayList<>();
        String classpath = "", modulepath = "", main = null;
        int index = 0;
        for (; index < expanded.size() && main == null; index++) {
            String option = expanded.get(index), value = index + 1 < expanded.size() ? expanded.get(index + 1) : null;
            if (!option.startsWith("-")) {
                main = option;
            } else if ((CLASS_PATH.contains(option) || MODULE_PATH.contains(option) || MODULE.contains(option)
                    || VALUED.contains(option)) && value == null) {
                throw new IllegalStateException("The JVM option " + option + " names no value in " + commands);
            } else if (CLASS_PATH.contains(option)) {
                classpath = expanded.get(++index);
            } else if (option.startsWith("--class-path=")) {
                classpath = option.substring("--class-path=".length());
            } else if (MODULE_PATH.contains(option)) {
                modulepath = expanded.get(++index);
            } else if (option.startsWith("--module-path=")) {
                modulepath = option.substring("--module-path=".length());
            } else if (MODULE.contains(option) || option.startsWith("--module=")) {
                String module = MODULE.contains(option) ? expanded.get(++index) : option.substring("--module=".length());
                int slash = module.indexOf('/');
                if (slash < 0) {
                    throw new IllegalStateException("jenesis.process.mode=native runs a module's main class in a native"
                            + " image, but " + module + " names none: name it as <module>/<class>, or build with"
                            + " jenesis.process.mode=aot");
                }
                options.add("--add-modules=" + module.substring(0, slash));
                main = module.substring(slash + 1);
            } else if (VALUED.contains(option)) {
                options.add(option + "=" + expanded.get(++index));
            } else if (TAKING.contains(option)) {
                throw new IllegalStateException("jenesis.process.mode=native cannot carry the JVM option " + option
                        + " into a native image: remove it, or build with jenesis.process.mode=aot");
            } else {
                options.add(option);
            }
        }
        if (main == null || main.endsWith(".java")) {
            throw new IllegalStateException("jenesis.process.mode=native runs a main class in a native image, but "
                    + commands + " names " + (main == null ? "none" : "the source " + main) + ": build with"
                    + " jenesis.process.mode=aot");
        }
        return new Call(commands.getFirst(),
                List.copyOf(options),
                classpath,
                modulepath,
                main,
                List.copyOf(expanded.subList(index, expanded.size())));
    }

    static int execute(Call call,
                       boolean shared,
                       boolean lasting,
                       SortedMap<String, String> environment,
                       Path directory,
                       Path output,
                       Path error,
                       ProcessHandler.Tee tee) throws IOException {
        List<String> entries = call.classpath().isEmpty()
                ? List.of()
                : List.of(call.classpath().split(Pattern.quote(File.pathSeparator), -1));
        List<Object> identities = new ArrayList<>();
        for (String entry : entries) {
            Path path = Path.of(entry).toAbsolutePath();
            if (Files.isRegularFile(path)) {
                Object identity = Files.readAttributes(path, BasicFileAttributes.class).fileKey();
                if (identity == null) {
                    try (InputStream input = Files.newInputStream(path)) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
                        identity = HexFormat.of().formatHex(digest.digest());
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                }
                identities.add(identity);
            } else {
                identities.add(path.toString());
            }
        }
        Key key = new Key(call.java(), call.options(), List.copyOf(identities), new TreeMap<>(environment), directory);
        List<String> request = new ArrayList<>();
        if (shared && !call.main().equals(Worker.TOOL)) {
            request.add(Worker.SHARED);
        }
        request.add(call.main());
        request.addAll(call.arguments());
        if (shared || call.main().equals(Worker.TOOL)) {
            Shared worker;
            synchronized (SHARED) {
                worker = SHARED.get(key);
                if (worker == null || worker.ended) {
                    worker = new Shared(start(key, entries));
                    SHARED.put(key, worker);
                }
                worker.lasting |= lasting;
            }
            try (OutputStream out = Files.newOutputStream(output); OutputStream err = Files.newOutputStream(error)) {
                Lines printed = new Lines(out, tee == null ? null : tee.out()),
                        errored = new Lines(err, tee == null ? null : tee.err());
                int code = worker.execute(request, printed, errored);
                printed.close();
                errored.close();
                return code;
            }
        }
        Deque<Running> idle = IDLE.computeIfAbsent(key, _ -> new ConcurrentLinkedDeque<>());
        Running worker;
        do {
            worker = idle.pollFirst();
        } while (worker != null && !worker.process().isAlive());
        if (worker == null) {
            worker = start(key, entries);
        }
        boolean alive = false;
        try (OutputStream out = Files.newOutputStream(output); OutputStream err = Files.newOutputStream(error)) {
            Lines printed = new Lines(out, tee == null ? null : tee.out()),
                    errored = new Lines(err, tee == null ? null : tee.err());
            int code;
            try {
                Worker.request(worker.requests(), 0, request);
                code = Worker.receive(worker.frames(), printed, errored);
                alive = true;
            } catch (EOFException _) {
                try {
                    code = worker.process().waitFor();
                } catch (InterruptedException e) {
                    worker.process().destroyForcibly();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            printed.close();
            errored.close();
            return code;
        } finally {
            if (alive) {
                idle.offerFirst(worker);
            } else {
                worker.process().destroyForcibly();
            }
        }
    }

    static Once once(Call call, Path aot, Path image) throws IOException {
        List<String> commands = new ArrayList<>(), classpath = new ArrayList<>(), modulepath = new ArrayList<>();
        commands.add(call.java());
        commands.addAll(call.options());
        List<Path> entries = new ArrayList<>(List.of(classes())), modules = new ArrayList<>();
        if (!call.classpath().isEmpty()) {
            for (String entry : call.classpath().split(Pattern.quote(File.pathSeparator), -1)) {
                entries.add(Path.of(entry).toAbsolutePath());
            }
        }
        if (!call.modulepath().isEmpty()) {
            for (String entry : call.modulepath().split(Pattern.quote(File.pathSeparator), -1)) {
                modules.add(Path.of(entry).toAbsolutePath());
            }
        }
        Path trained = null, cache = null, caches = aot == null ? image : aot;
        Imaged imaged = null;
        if (caches == null) {
            entries.forEach(entry -> classpath.add(entry.toString()));
            modules.forEach(entry -> modulepath.add(entry.toString()));
        } else {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            for (String value : Stream.concat(Stream.of(image == null ? "aot" : "native",
                    call.java(),
                    Runtime.version().toString(),
                    call.main()), call.options().stream()).toList()) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            SequencedMap<String, Path> named = new LinkedHashMap<>();
            for (Path entry : Stream.concat(entries.stream(), modules.stream()).toList()) {
                if (!Files.exists(entry)) {
                    continue;
                }
                String name = (modules.contains(entry) ? "module-" : "") + named.size()
                        + (Files.isDirectory(entry) ? ".jar" : "-" + entry.getFileName());
                named.put(name, entry);
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                if (Files.isDirectory(entry)) {
                    try (Stream<Path> files = Files.walk(entry)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            digest.update(entry.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                            digest.update(Files.readAllBytes(file));
                        }
                    }
                } else if (image == null) {
                    BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class);
                    digest.update((entry
                            + ":" + attributes.size()
                            + ":" + attributes.lastModifiedTime().toMillis()).getBytes(StandardCharsets.UTF_8));
                } else {
                    try (InputStream input = Files.newInputStream(entry)) {
                        input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
                    }
                }
            }
            Path folder = caches.resolve(HexFormat.of().formatHex(digest.digest()).substring(0, 32));
            if (!Files.isDirectory(folder)) {
                Path written = Files.createTempDirectory(Files.createDirectories(caches), "linked");
                for (Map.Entry<String, Path> entry : named.entrySet()) {
                    Path target = written.resolve(entry.getKey());
                    if (Files.isDirectory(entry.getValue())) {
                        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target));
                             Stream<Path> files = Files.walk(entry.getValue())) {
                            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                                out.putNextEntry(new JarEntry(entry.getValue().relativize(file).toString()
                                        .replace(File.separatorChar, '/')));
                                Files.copy(file, out);
                                out.closeEntry();
                            }
                        }
                    } else {
                        try {
                            Files.createLink(target, entry.getValue());
                        } catch (IOException | UnsupportedOperationException _) {
                            Files.copy(entry.getValue(), target);
                        }
                    }
                }
                try {
                    Files.move(written, folder, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException | DirectoryNotEmptyException _) {
                    try (Stream<Path> files = Files.walk(written)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                            Files.delete(file);
                        }
                    }
                }
            }
            named.sequencedKeySet().forEach(name -> (name.startsWith("module-") ? modulepath : classpath)
                    .add(folder.resolve(name).toString()));
            if (image != null) {
                imaged = Imaged.of(call, folder, classpath, modulepath);
                if (imaged.compiler() == null) {
                    List<String> running = new ArrayList<>(List.of(imaged.executable().toString(), Worker.ONCE));
                    running.addAll(imaged.properties());
                    running.add(call.main());
                    running.addAll(call.arguments());
                    return new Once(List.copyOf(running), null, null, imaged);
                }
                commands.add("-agentlib:native-image-agent=config-output-dir=" + folder.resolve("metadata"));
            } else if (Files.isRegularFile(cache = folder.resolve("cache.aot"))) {
                commands.add("-XX:AOTCache=" + cache);
            } else {
                trained = folder.resolve("cache-" + UUID.randomUUID() + ".aot");
                commands.add("-XX:AOTCacheOutput=" + trained);
                commands.add("-XX:+UnlockDiagnosticVMOptions");
                commands.add("-XX:-DisplayVMOutput");
            }
            if (image == null) {
                commands.add("-Xlog:aot=off");
                commands.add("-Xlog:cds=off");
            }
        }
        if (!modulepath.isEmpty()) {
            commands.add("-p");
            commands.add(String.join(File.pathSeparator, modulepath));
        }
        commands.add("-cp");
        commands.add(String.join(File.pathSeparator, classpath));
        commands.add(Worker.class.getName());
        commands.add(Worker.ONCE);
        commands.add(call.main());
        commands.addAll(call.arguments());
        return new Once(List.copyOf(commands), trained, cache, imaged);
    }

    private static synchronized Path classes() throws IOException {
        if (compiled == null) {
            Path copied = ProcessHandler.OfProcess.temporary("jenesis-java-worker");
            String host = Worker.class.getName().replace('.', '/');
            List<String> members = new ArrayList<>(List.of(host));
            try (InputStream input = Worker.class.getResourceAsStream("/" + host + ".class")) {
                if (input == null) {
                    throw new IllegalStateException("Cannot read " + host + ".class of the engine to run a tool's"
                            + " main method in a JVM of its own");
                }
                ClassFile.of().parse(input.readAllBytes()).findAttribute(Attributes.nestMembers()).ifPresent(nest -> {
                    for (ClassEntry member : nest.nestMembers()) {
                        members.add(member.asInternalName());
                    }
                });
            }
            for (String member : members) {
                String name = member + ".class";
                try (InputStream input = Worker.class.getResourceAsStream("/" + name)) {
                    if (input == null) {
                        throw new IllegalStateException("Cannot read " + name + " of the engine to run a tool's main"
                                + " method in a JVM of its own");
                    }
                    Path target = copied.resolve(name);
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target);
                }
            }
            compiled = copied;
        }
        return compiled;
    }

    record Once(List<String> commands, Path trained, Path cache, Imaged imaged) implements Closeable {

        int complete(int code, Path output, Path error) throws IOException {
            if (trained != null) {
                Files.deleteIfExists(trained.resolveSibling(trained.getFileName() + ".config"));
                if (Files.isRegularFile(trained)) {
                    Files.move(trained, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
            return imaged == null ? code : imaged.complete(code, output, error);
        }

        @Override
        public void close() throws IOException {
            if (imaged != null) {
                imaged.close();
            }
        }
    }

    record Imaged(String main,
                  Path folder,
                  List<String> properties,
                  List<String> compiler,
                  Lock lock,
                  FileLock locked) implements Closeable {

        static Imaged of(Call call, Path folder, List<String> classpath, List<String> modulepath) throws IOException {
            Path log = folder.resolve("native.log");
            if (Files.exists(log)) {
                throw new IllegalStateException("native-image could not compile an image of " + call.main() + ", as "
                        + log + " shows: delete that file to compile the image again, or build with"
                        + " jenesis.process.mode=aot");
            }
            List<String> properties = new ArrayList<>(), compiled = new ArrayList<>();
            for (String option : call.options()) {
                String heap = HEAP.keySet().stream().filter(option::startsWith).findFirst().orElse(null);
                if (option.startsWith("-D")) {
                    properties.add(option);
                } else if (heap != null) {
                    compiled.add(HEAP.get(heap) + option.substring(heap.length()));
                } else if (COMPILED.stream().anyMatch(option::startsWith) || option.equals("--enable-preview")) {
                    compiled.add(option);
                } else if (option.equals("-ea") || option.equals("-enableassertions")) {
                    compiled.add("-H:+RuntimeAssertions");
                } else {
                    throw new IllegalStateException("jenesis.process.mode=native cannot carry the JVM option " + option
                            + " of " + call.main() + " into a native image: remove it, or build with"
                            + " jenesis.process.mode=aot");
                }
            }
            if (call.main().equals(Worker.TOOL)) {
                String tool = call.arguments().getFirst();
                compiled.add("--add-modules=" + java.util.spi.ToolProvider.findFirst(tool)
                        .map(provider -> provider.getClass().getModule())
                        .filter(Module::isNamed)
                        .orElseThrow(() -> new IllegalStateException("jenesis.process.mode=native compiles " + tool
                                + " into a native image from the module that provides it, but this JDK provides it from"
                                + " none: build with jenesis.process.mode=aot"))
                        .getName());
            }
            Imaged imaged = new Imaged(call.main(), folder, List.copyOf(properties), null, null, null);
            if (Files.isExecutable(imaged.executable())) {
                return imaged;
            }
            Path java = Path.of(call.java()), compiler = java.resolveSibling(
                    java.getFileName().toString().endsWith(".exe") ? "native-image.cmd" : "native-image");
            if (!Files.isExecutable(compiler)) {
                throw new IllegalStateException("jenesis.process.mode=native compiles a tool with the native-image"
                        + " beside " + java + ", but its JDK holds none: run the build on a GraalVM 25.5 or newer, or"
                        + " build with jenesis.process.mode=aot");
            }
            Lock lock = TRAINING.computeIfAbsent(folder, _ -> new ReentrantLock());
            lock.lock();
            FileLock locked;
            try {
                FileChannel channel = FileChannel.open(folder.resolve("native.lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                try {
                    locked = channel.lock();
                } catch (IOException | RuntimeException e) {
                    channel.close();
                    throw e;
                }
            } catch (IOException | RuntimeException e) {
                lock.unlock();
                throw e;
            }
            if (Files.isExecutable(imaged.executable())) {
                locked.channel().close();
                lock.unlock();
                return imaged;
            }
            List<String> command = new ArrayList<>(List.of(compiler.toString(),
                    "--no-fallback",
                    "-H:-ParseRuntimeOptions",
                    "-H:MissingRegistrationReportingMode=Exit",
                    "--exact-reachability-metadata",
                    "-H:ConfigurationFileDirectories=" + folder.resolve("metadata")));
            command.addAll(compiled);
            if (!modulepath.isEmpty()) {
                command.addAll(List.of("-p", String.join(File.pathSeparator, modulepath)));
            }
            command.addAll(List.of("-cp", String.join(File.pathSeparator, classpath)));
            return new Imaged(call.main(), folder, List.copyOf(properties), List.copyOf(command), lock, locked);
        }

        Path executable() {
            return folder.resolve("native").resolve(File.separatorChar == '\\' ? "tool.exe" : "tool");
        }

        int complete(int code, Path output, Path error) throws IOException {
            if (compiler == null) {
                if (code == MISSING && (Files.readString(output).contains("org.graalvm.nativeimage.Missing")
                        || Files.readString(error).contains("org.graalvm.nativeimage.Missing"))) {
                    throw new IllegalStateException(main + " reached what the tracing agent did not record on its first"
                            + " run, in the native image of " + folder + ": delete that folder to train it again, or"
                            + " build with jenesis.process.mode=aot");
                }
                return code;
            }
            Path staging = Files.createTempDirectory(folder, "native"), log = folder.resolve("native.log");
            List<String> command = new ArrayList<>(compiler);
            command.addAll(List.of("-o", staging.resolve("tool").toString(), Worker.class.getName()));
            COMPILING.acquireUninterruptibly();
            int compiled;
            try {
                Process process = ProcessHandler.OfProcess.start(new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile()));
                compiled = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                COMPILING.release();
            }
            if (compiled != 0) {
                throw new IllegalStateException("native-image failed with exit code " + compiled + " to compile an"
                        + " image of " + main + ", as " + log + " shows: delete that file to compile the image again,"
                        + " or build with jenesis.process.mode=aot");
            }
            Files.move(staging, executable().getParent(), StandardCopyOption.ATOMIC_MOVE);
            Files.delete(log);
            return code;
        }

        @Override
        public void close() throws IOException {
            if (locked != null) {
                try {
                    locked.channel().close();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private static Running start(Key key, List<String> entries) throws IOException {
        Path folder = classes();
        Path linked = Files.createTempDirectory(folder, "classpath");
        StringJoiner classpath = new StringJoiner(File.pathSeparator).add(folder.toString());
        for (int index = 0; index < entries.size(); index++) {
            Path path = Path.of(entries.get(index)).toAbsolutePath();
            if (Files.isRegularFile(path)) {
                Path target = linked.resolve(index + "-" + path.getFileName());
                try {
                    Files.createLink(target, path);
                } catch (IOException | UnsupportedOperationException _) {
                    Files.copy(path, target);
                }
                classpath.add(target.toString());
            } else {
                classpath.add(path.toString());
            }
        }
        List<String> commands = new ArrayList<>();
        commands.add(key.java());
        commands.addAll(key.options());
        commands.add("-cp");
        commands.add(classpath.toString());
        commands.add(Worker.class.getName());
        ProcessBuilder builder = new ProcessBuilder(commands).redirectError(ProcessBuilder.Redirect.DISCARD);
        if (key.directory() != null) {
            builder.directory(key.directory().toFile());
        }
        builder.environment().clear();
        builder.environment().putAll(key.environment());
        builder.environment().putIfAbsent("COLUMNS", "80");
        builder.environment().putIfAbsent("LINES", "24");
        builder.environment().putIfAbsent("TERM", "dumb");
        Process process = ProcessHandler.OfProcess.start(builder);
        return new Running(process,
                new DataOutputStream(new BufferedOutputStream(process.getOutputStream())),
                new DataInputStream(new BufferedInputStream(process.getInputStream())));
    }

    static Closeable scope() {
        synchronized (SHARED) {
            scopes++;
        }
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            List<Shared> ending = new ArrayList<>();
            List<Closeable> nested = new ArrayList<>();
            synchronized (SHARED) {
                if (--scopes == 0) {
                    SHARED.values().removeIf(worker -> !worker.lasting && ending.add(worker));
                    nested.addAll(NESTED);
                    NESTED.clear();
                }
            }
            ending.forEach(worker -> worker.running.process().destroy());
            IOException failure = null;
            for (Closeable closeable : nested) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        };
    }

    static boolean nest(Closeable nested) {
        synchronized (SHARED) {
            if (scopes > 0) {
                NESTED.add(nested);
                return true;
            }
        }
        return false;
    }

    record Call(String java,
                List<String> options,
                String classpath,
                String modulepath,
                String main,
                List<String> arguments) {
    }

    private record Key(String java,
                       List<String> options,
                       List<Object> classpath,
                       SortedMap<String, String> environment,
                       Path directory) {
    }

    private record Running(Process process, DataOutputStream requests, DataInputStream frames) {
    }

    private static final class Shared implements Worker.Calls {

        private final Running running;
        private final AtomicInteger calls = new AtomicInteger();
        private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
        private volatile boolean ended;
        private boolean lasting;

        private Shared(Running running) {
            this.running = running;
            Thread.ofPlatform().daemon().name("jenesis-java-worker-frames").start(() -> {
                try {
                    Worker.dispatch(running.frames(), this);
                } catch (IOException _) {
                }
                ended = true;
                int code;
                try {
                    code = running.process().waitFor();
                } catch (InterruptedException _) {
                    running.process().destroyForcibly();
                    code = 1;
                }
                for (Pending call : pending.values()) {
                    call.done().complete(code);
                }
            });
        }

        private int execute(List<String> request, OutputStream out, OutputStream err) throws IOException {
            int call = calls.incrementAndGet();
            Pending registered = new Pending(out, err, new CompletableFuture<>());
            pending.put(call, registered);
            try {
                if (ended) {
                    return running.process().waitFor();
                }
                synchronized (running.requests()) {
                    Worker.request(running.requests(), call, request);
                }
                return registered.done().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                pending.remove(call);
            }
        }

        @Override
        public OutputStream out(int call) {
            Pending target = pending.get(call);
            return target == null ? OutputStream.nullOutputStream() : target.out();
        }

        @Override
        public OutputStream err(int call) {
            Pending target = pending.get(call);
            return target == null ? OutputStream.nullOutputStream() : target.err();
        }

        @Override
        public boolean exit(int call, int code) {
            Pending target = pending.get(call);
            if (target != null) {
                target.done().complete(code);
            }
            return true;
        }
    }

    private record Pending(OutputStream out, OutputStream err, CompletableFuture<Integer> done) {
    }

    private static final class Lines extends OutputStream {

        private final OutputStream file;
        private final Consumer<String> consumer;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private Lines(OutputStream file, Consumer<String> consumer) {
            this.file = file;
            this.consumer = consumer;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] { (byte) value }, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            file.write(bytes, offset, length);
            if (consumer == null) {
                return;
            }
            for (int index = offset; index < offset + length; index++) {
                if (bytes[index] == '\n') {
                    consumer.accept(line.toString(StandardCharsets.UTF_8));
                    line.reset();
                } else if (bytes[index] != '\r') {
                    line.write(bytes[index]);
                }
            }
        }

        @Override
        public void close() {
            if (consumer != null && line.size() > 0) {
                consumer.accept(line.toString(StandardCharsets.UTF_8));
            }
        }
    }
}
