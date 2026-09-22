package build.jenesis.daemon;

import module java.base;
import build.jenesis.Make;
import build.jenesis.Output;
import build.jenesis.Project;
import build.jenesis.SequencedProperties;
import static build.jenesis.SequencedProperties.SYSTEM;

public final class DaemonServer {

    private final Path root;
    private final String digest;
    private final Duration idle;
    private final Semaphore collect = new Semaphore(0);
    private final ReentrantLock building = new ReentrantLock();
    private volatile ServerSocket retiring;

    public DaemonServer(Path root, String digest) {
        this(root, digest, Duration.ofSeconds(SequencedProperties.number(SYSTEM, "daemon.idle", 10_800L)));
    }

    private DaemonServer(Path root, String digest, Duration idle) {
        this.root = root.toAbsolutePath().normalize();
        this.digest = digest;
        this.idle = idle;
    }

    public DaemonServer idle(Duration idle) {
        return new DaemonServer(root, digest, idle);
    }

    public static void main(String... arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected a project root and an engine fingerprint, but got "
                    + List.of(arguments) + " - this class is started by build/jenesis/Make.java, not by hand");
        }
        new DaemonServer(Path.of(arguments[0]), arguments[1]).serve();
    }

    private void serve() throws IOException {
        Path folder = root.resolve(".jenesis");
        Files.createDirectories(folder);
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            socket.setSoTimeout(Math.toIntExact(idle.toMillis()));
            Thread.ofPlatform().daemon().name("jenesis-daemon-collector").start(() -> {
                while (true) {
                    try {
                        collect.acquire();
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    collect.drainPermits();
                    System.gc();
                }
            });
            Path port = folder.resolve("daemon.port"), secret = folder.resolve("daemon.token");
            write(secret, HexFormat.of().formatHex(token));
            write(port, Integer.toString(socket.getLocalPort()));
            try {
                while (retiring == null) {
                    Socket accepted;
                    try {
                        accepted = socket.accept();
                    } catch (SocketTimeoutException _) {
                        if (building.isLocked()) {
                            continue;
                        }
                        break;
                    } catch (SocketException _) {
                        break;
                    }
                    Thread.ofPlatform().start(() -> {
                        try {
                            exchange(socket, accepted, token);
                        } catch (IOException _) {
                            return;
                        }
                        collect.release();
                    });
                }
            } finally {
                discard(port, Integer.toString(socket.getLocalPort()));
                discard(secret, HexFormat.of().formatHex(token));
            }
        }
    }

    private static void discard(Path file, String content) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file).equals(content)) {
            Files.deleteIfExists(file);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content);
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            return;
        } catch (UnsupportedOperationException _) {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl != null) {
                acl.setAcl(List.of(AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(file))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build()));
                return;
            }
        }
        throw new IOException("Cannot restrict " + file + " to its owner, refusing to expose the daemon"
                + " - this filesystem supports neither POSIX permissions nor access control lists");
    }

    private void exchange(ServerSocket server, Socket socket, byte[] token) throws IOException {
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(in.readUTF()), token)) {
                out.writeByte(3);
                out.flush();
                return;
            }
            if (!in.readUTF().equals(digest)) {
                out.writeByte(4);
                out.flush();
                retire(server);
                return;
            }
            String mainClass = in.readUTF();
            if (in.readBoolean()) {
                out.writeByte(0);
                out.writeInt(0);
                out.flush();
                retire(server);
                return;
            }
            SequencedMap<String, String> properties = new LinkedHashMap<>();
            for (int index = in.readInt(); index > 0; index--) {
                properties.put(in.readUTF(), in.readUTF());
            }
            String[] selectors = new String[in.readInt()];
            for (int index = 0; index < selectors.length; index++) {
                selectors[index] = in.readUTF();
            }
            if (!building.tryLock()) {
                out.writeByte(5);
                out.writeUTF("Another build process is already building "
                        + root.resolve(properties.getOrDefault("jenesis.project.target", "target"))
                                .toAbsolutePath()
                                .normalize()
                        + ": concurrent builds over one target folder interfere with each other's staged steps");
                out.flush();
                return;
            }
            int code;
            SequencedMap<String, String> outputs = new LinkedHashMap<>();
            try {
                code = build(out, mainClass, properties, outputs, selectors);
            } finally {
                building.unlock();
            }
            out.writeByte(6);
            out.writeInt(outputs.size());
            for (Map.Entry<String, String> output : outputs.entrySet()) {
                out.writeUTF(output.getKey());
                out.writeUTF(output.getValue());
            }
            out.writeByte(0);
            out.writeInt(code);
            out.flush();
        }
    }

    private void retire(ServerSocket server) throws IOException {
        retiring = server;
        server.close();
    }

    private int build(DataOutputStream out,
                      String mainClass,
                      SequencedMap<String, String> properties,
                      SequencedMap<String, String> outputs,
                      String... selectors) throws IOException {
        PrintStream systemOut = System.out, systemErr = System.err;
        try {
            Function<String, String> requested = requested(properties);
            System.setOut(new PrintStream(new Frames(out, 1), true));
            System.setErr(new PrintStream(new Frames(out, 2), true));
            try {
                if (SequencedProperties.flag(requested, "project.docker")) {
                    throw new IllegalStateException("A dockerized build cannot run in the daemon, because it replaces"
                            + " the running process - unset jenesis.project.docker or run build/jenesis/Make.java");
                }
                Make.Settings settings = Make.settings(root, requested);
                Function<String, String> keys = settings.keys();
                SequencedSet<Path> profiles = settings.profiles();
                if (!mainClass.equals(Project.class.getName())) {
                    return Project.run(keys, new Output(), mainClass, root, profiles, selectors);
                }
                SequencedMap<String, Path> produced = Project.perform(keys, new Output(), root, profiles, selectors);
                if (produced == null) {
                    return 1;
                }
                produced.forEach((identity, output) -> outputs.put(identity,
                        root.relativize(output.toAbsolutePath().normalize()).toString()));
                return 0;
            } finally {
                System.out.flush();
                System.err.flush();
            }
        } finally {
            System.setOut(systemOut);
            System.setErr(systemErr);
        }
    }

    private Function<String, String> requested(SequencedMap<String, String> properties) {
        Map<String, String> anchored = new LinkedHashMap<>(properties);
        anchored.put("jenesis.make.root", root.toString());
        anchored.put("jenesis.make.daemon", "false");
        anchored.putIfAbsent("jenesis.project.target", root.resolve("target").toString());
        anchored.putIfAbsent("jenesis.project.artifacts",
                root.resolve(".jenesis").resolve("artifacts").toString());
        return key -> anchored.get("jenesis." + key);
    }

    private static final class Frames extends OutputStream {

        private final DataOutputStream out;
        private final int kind;

        private Frames(DataOutputStream out, int kind) {
            this.out = out;
            this.kind = kind;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] { (byte) value }, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return;
            }
            synchronized (out) {
                out.writeByte(kind);
                out.writeInt(length);
                out.write(bytes, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (out) {
                out.flush();
            }
        }
    }
}
