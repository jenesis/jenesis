package build.jenesis;

import module java.base;

public record BuildExecutorFileCache(Path root,
                                     String algorithm,
                                     boolean links,
                                     int steps,
                                     int versions,
                                     long size,
                                     Duration ttl,
                                     boolean touch,
                                     boolean lru,
                                     boolean read,
                                     boolean write) implements BuildExecutorCache {

    public static final String BLOBS = "blobs", STEPS = "steps", DIRECTORY = "-";

    public BuildExecutorFileCache(Path root) {
        Path file = root.resolve("cache.properties");
        SequencedProperties properties = new SequencedProperties();
        if (Files.isRegularFile(file)) {
            try {
                properties = SequencedProperties.ofFiles(file);
            } catch (IOException _) {
            }
        }
        String steps = properties.value("steps"),
                versions = properties.value("versions"),
                size = properties.value("size"),
                ttl = properties.value("ttl");
        this(root,
                properties.value("digest", "SHA-256"),
                true,
                steps == null ? 250 : Integer.parseInt(steps),
                versions == null ? 10 : Integer.parseInt(versions),
                size == null ? 0 : Long.parseLong(size),
                ttl == null ? null : Duration.parse(ttl),
                properties.flag("touch", true),
                properties.flag("lru", true),
                properties.flag("read", true),
                properties.flag("write", true));
    }

    @Override
    public Optional<Map<Path, byte[]>> fetch(Executor executor,
                                             String identity,
                                             byte[] step,
                                             SequencedMap<String, Map<Path, byte[]>> inputs,
                                             boolean remote,
                                             Path target,
                                             String digest) throws IOException {
        if (!read) {
            return Optional.empty();
        }
        Path store = root.resolve(digest.toLowerCase(Locale.ROOT)),
                folder = store.resolve(STEPS).resolve(HexFormat.of().formatHex(step)),
                manifest = folder.resolve(HexFormat.of().formatHex(fold(inputs)));
        if (!Files.isRegularFile(manifest)) {
            return Optional.empty();
        }
        Optional<Map<Path, byte[]>> checksums;
        try {
            checksums = restore(executor, Files.readAllLines(manifest), target, digest);
        } catch (IOException _) {
            checksums = Optional.empty();
        }
        if (checksums.isEmpty()) {
            if (write) {
                Files.deleteIfExists(manifest);
            }
            return checksums;
        }
        if (touch && write) {
            touch(manifest);
            touch(folder);
        }
        return checksums;
    }

    Optional<Map<Path, byte[]>> restore(Executor executor, List<String> lines, Path target, String digest) {
        Path store = root.resolve(digest.toLowerCase(Locale.ROOT));
        try {
            Path base = target.normalize();
            Map<Path, String> expected = new LinkedHashMap<>();
            for (String line : lines) {
                String[] entry = line.split(" ", 3);
                Path destination = BuildStep.resolveContained(base, entry[2]);
                if (entry[0].equals(DIRECTORY)) {
                    Files.createDirectories(destination);
                    continue;
                }
                Files.createDirectories(destination.getParent());
                if (links) {
                    BuildStep.linkOrCopy(destination, blob(store, entry[0]));
                } else {
                    Files.copy(blob(store, entry[0]), destination);
                }
                expected.put(Path.of(entry[2]), entry[0]);
            }
            Map<Path, byte[]> checksums = HashFunction.read(target, new HashDigestFunction(digest), executor);
            boolean intact = checksums.keySet().equals(expected.keySet());
            for (Map.Entry<Path, String> entry : expected.entrySet()) {
                byte[] checksum = checksums.get(entry.getKey());
                if (checksum == null || !HexFormat.of().formatHex(checksum).equals(entry.getValue())) {
                    intact = false;
                    if (write) {
                        Files.deleteIfExists(blob(store, entry.getValue()));
                    }
                }
            }
            if (!intact) {
                throw new IOException("A file restored to " + target + " is not what its checksum names");
            }
            return Optional.of(checksums);
        } catch (IOException | RuntimeException _) {
            clean(target);
            return Optional.empty();
        }
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
        if (!write) {
            return;
        }
        Path store = root.resolve(digest.toLowerCase(Locale.ROOT)),
                folder = store.resolve(STEPS).resolve(HexFormat.of().formatHex(step)),
                manifest = folder.resolve(HexFormat.of().formatHex(fold(inputs)));
        if (Files.exists(manifest)) {
            return;
        }
        Path temporary = null;
        try {
            List<String> lines = persist(output, digest, checksums);
            Files.createDirectories(folder);
            temporary = Files.createTempFile(folder, "tmp", null);
            Files.write(temporary, lines);
            try {
                Files.move(temporary, manifest, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(temporary, manifest);
            }
            temporary = null;
            if (evict(folder, versions) | evict(store.resolve(STEPS), steps)) {
                sweep(store);
            }
            trim(digest);
        } catch (IOException | RuntimeException _) {
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException _) {
                }
            }
        }
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            executor.execute(() -> {
                try {
                    Instant threshold = Instant.now().minus(ttl);
                    boolean evicted = false;
                    try (DirectoryStream<Path> folders = Files.newDirectoryStream(store.resolve(STEPS))) {
                        for (Path candidate : folders) {
                            if (!isHex(candidate.getFileName().toString()) || !Files.isDirectory(candidate)) {
                                continue;
                            }
                            boolean remaining = false;
                            try (DirectoryStream<Path> manifests = Files.newDirectoryStream(candidate)) {
                                for (Path entry : manifests) {
                                    if (isHex(entry.getFileName().toString())
                                            && lastModified(entry).toInstant().isBefore(threshold)) {
                                        Files.deleteIfExists(entry);
                                        evicted = true;
                                    } else {
                                        remaining = true;
                                    }
                                }
                            }
                            if (!remaining) {
                                Files.deleteIfExists(candidate);
                            }
                        }
                    }
                    if (evicted) {
                        sweep(store);
                    }
                } catch (IOException _) {
                }
            });
        }
    }

    List<String> persist(Path output, String digest, Map<Path, byte[]> checksums) throws IOException {
        Path store = root.resolve(digest.toLowerCase(Locale.ROOT));
        Files.createDirectories(store.resolve(BLOBS));
        try {
            Files.createFile(root.resolve(BuildExecutor.SKIP_MARKER));
        } catch (FileAlreadyExistsException _) {
        }
        HashFunction hash = new HashDigestFunction(digest);
        SequencedMap<String, String> lines = new TreeMap<>();
        Files.walkFileTree(output, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
                    if (!directory.equals(output) && !children.iterator().hasNext()) {
                        String name = output.relativize(directory).toString().replace(File.separatorChar, '/');
                        lines.put(name, DIRECTORY + " 0 " + name);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                byte[] checksum = checksums.get(output.relativize(file));
                String name = output.relativize(file).toString().replace(File.separatorChar, '/'),
                        hex = HexFormat.of().formatHex(checksum == null ? hash.hash(file) : checksum);
                if (name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
                    throw new IOException("A cache holds no file named across lines: " + file);
                }
                Path blob = blob(store, hex);
                if (!Files.exists(blob)) {
                    Files.createDirectories(blob.getParent());
                    boolean linked = false;
                    if (links && !Files.isSymbolicLink(file)) {
                        try {
                            Files.createLink(blob, file);
                            linked = true;
                        } catch (FileAlreadyExistsException _) {
                            linked = true;
                        } catch (UnsupportedOperationException | FileSystemException _) {
                        }
                    }
                    if (!linked) {
                        Path copy = Files.createTempFile(blob.getParent(), "tmp", null);
                        try {
                            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                            Files.move(copy, blob, StandardCopyOption.REPLACE_EXISTING);
                        } finally {
                            Files.deleteIfExists(copy);
                        }
                    }
                }
                lines.put(name, hex + " " + attributes.size() + " " + name);
                return FileVisitResult.CONTINUE;
            }
        });
        return List.copyOf(lines.values());
    }

    boolean admit(String digest, String hex, InputStream content) throws IOException {
        Path blob = blob(root.resolve(digest.toLowerCase(Locale.ROOT)), hex);
        Files.createDirectories(blob.getParent());
        try {
            Files.createFile(root.resolve(BuildExecutor.SKIP_MARKER));
        } catch (FileAlreadyExistsException _) {
        }
        MessageDigest message;
        try {
            message = MessageDigest.getInstance(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path copy = Files.createTempFile(blob.getParent(), "tmp", null);
        try {
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(copy), message)) {
                content.transferTo(out);
            }
            if (!HexFormat.of().formatHex(message.digest()).equals(hex)) {
                return false;
            }
            Files.move(copy, blob, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    Path file(String digest, String hex) {
        return blob(root.resolve(digest.toLowerCase(Locale.ROOT)), hex);
    }

    void trim(String digest) {
        if (size > 0) {
            try {
                evictBySize(root.resolve(digest.toLowerCase(Locale.ROOT)).resolve(BLOBS));
            } catch (IOException _) {
            }
        }
    }

    public BuildExecutorFileCache size(long size) {
        return new BuildExecutorFileCache(root, algorithm, links, steps, versions, size, ttl, touch, lru, read, write);
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
        if (!touch || !write) {
            return;
        }
        Path folder = root.resolve(digest.toLowerCase(Locale.ROOT))
                .resolve(STEPS)
                .resolve(HexFormat.of().formatHex(step));
        Path manifest = folder.resolve(HexFormat.of().formatHex(fold(inputs)));
        if (Files.exists(manifest)) {
            touch(manifest);
            touch(folder);
        }
    }

    private static Path blob(Path store, String hex) {
        return store.resolve(BLOBS).resolve(hex.substring(0, 2)).resolve(hex.substring(2));
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

    private boolean evict(Path parent, int limit) throws IOException {
        if (limit <= 0) {
            return false;
        }
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            for (Path path : stream) {
                if (isHex(path.getFileName().toString())) {
                    entries.add(path);
                }
            }
        }
        int excess = entries.size() - limit;
        if (excess <= 0) {
            return false;
        }
        entries.sort(Comparator.comparing(BuildExecutorFileCache::lastModified));
        for (int index = 0; index < excess; index++) {
            try {
                Files.walkFileTree(lru ? entries.get(index) : entries.get(entries.size() - 1 - index),
                        new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                                    throws IOException {
                                Files.deleteIfExists(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                                    throws IOException {
                                Files.deleteIfExists(directory);
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException _) {
            }
        }
        return true;
    }

    private void evictBySize(Path blobs) throws IOException {
        record Blob(Path path, long size, FileTime used) {
        }
        List<Blob> unused = new ArrayList<>();
        long total = 0;
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(blobs)) {
            for (Path folder : folders) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
                    for (Path file : files) {
                        if (!isHex(file.getFileName().toString())) {
                            continue;
                        }
                        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                        total += attributes.size();
                        try {
                            Map<String, Object> unix = Files.readAttributes(file, "unix:nlink,ctime");
                            if ((Integer) unix.get("nlink") < 2) {
                                unused.add(new Blob(file, attributes.size(), (FileTime) unix.get("ctime")));
                            }
                        } catch (UnsupportedOperationException | IllegalArgumentException _) {
                            unused.add(new Blob(file, attributes.size(), attributes.lastModifiedTime()));
                        }
                    }
                }
            }
        }
        unused.sort(Comparator.comparing(Blob::used));
        for (int index = 0; index < unused.size() && total > size; index++) {
            Blob blob = lru ? unused.get(index) : unused.get(unused.size() - 1 - index);
            try {
                Files.deleteIfExists(blob.path());
                total -= blob.size();
            } catch (IOException _) {
            }
        }
    }

    private static void sweep(Path store) throws IOException {
        Set<String> referenced = new HashSet<>();
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(store.resolve(STEPS))) {
            for (Path folder : folders) {
                if (!isHex(folder.getFileName().toString()) || !Files.isDirectory(folder)) {
                    continue;
                }
                try (DirectoryStream<Path> manifests = Files.newDirectoryStream(folder)) {
                    for (Path manifest : manifests) {
                        for (String line : Files.readAllLines(manifest)) {
                            referenced.add(line.substring(0, Math.max(0, line.indexOf(' '))));
                        }
                    }
                }
            }
        }
        if (!Files.isDirectory(store.resolve(BLOBS))) {
            return;
        }
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(store.resolve(BLOBS))) {
            for (Path folder : folders) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
                    for (Path file : files) {
                        if (!referenced.contains(folder.getFileName().toString() + file.getFileName())) {
                            Files.deleteIfExists(file);
                        }
                    }
                }
            }
        }
    }

    private static void touch(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException _) {
        }
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException _) {
            return FileTime.fromMillis(0);
        }
    }

    private static boolean isHex(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            if (Character.digit(name.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void clean(Path target) {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(target)) {
            for (Path child : children) {
                Files.walkFileTree(child, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                            throws IOException {
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException _) {
        }
    }
}
