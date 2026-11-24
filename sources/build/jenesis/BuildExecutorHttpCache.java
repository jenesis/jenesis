package build.jenesis;

import module java.base;
import build.jenesis.Environment.Setting;

public final class BuildExecutorHttpCache implements BuildExecutorCache {

    public static final String KEY = "Jenesis-Cache-Key",
            PROJECT = "Jenesis-Cache-Project",
            DIGEST = "Jenesis-Cache-Digest";
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
    private final AtomicBoolean trimmed = new AtomicBoolean();

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
                false);
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
        Boolean insecure = environment.flagOrNull("cache.insecure", Setting.MACHINE);
        return insecure == null ? cache : cache.insecure(insecure);
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
                                   boolean insecure) {
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
    }

    public BuildExecutorHttpCache insecure(boolean insecure) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache local(BuildExecutorFileCache local) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache token(String token) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache project(String project) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache algorithm(String algorithm) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache connectTimeout(Duration connectTimeout) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache read(boolean read) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache write(boolean write) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache readTimeout(Duration readTimeout) {
        return new BuildExecutorHttpCache(uri, local, token, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    @Override
    public Optional<Map<Path, byte[]>> fetch(Executor executor,
                                             String identity,
                                             byte[] step,
                                             SequencedMap<String, Map<Path, byte[]>> inputs,
                                             boolean remote,
                                             Path target,
                                             String digest) throws IOException {
        if (!read || !remote) {
            return Optional.empty();
        }
        List<String> lines;
        HttpURLConnection manifest = connect("GET", address(step, inputs), digest);
        try {
            if (manifest.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }
            try (InputStream stream = manifest.getInputStream()) {
                lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .filter(line -> !line.isBlank())
                        .toList();
            }
        } catch (IOException _) {
            return Optional.empty();
        } finally {
            manifest.disconnect();
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String line : lines) {
            String[] entry = line.split(" ", 3);
            if (entry.length < 3 || !entry[0].equals(BuildExecutorFileCache.DIRECTORY) && !isHex(entry[0])) {
                return Optional.empty();
            }
            if (!entry[0].equals(BuildExecutorFileCache.DIRECTORY) && !Files.isRegularFile(local.file(digest, entry[0]))) {
                missing.add(entry[0]);
            }
        }
        if (!missing.isEmpty()) {
            byte[] wanted = String.join("\n", missing).getBytes(StandardCharsets.UTF_8);
            HttpURLConnection blobs = connect("POST", "blobs", digest);
            try {
                blobs.setDoOutput(true);
                blobs.setFixedLengthStreamingMode(wanted.length);
                blobs.setRequestProperty("Content-Type", "text/plain");
                try (OutputStream out = blobs.getOutputStream()) {
                    out.write(wanted);
                }
                if (blobs.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return Optional.empty();
                }
                try (ZipInputStream zip = new ZipInputStream(blobs.getInputStream())) {
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (missing.contains(entry.getName()) && local.admit(digest, entry.getName(), zip)) {
                            missing.remove(entry.getName());
                        }
                    }
                }
            } catch (IOException _) {
                return Optional.empty();
            } finally {
                blobs.disconnect();
            }
            trim(executor, digest);
            if (!missing.isEmpty()) {
                return Optional.empty();
            }
        }
        return local.restore(executor, lines, target, digest);
    }

    @Override
    public void store(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote,
                      Path output,
                      String digest,
                      Map<Path, byte[]> checksums) throws IOException {
        if (!write || !remote) {
            return;
        }
        Path temporary = null;
        try {
            List<String> lines = local.persist(output, digest, checksums);
            trim(executor, digest);
            byte[] manifest = lines.stream()
                    .map(line -> line + "\n")
                    .collect(Collectors.joining())
                    .getBytes(StandardCharsets.UTF_8);
            for (int attempt = 0; attempt < 2; attempt++) {
                List<String> missing;
                HttpURLConnection connection = connect("PUT", address(step, inputs), digest);
                try {
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(manifest.length);
                    connection.setRequestProperty("Content-Type", "text/plain");
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(manifest);
                    }
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_CONFLICT || attempt > 0) {
                        return;
                    }
                    try (InputStream stream = connection.getErrorStream()) {
                        missing = stream == null
                                ? List.of()
                                : new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
                    }
                } finally {
                    connection.disconnect();
                }
                Map<String, String> names = new HashMap<>();
                for (String line : lines) {
                    String[] entry = line.split(" ", 3);
                    names.put(entry[0], entry[2]);
                }
                temporary = Files.createTempFile("jenesis-cache", ".zip");
                try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                    for (String hex : new LinkedHashSet<>(missing)) {
                        String name = names.get(hex);
                        if (name == null || hex.equals(BuildExecutorFileCache.DIRECTORY)) {
                            continue;
                        }
                        zip.setLevel(ARCHIVES.contains(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT))
                                ? Deflater.NO_COMPRESSION
                                : Deflater.BEST_SPEED);
                        zip.putNextEntry(new ZipEntry(hex));
                        Files.copy(local.file(digest, hex), zip);
                        zip.closeEntry();
                    }
                }
                HttpURLConnection upload = connect("PUT", "blobs", digest);
                try {
                    upload.setDoOutput(true);
                    upload.setFixedLengthStreamingMode(Files.size(temporary));
                    upload.setRequestProperty("Content-Type", "application/zip");
                    try (OutputStream out = upload.getOutputStream()) {
                        Files.copy(temporary, out);
                    }
                    if (upload.getResponseCode() >= HttpURLConnection.HTTP_MULT_CHOICE) {
                        return;
                    }
                } finally {
                    upload.disconnect();
                }
            }
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
                      boolean remote,
                      String digest) {
        if (!read || !remote) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    HttpURLConnection connection = connect("HEAD", address(step, inputs), digest);
                    try {
                        connection.getResponseCode();
                    } finally {
                        connection.disconnect();
                    }
                } catch (IOException | RuntimeException _) {
                }
            });
        } catch (RejectedExecutionException _) {
        }
    }

    private void trim(Executor executor, String digest) {
        if (trimmed.compareAndSet(false, true)) {
            try {
                executor.execute(() -> local.trim(digest));
            } catch (RejectedExecutionException _) {
            }
        }
    }

    private String address(byte[] step, SequencedMap<String, Map<Path, byte[]>> inputs) {
        return "step/" + HexFormat.of().formatHex(step) + "/" + HexFormat.of().formatHex(fold(inputs));
    }

    private HttpURLConnection connect(String method, String path, String digest) throws IOException {
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
        connection.setRequestProperty(DIGEST, digest);
        if (token != null) {
            connection.setRequestProperty(KEY, token);
        }
        if (project != null) {
            connection.setRequestProperty(PROJECT, project);
        }
        return connection;
    }

    private byte[] fold(SequencedMap<String, Map<Path, byte[]>> inputs) {
        MessageDigest message;
        try {
            message = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
        return message.digest();
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
