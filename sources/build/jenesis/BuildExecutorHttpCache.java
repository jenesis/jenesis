package build.jenesis;

import module java.base;
import build.jenesis.Environment.Setting;

public final class BuildExecutorHttpCache implements BuildExecutorCache {

    public static final String PROJECT = "Jenesis-Cache-Project";
    private static final String FORMAT = "jenesis-cache/2", MANIFEST = "manifest";
    private static final int LIMIT = 64 << 20;
    private static final Set<String> ARCHIVES = Set.of("jar", "zip", "jmod", "war", "ear", "gz", "tgz", "xz", "zst", "png", "jpg");

    private final URI uri;
    private final BuildExecutorFileCache local;
    private final String token;
    private final String project;
    private final String algorithm;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final boolean read;
    private final boolean write;
    private final boolean insecure;
    private final long inline;
    private final ConcurrentMap<String, CompletableFuture<Boolean>> blobs = new ConcurrentHashMap<>();

    public BuildExecutorHttpCache(URI uri) {
        this(uri,
                new BuildExecutorFileCache(Path.of(System.getProperty("user.home"), ".jenesis", "cache")),
                null,
                null,
                "SHA-256",
                Duration.parse("PT1S"),
                Duration.parse("PT10S"),
                true,
                true,
                false,
                65_536);
    }

    public static BuildExecutorHttpCache ofEnvironment(Environment environment, URI uri) {
        BuildExecutorHttpCache cache = new BuildExecutorHttpCache(uri);
        Path folder = environment.path("cache.local", Setting.MACHINE);
        BuildExecutorFileCache local = folder == null ? cache.local : new BuildExecutorFileCache(folder);
        cache = cache.local(local.size() > 0 ? local : local.size(10L << 30));
        if (environment.getProperty("cache.key", Setting.MACHINE) != null
                || System.getenv("JENESIS_CACHE_KEY") != null) {
            throw new IllegalArgumentException("jenesis.cache.key and JENESIS_CACHE_KEY are named jenesis.cache.token"
                    + " and JENESIS_CACHE_TOKEN now: rename the one that is set, as the cache server would be sent"
                    + " no credential otherwise");
        }
        String token = environment.getProperty("cache.token", System.getenv("JENESIS_CACHE_TOKEN"), Setting.MACHINE);
        if (token != null) {
            cache = cache.token(token);
        }
        String project = environment.getProperty("cache.project",
                System.getenv("JENESIS_CACHE_PROJECT"),
                Setting.PROJECT);
        if (project != null) {
            cache = cache.project(project);
        }
        String connect = environment.getProperty("cache.connect", Setting.PROJECT);
        if (connect != null) {
            cache = cache.connectTimeout(Duration.parse(connect));
        }
        String read = environment.getProperty("cache.read", Setting.PROJECT);
        if (read != null) {
            cache = cache.readTimeout(Duration.parse(read));
        }
        if (!environment.flag("cache.write", true, Setting.PROJECT)) {
            cache = cache.write(false);
        }
        long inline = environment.number("cache.inline", cache.inline, Setting.PROJECT);
        if (inline < 0) {
            throw new IllegalArgumentException("jenesis.cache.inline is " + inline
                    + ", but names the size in bytes from which a file is stored apart from its step: 0 or more");
        }
        Boolean insecure = environment.flagOrNull("cache.insecure", Setting.MACHINE);
        return insecure == null ? cache.inline(inline) : cache.inline(inline).insecure(insecure);
    }

    private BuildExecutorHttpCache(URI uri,
                                   BuildExecutorFileCache local,
                                   String token,
                                   String project,
                                   String algorithm,
                                   Duration connectTimeout,
                                   Duration readTimeout,
                                   boolean read,
                                   boolean write,
                                   boolean insecure,
                                   long inline) {
        this.uri = uri;
        this.local = local;
        this.token = token;
        this.project = project;
        this.algorithm = algorithm;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.read = read;
        this.write = write;
        this.insecure = insecure;
        this.inline = inline;
    }

    public BuildExecutorHttpCache insecure(boolean insecure) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache inline(long inline) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache local(BuildExecutorFileCache local) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache token(String token) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache project(String project) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache algorithm(String algorithm) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache connectTimeout(Duration connectTimeout) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache read(boolean read) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache write(boolean write) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    public BuildExecutorHttpCache readTimeout(Duration readTimeout) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure, inline);
    }

    @Override
    public Optional<Map<Path, byte[]>> fetch(Executor executor,
                                             String identity,
                                             byte[] step,
                                             SequencedMap<String, Map<Path, byte[]>> inputs,
                                             SequencedMap<String, Path> folders,
                                             Path target,
                                             String digest) throws IOException {
        if (!read) {
            return Optional.empty();
        }
        List<String> lines = new ArrayList<>();
        Set<String> inputted = new LinkedHashSet<>(), separate = new LinkedHashSet<>();
        HttpURLConnection connection = connect("GET", address(step, inputs));
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }
            try (ZipInputStream zip = new ZipInputStream(connection.getInputStream())) {
                ZipEntry first = zip.getNextEntry();
                if (first == null || !first.getName().equals(MANIFEST)) {
                    return Optional.empty();
                }
                byte[] manifest = zip.readNBytes(LIMIT + 1);
                if (manifest.length > LIMIT) {
                    return Optional.empty();
                }
                Set<String> included = new HashSet<>();
                Iterator<String> listed = new String(manifest, StandardCharsets.UTF_8).lines().iterator();
                if (!listed.hasNext() || !listed.next().equals(FORMAT + " " + digest)) {
                    return Optional.empty();
                }
                while (listed.hasNext()) {
                    String[] entry = listed.next().split(" ", 4);
                    if (entry.length < 4) {
                        return Optional.empty();
                    }
                    switch (entry[0]) {
                        case "d" -> lines.add(BuildExecutorFileCache.DIRECTORY + " 0 " + entry[3]);
                        case "f", "i", "b" -> {
                            if (!isHex(entry[1])) {
                                return Optional.empty();
                            }
                            (entry[0].equals("f") ? included : entry[0].equals("i") ? inputted : separate).add(entry[1]);
                            lines.add(entry[1] + " " + entry[2] + " " + entry[3]);
                        }
                        default -> {
                            return Optional.empty();
                        }
                    }
                }
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!included.remove(entry.getName()) || !local.admit(digest, entry.getName(), zip)) {
                        return Optional.empty();
                    }
                }
                if (!included.isEmpty()) {
                    return Optional.empty();
                }
            }
        } catch (IOException | RuntimeException _) {
            return Optional.empty();
        } finally {
            connection.disconnect();
        }
        try {
            Map<String, Path> available = new HashMap<>();
            for (String hex : inputted) {
                if (Files.isRegularFile(local.file(digest, hex))) {
                    continue;
                }
                if (available.isEmpty()) {
                    inputs.forEach((argument, files) -> files.forEach((file, checksum) -> available.putIfAbsent(
                            HexFormat.of().formatHex(checksum),
                            folders.get(argument).resolve(file))));
                }
                Path file = available.get(hex);
                if (file == null) {
                    return Optional.empty();
                }
                try (InputStream stream = Files.newInputStream(file)) {
                    if (!local.admit(digest, hex, stream)) {
                        return Optional.empty();
                    }
                }
            }
            for (String hex : separate) {
                String blob = address(digest, hex);
                if (Files.isRegularFile(local.file(digest, hex))) {
                    CompletableFuture<Boolean> created = new CompletableFuture<>();
                    if (blobs.putIfAbsent(hex, created) == null) {
                        try {
                            executor.execute(() -> {
                                boolean held = false;
                                try {
                                    held = send("HEAD", blob, null) == HttpURLConnection.HTTP_OK;
                                } catch (IOException | RuntimeException _) {
                                } finally {
                                    if (!held) {
                                        blobs.remove(hex, created);
                                    }
                                    created.complete(held);
                                }
                            });
                        } catch (RejectedExecutionException _) {
                            blobs.remove(hex, created);
                            created.complete(false);
                        }
                    }
                    continue;
                }
                HttpURLConnection download = connect("GET", blob);
                try {
                    if (download.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        blobs.remove(hex);
                        return Optional.empty();
                    }
                    try (InputStream stream = download.getInputStream()) {
                        if (!local.admit(digest, hex, stream)) {
                            return Optional.empty();
                        }
                    }
                    blobs.putIfAbsent(hex, CompletableFuture.completedFuture(true));
                } finally {
                    download.disconnect();
                }
            }
        } catch (IOException _) {
            return Optional.empty();
        }
        local.trim(executor, digest);
        return local.restore(executor, lines, target, digest);
    }

    @Override
    public void store(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      Path output,
                      String digest,
                      Map<Path, byte[]> checksums) throws IOException {
        if (!write) {
            return;
        }
        Path temporary = null;
        try {
            List<String> lines = local.persist(output, digest, checksums);
            local.trim(executor, digest);
            Set<String> inputted = new HashSet<>();
            inputs.values().forEach(files -> files.values().forEach(checksum -> inputted.add(HexFormat.of().formatHex(checksum))));
            StringBuilder manifest = new StringBuilder(FORMAT).append(' ').append(digest).append('\n');
            SequencedMap<String, String> included = new LinkedHashMap<>();
            for (String line : lines) {
                String[] entry = line.split(" ", 3);
                String kind;
                if (entry[0].equals(BuildExecutorFileCache.DIRECTORY)) {
                    kind = "d";
                } else if (inputted.contains(entry[0])) {
                    kind = "i";
                } else if (Long.parseLong(entry[1]) < inline) {
                    kind = "f";
                    included.putIfAbsent(entry[0], entry[2]);
                } else {
                    kind = "b";
                    CompletableFuture<Boolean> created = new CompletableFuture<>(), running = blobs.putIfAbsent(entry[0], created);
                    if (running == null) {
                        boolean held = false;
                        try {
                            String blob = address(digest, entry[0]);
                            int answer = send("HEAD", blob, null);
                            if (answer == HttpURLConnection.HTTP_NOT_FOUND) {
                                answer = send("PUT", blob, local.file(digest, entry[0]));
                            }
                            held = answer == HttpURLConnection.HTTP_OK
                                    || answer == HttpURLConnection.HTTP_CREATED
                                    || answer == HttpURLConnection.HTTP_NO_CONTENT;
                        } finally {
                            if (!held) {
                                blobs.remove(entry[0], created);
                            }
                            created.complete(held);
                        }
                        if (!held) {
                            return;
                        }
                    } else if (!running.join()) {
                        return;
                    }
                }
                manifest.append(kind).append(' ').append(line).append('\n');
            }
            temporary = Files.createTempFile("jenesis-cache", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                zip.setLevel(Deflater.BEST_SPEED);
                zip.putNextEntry(new ZipEntry(MANIFEST));
                zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                for (Map.Entry<String, String> entry : included.entrySet()) {
                    String name = entry.getValue();
                    zip.setLevel(ARCHIVES.contains(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT))
                            ? Deflater.NO_COMPRESSION
                            : Deflater.BEST_SPEED);
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    Files.copy(local.file(digest, entry.getKey()), zip);
                    zip.closeEntry();
                }
            }
            send("PUT", address(step, inputs), temporary);
        } catch (IOException _) {
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException _) {
                }
            }
        }
    }

    @Override
    public boolean stores() {
        return write;
    }

    @Override
    public void touch(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      String digest) {
        if (!read) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    send("HEAD", address(step, inputs), null);
                } catch (IOException | RuntimeException _) {
                }
            });
        } catch (RejectedExecutionException _) {
        }
    }

    private int send(String method, String path, Path body) throws IOException {
        HttpURLConnection connection = connect(method, path);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(Files.size(body));
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setRequestProperty("Expect", "100-continue");
                try (OutputStream out = connection.getOutputStream()) {
                    Files.copy(body, out);
                } catch (ProtocolException _) {
                }
            }
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private String address(byte[] step, SequencedMap<String, Map<Path, byte[]>> inputs) {
        MessageDigest message;
        try {
            message = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        message.update(FORMAT.getBytes(StandardCharsets.UTF_8));
        message.update((byte) 0);
        inputs.forEach((argument, files) -> {
            message.update(argument.getBytes(StandardCharsets.UTF_8));
            message.update((byte) 0);
            List<Map.Entry<Path, byte[]>> ordered = new ArrayList<>(files.entrySet());
            ordered.sort(Comparator.comparing(entry -> entry.getKey().toString().replace('\\', '/')));
            for (Map.Entry<Path, byte[]> entry : ordered) {
                message.update(entry.getKey().toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                message.update((byte) 0);
                message.update(entry.getValue());
            }
        });
        return HexFormat.of().formatHex(step) + "/" + HexFormat.of().formatHex(message.digest());
    }

    private static String address(String digest, String hex) {
        return HexFormat.of().formatHex(("blob:" + digest.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)) + "/" + hex;
    }

    private HttpURLConnection connect(String method, String path) throws IOException {
        String base = uri.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI target = URI.create(base + "/" + path);
        String scheme = target.getScheme(), host = target.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!"https".equals(scheme) && !loopback && !insecure) {
            throw new IllegalStateException("Refusing to send the cache token over insecure scheme '"
                    + scheme
                    + "': "
                    + target
                    + " (set -Djenesis.cache.insecure=true to allow plaintext)");
        }
        HttpURLConnection connection = (HttpURLConnection) Repository.connect(target, insecure);
        connection.setRequestMethod(method);
        connection.setConnectTimeout((int) Math.min(connectTimeout.toMillis(), Integer.MAX_VALUE));
        connection.setReadTimeout((int) Math.min(readTimeout.toMillis(), Integer.MAX_VALUE));
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "Jenesis");
        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (project != null) {
            connection.setRequestProperty(PROJECT, project);
        }
        return connection;
    }

    private static boolean isHex(String name) {
        if (name.length() < 8) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            if (Character.digit(name.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
