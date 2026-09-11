package build.jenesis.daemon;

import module java.base;
import build.jenesis.HashDigestFunction;

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

    private final Path root;
    private final Path folder;
    private final List<String> path;
    private final String mainClass;
    private final String options;
    private final SequencedMap<String, String> outputs;

    public DaemonClient(Path root, List<String> path, String mainClass) {
        this(root, path, mainClass, System.getProperty("jenesis.daemon.options", "-Xmx2g"), null);
    }

    private DaemonClient(Path root,
                         List<String> path,
                         String mainClass,
                         String options,
                         SequencedMap<String, String> outputs) {
        this.root = root.toAbsolutePath().normalize();
        this.path = path;
        this.mainClass = mainClass;
        this.options = options;
        this.outputs = outputs;
        folder = this.root.resolve(".jenesis");
    }

    public DaemonClient options(String options) {
        return new DaemonClient(root, path, mainClass, options, outputs);
    }

    public DaemonClient outputs(SequencedMap<String, String> outputs) {
        return new DaemonClient(root, path, mainClass, options, outputs);
    }

    public static int doDispatch(Path root,
                                 List<String> path,
                                 String mainClass,
                                 String seed,
                                 SequencedMap<String, String> outputs,
                                 String... selectors) throws IOException, InterruptedException {
        return new DaemonClient(root, path, mainClass).outputs(outputs).dispatch(seed, selectors);
    }

    public int dispatch(String seed, String... selectors) throws IOException, InterruptedException {
        boolean stop = selectors.length == 1 && selectors[0].equals("--stop");
        String[] arguments = stop ? new String[0] : selectors;
        SequencedMap<String, String> environment = new TreeMap<>(System.getenv())
                .entrySet()
                .stream()
                .filter(entry -> !VOLATILE.contains(entry.getKey()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
        for (String argument : ProcessHandle.current().info().arguments().orElse(new String[0])) {
            if (argument.startsWith("-D") && !argument.startsWith("-Djenesis.")
                    || argument.startsWith("-X")
                    || argument.equals("-ea")) {
                environment.put(argument, "");
            }
        }
        String fingerprint = fingerprint(seed.isBlank()
                ? Stream.of(path.getLast().split(File.pathSeparator)).map(Path::of).toList()
                : List.of(), seed, environment);
        for (int attempt = 0; attempt < 2; attempt++) {
            Integer code = request(fingerprint, stop, arguments);
            if (code != null) {
                return code;
            }
            if (stop) {
                return 0;
            }
            start(fingerprint);
        }
        throw new IllegalStateException("The build daemon under " + folder + " refused two consecutive requests"
                + " - see daemon.log there, or set -Djenesis.make.daemon=false to build without it");
    }

    private static String fingerprint(Collection<Path> files, String seed, SequencedMap<String, String> environment)
            throws IOException {
        HashDigestFunction function = new HashDigestFunction("SHA-256");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update(seed.getBytes(StandardCharsets.UTF_8));
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

    private Integer request(String fingerprint, boolean stop, String... selectors) throws IOException {
        if (outputs != null) {
            outputs.clear();
        }
        Path port = folder.resolve("daemon.port"), secret = folder.resolve("daemon.token");
        if (!Files.isRegularFile(port) || !Files.isRegularFile(secret)) {
            return null;
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    Integer.parseInt(Files.readString(port).trim())), 2_000);
        } catch (IOException _) {
            socket.close();
            return null;
        }
        try (socket;
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out.writeUTF(Files.readString(secret).trim());
            out.writeUTF(fingerprint);
            out.writeUTF(mainClass);
            out.writeBoolean(stop);
            SequencedMap<String, String> properties = new LinkedHashMap<>();
            for (String name : System.getProperties().stringPropertyNames()) {
                if (name.startsWith("jenesis.")) {
                    properties.put(name, System.getProperty(name));
                }
            }
            out.writeInt(properties.size());
            for (Map.Entry<String, String> property : properties.entrySet()) {
                out.writeUTF(property.getKey());
                out.writeUTF(property.getValue());
            }
            out.writeInt(selectors.length);
            for (String selector : selectors) {
                out.writeUTF(selector);
            }
            out.flush();
            return receive(in);
        }
    }

    private Integer receive(DataInputStream in) throws IOException {
        boolean started = false;
        while (true) {
            int kind;
            try {
                kind = in.readByte();
            } catch (EOFException e) {
                if (started) {
                    throw new IllegalStateException("The build daemon died while the build was running"
                            + " - its output above is incomplete and the build did not finish", e);
                }
                return null;
            }
            switch (kind) {
                case 0 -> {
                    return in.readInt();
                }
                case 1, 2 -> {
                    byte[] bytes = new byte[in.readInt()];
                    in.readFully(bytes);
                    started = true;
                    (kind == 1 ? System.out : System.err).write(bytes);
                    (kind == 1 ? System.out : System.err).flush();
                }
                case 3 -> throw new IllegalStateException("The build daemon rejected this client's token"
                        + " - delete .jenesis/daemon.token and .jenesis/daemon.port, then build again");
                case 4 -> {
                    return null;
                }
                case 5 -> throw new IllegalStateException(in.readUTF());
                case 6 -> {
                    for (int index = in.readInt(); index > 0; index--) {
                        String identity = in.readUTF(), output = in.readUTF();
                        if (outputs != null) {
                            outputs.put(identity, output);
                        }
                    }
                }
                default -> throw new IllegalStateException("The build daemon sent an unknown frame: " + kind);
            }
        }
    }

    private void start(String fingerprint) throws IOException, InterruptedException {
        Path port = folder.resolve("daemon.port");
        Files.createDirectories(folder);
        if (Files.isRegularFile(port)) {
            request(fingerprint, true);
        }
        Files.deleteIfExists(port);
        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElseThrow(() -> new IllegalStateException(
                "Cannot resolve the running Java executable to start a build daemon"
                        + " - set -Djenesis.make.daemon=false to build without one")));
        if (!options.isBlank()) {
            command.addAll(List.of(options.trim().split("\\s+")));
        }
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.daemon.")) {
                command.add("-D" + name + "=" + System.getProperty(name));
            }
        }
        command.addAll(path);
        if (path.getFirst().equals("-p")) {
            command.addAll(List.of("-m",
                    DaemonServer.class.getModule().getName() + "/" + DaemonServer.class.getName()));
        } else {
            command.add(DaemonServer.class.getName());
        }
        command.add(root.toString());
        command.add(fingerprint);
        new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(folder.resolve("daemon.log").toFile()))
                .start();
        for (int attempt = 0; attempt < 400 && !Files.isRegularFile(port); attempt++) {
            Thread.sleep(25);
        }
        if (!Files.isRegularFile(port)) {
            throw new IllegalStateException("A build daemon was started but never announced a port in " + port
                    + " - see " + folder.resolve("daemon.log"));
        }
    }
}
