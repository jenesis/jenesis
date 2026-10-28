package build.jenesis.daemon;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Make;
import build.jenesis.SequencedProperties;

public final class DaemonClient {

    private static final Set<String> VOLATILE = Set.of("_",
            "PWD",
            "OLDPWD",
            "SHLVL",
            "COLUMNS",
            "LINES",
            "WINDOWID",
            "TERM_SESSION_ID",
            "SHELL_SESSION_ID");
    private static final String LOCK = "daemon.lock", LOG = "daemon.log";

    private final Path root;
    private final List<String> path;
    private final String mainClass;
    private final SequencedMap<String, String> supplied;
    private final String options;
    private final long idle;
    private final int limit;
    private final boolean shared;
    private final Path folder;
    private final SequencedMap<String, String> outputs;

    public DaemonClient(Path root, List<String> path, String mainClass, SequencedMap<String, String> supplied) {
        Environment environment = new Environment(Make.keys(effective(supplied)));
        int limit = environment.number("daemon.limit", 3);
        if (limit < 1) {
            throw new IllegalArgumentException("jenesis.daemon.limit is " + limit
                    + ", but names how many build daemons may run at once: 1 or more");
        }
        String location = environment.value("daemon.folder");
        this(root,
                path,
                mainClass,
                supplied,
                environment.value("daemon.options", ""),
                environment.number("daemon.idle", 10_800L),
                limit,
                environment.flag("daemon.shared", true),
                location == null
                        ? Path.of(System.getProperty("user.home"), ".jenesis", "daemons")
                        : Path.of(location).toAbsolutePath().normalize(),
                null);
    }

    private DaemonClient(Path root,
                         List<String> path,
                         String mainClass,
                         SequencedMap<String, String> supplied,
                         String options,
                         long idle,
                         int limit,
                         boolean shared,
                         Path folder,
                         SequencedMap<String, String> outputs) {
        this.root = root.toAbsolutePath().normalize();
        this.path = path;
        this.mainClass = mainClass;
        this.supplied = supplied;
        this.options = options;
        this.idle = idle;
        this.limit = limit;
        this.shared = shared;
        this.folder = folder;
        this.outputs = outputs;
    }

    public DaemonClient options(String options) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, limit, shared, folder, outputs);
    }

    public DaemonClient idle(long idle) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, limit, shared, folder, outputs);
    }

    public DaemonClient limit(int limit) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, limit, shared, folder, outputs);
    }

    public DaemonClient shared(boolean shared) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, limit, shared, folder, outputs);
    }

    public DaemonClient folder(Path folder) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, limit, shared, folder, outputs);
    }

    public DaemonClient outputs(SequencedMap<String, String> outputs) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, limit, shared, folder, outputs);
    }

    private static SequencedMap<String, String> effective(SequencedMap<String, String> supplied) {
        SequencedMap<String, String> effective = new LinkedHashMap<>(supplied);
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.")) {
                effective.put(name, System.getProperty(name));
            }
        }
        return effective;
    }

    public static int doDispatch(Path root,
                                 List<String> path,
                                 String mainClass,
                                 String seed,
                                 SequencedMap<String, String> supplied,
                                 SequencedMap<String, String> outputs,
                                 String... selectors) throws IOException, InterruptedException {
        return new DaemonClient(root, path, mainClass, supplied).outputs(outputs).dispatch(seed, selectors);
    }

    public int dispatch(String seed, String... selectors) throws IOException, InterruptedException {
        if (selectors.length == 1 && selectors[0].equals("--stop")) {
            for (Path instance : running()) {
                stop(instance, true);
            }
            return 0;
        }
        String java = ProcessHandle.current().info().command().orElseThrow(() -> new IllegalStateException(
                "Cannot resolve the running Java executable to start a build daemon"
                        + " - set -Djenesis.make.mode=compiled to build without one"));
        List<String> started = new ArrayList<>();
        if (options.isBlank()) {
            for (String argument : ProcessHandle.current().info().arguments().orElse(new String[0])) {
                if (argument.startsWith("-D") && !argument.startsWith("-Djenesis.")
                        || argument.startsWith("-X")
                        || argument.equals("-ea")) {
                    started.add(argument);
                }
            }
        } else {
            started.addAll(List.of(options.trim().split("\\s+")));
        }
        SequencedMap<String, String> environment = new TreeMap<>(System.getenv())
                .entrySet()
                .stream()
                .filter(entry -> !VOLATILE.contains(entry.getKey()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
        String fingerprint = fingerprint(seed.isBlank()
                ? Stream.of(path.getLast().split(File.pathSeparator)).map(Path::of).toList()
                : List.of(), seed + (shared ? "" : "\n" + root), java, started, environment);
        List<String> engine = engine(seed);
        Path lock = root.resolve(".jenesis").resolve(LOCK);
        Files.createDirectories(lock.getParent());
        try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock _ = locked(channel)) {
            for (int attempt = 0; attempt < 3; attempt++) {
                for (Path instance : instances(fingerprint)) {
                    Integer code = request(instance, fingerprint, selectors);
                    if (code != null) {
                        return code;
                    }
                }
                start(fingerprint, java, started, engine);
            }
        }
        throw new IllegalStateException("No build daemon under " + folder + " accepted the build three times"
                + " - see the daemon.log of the folders there, or set -Djenesis.make.mode=compiled to build"
                + " without one");
    }

    private FileLock locked(FileChannel channel) throws IOException {
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException _) {
            lock = null;
        }
        if (lock == null) {
            throw new IllegalStateException("Another build process is already building "
                    + root.resolve(effective(supplied).getOrDefault("jenesis.project.target", "target"))
                            .toAbsolutePath()
                            .normalize()
                    + ": concurrent builds over one target folder interfere with each other's staged steps");
        }
        return lock;
    }

    private static String fingerprint(Collection<Path> files,
                                      String seed,
                                      String java,
                                      List<String> options,
                                      SequencedMap<String, String> environment) throws IOException {
        HashDigestFunction function = new HashDigestFunction("SHA-256");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update(seed.getBytes(StandardCharsets.UTF_8));
        digest.update(Runtime.version().toString().getBytes(StandardCharsets.UTF_8));
        digest.update(java.getBytes(StandardCharsets.UTF_8));
        for (String option : options) {
            digest.update(option.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        environment.forEach((name, value) -> {
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        });
        for (Path file : new TreeSet<>(files)) {
            digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
            if (Files.isDirectory(file)) {
                try (Stream<Path> walk = Files.walk(file, FileVisitOption.FOLLOW_LINKS)) {
                    for (Path nested : walk.filter(Files::isRegularFile).sorted().toList()) {
                        digest.update(file.relativize(nested).toString().getBytes(StandardCharsets.UTF_8));
                        digest.update(function.hash(nested));
                    }
                }
            } else if (Files.isRegularFile(file)) {
                digest.update(function.hash(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private List<String> engine(String seed) throws IOException {
        if (seed.isBlank() || !path.getFirst().equals("-cp") || !Files.isDirectory(Path.of(path.getLast()))) {
            return path;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path copy = folder.resolve("engine-" + HexFormat.of()
                .formatHex(digest.digest(seed.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 16));
        if (!Files.isDirectory(copy)) {
            Files.createDirectories(folder);
            Path source = Path.of(path.getLast()), staged = Files.createTempDirectory(folder, "staged");
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path target = staged.resolve(source.relativize(file).toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
            }
            try {
                Files.move(staged, copy, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                delete(staged);
                if (!Files.isDirectory(copy)) {
                    throw e;
                }
            }
        }
        Files.setLastModifiedTime(copy, FileTime.from(Instant.now()));
        return List.of("-cp", copy.toString());
    }

    private List<Path> instances(String fingerprint) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> instances = Files.list(folder)) {
            return instances.filter(instance -> instance.getFileName().toString().startsWith(prefix(fingerprint)))
                    .filter(instance -> Files.isRegularFile(instance.resolve(Worker.PORT)))
                    .sorted()
                    .toList();
        }
    }

    private List<Path> running() throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        Instant expired = Instant.now().minusSeconds(idle + 3_600L);
        List<Path> running = new ArrayList<>();
        try (Stream<Path> instances = Files.list(folder)) {
            for (Path instance : instances.filter(Files::isDirectory).toList()) {
                String name = instance.getFileName().toString();
                Path port = instance.resolve(Worker.PORT);
                if (!name.startsWith("daemon-") && !name.startsWith("engine-") && !name.startsWith("staged")) {
                    continue;
                }
                if (Files.isRegularFile(port)) {
                    running.add(instance);
                } else if (Files.getLastModifiedTime(instance).toInstant().isBefore(expired)) {
                    try {
                        delete(instance);
                    } catch (IOException _) {
                    }
                }
            }
        }
        running.sort(Comparator.comparing(instance -> {
            try {
                return Files.getLastModifiedTime(instance.resolve(Worker.PORT));
            } catch (IOException _) {
                return FileTime.fromMillis(0);
            }
        }));
        return running;
    }

    private static String prefix(String fingerprint) {
        return "daemon-" + fingerprint.substring(0, 16) + "-";
    }

    private Integer request(Path instance, String fingerprint, String... selectors)
            throws IOException, InterruptedException {
        if (outputs != null) {
            outputs.clear();
        }
        Socket socket = connected(instance);
        if (socket == null) {
            return null;
        }
        Path produced = outputs == null ? null : Files.createTempFile(root.resolve(".jenesis"), "daemon", ".outputs");
        try (socket;
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out.writeUTF(Files.readString(instance.resolve(Worker.TOKEN)).trim());
            out.writeUTF(fingerprint);
            out.writeBoolean(false);
            List<String> request = new ArrayList<>(List.of(DaemonBuild.class.getName(),
                    root.toString(),
                    mainClass,
                    produced == null ? "" : produced.toString()));
            effective(supplied).forEach((key, value) -> request.add("-D" + key + "=" + value));
            request.add("--");
            request.addAll(List.of(selectors));
            Worker.request(out, 0, request);
            byte status;
            try {
                status = in.readByte();
            } catch (EOFException _) {
                return null;
            }
            switch (status) {
                case Worker.REJECTED -> throw new IllegalStateException("The build daemon under " + instance
                        + " rejected this client's token - delete that folder, then build again");
                case Worker.STALE, Worker.BUSY -> {
                    return null;
                }
                case Worker.ACCEPTED -> {
                }
                default -> throw new IllegalStateException("The build daemon answered with an unknown status: " + status);
            }
            int code;
            try {
                code = Worker.receive(in, new Forward(System.out), new Forward(System.err));
            } catch (EOFException e) {
                throw new IllegalStateException("The build daemon died while the build was running"
                        + " - its output above is incomplete and the build did not finish, see "
                        + instance.resolve(LOG), e);
            }
            if (produced != null && code == 0 && Files.size(produced) > 0) {
                SequencedProperties.ofFiles(produced).forEachProperty(outputs::put);
            }
            return code;
        } finally {
            if (produced != null) {
                Files.deleteIfExists(produced);
            }
        }
    }

    private void stop(Path instance, boolean await) throws IOException, InterruptedException {
        Socket socket = connected(instance);
        if (socket == null) {
            return;
        }
        try (socket;
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out.writeUTF(Files.readString(instance.resolve(Worker.TOKEN)).trim());
            out.writeUTF("");
            out.writeBoolean(true);
            out.flush();
            if (in.readByte() != Worker.ACCEPTED) {
                return;
            }
            Worker.receive(in, OutputStream.nullOutputStream(), OutputStream.nullOutputStream());
            long pid = in.readLong();
            Optional<ProcessHandle> daemon = ProcessHandle.of(pid);
            if (await && daemon.isPresent()) {
                try {
                    daemon.get().onExit().get(30, TimeUnit.SECONDS);
                } catch (ExecutionException | TimeoutException e) {
                    throw new IllegalStateException("The build daemon under " + instance + " acknowledged the stop"
                            + " but its process " + pid + " did not end within 30 seconds - end it, and see "
                            + instance.resolve(LOG), e);
                }
            }
        } catch (EOFException _) {
        }
    }

    private static Socket connected(Path instance) throws IOException {
        Path port = instance.resolve(Worker.PORT), secret = instance.resolve(Worker.TOKEN);
        if (!Files.isRegularFile(port) || !Files.isRegularFile(secret)) {
            return null;
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    Integer.parseInt(Files.readString(port).trim())), 2_000);
            return socket;
        } catch (IOException | NumberFormatException _) {
            socket.close();
            Files.deleteIfExists(port);
            Files.deleteIfExists(secret);
            return null;
        }
    }

    private void start(String fingerprint, String java, List<String> started, List<String> engine)
            throws IOException, InterruptedException {
        List<Path> running = running();
        for (int index = 0; index <= running.size() - limit; index++) {
            stop(running.get(index), false);
        }
        Files.createDirectories(folder);
        Path instance = Files.createTempDirectory(folder, prefix(fingerprint)), port = instance.resolve(Worker.PORT);
        List<String> command = new ArrayList<>();
        command.add(java);
        command.addAll(started);
        command.add("-Djenesis.make.mode=compiled");
        command.addAll(engine);
        if (engine.getFirst().equals("-p")) {
            command.addAll(List.of("-m", Worker.class.getModule().getName() + "/" + Worker.class.getName()));
        } else {
            command.add(Worker.class.getName());
        }
        command.addAll(List.of("listen", instance.toString(), fingerprint, Long.toString(idle)));
        new ProcessBuilder(command)
                .directory(instance.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(instance.resolve(LOG).toFile()))
                .start();
        for (int attempt = 0; attempt < 400 && !Files.isRegularFile(port); attempt++) {
            Thread.sleep(25);
        }
        if (!Files.isRegularFile(port)) {
            throw new IllegalStateException("A build daemon was started but never announced a port in " + port
                    + " - see " + instance.resolve(LOG));
        }
    }

    private static void delete(Path folder) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static final class Forward extends OutputStream {

        private final PrintStream stream;

        private Forward(PrintStream stream) {
            this.stream = stream;
        }

        @Override
        public void write(int value) {
            stream.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            stream.write(bytes, offset, length);
            stream.flush();
        }
    }
}
