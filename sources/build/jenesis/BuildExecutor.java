package build.jenesis;

import module java.base;

public interface BuildExecutor {

    String SKIP_MARKER = ".jenesis.skip", LOCK_MARKER = ".jenesis.lock";

    static BuildExecutor of(Path target) throws IOException {
        return new Configuration().of(target);
    }

    record Configuration(Duration timeout,
                         String digest,
                         boolean verbose,
                         boolean progress,
                         boolean cacheHits,
                         boolean rebuild,
                         boolean aggregate,
                         int concurrency,
                         BuildExecutorCache cache,
                         Output output) {

        public Configuration() {
            this(Duration.ZERO, "MD5", false, true, false, false, false, 0, null, new Output());
        }

        public static Configuration ofKeys(Function<String, String> keys) {
            String location = SequencedProperties.getProperty(keys, "cache.uri");
            BuildExecutorCache cache;
            if (location == null || location.isEmpty()) {
                cache = null;
            } else {
                URI uri = URI.create(location);
                cache = switch (uri.getScheme() == null ? "" : uri.getScheme()) {
                    case "http", "https" -> Repository.Origin.of(keys, "cache.uri") == Repository.Origin.PROJECT
                            ? BuildExecutorHttpCache.ofKeys(keys, uri).key(null)
                            : BuildExecutorHttpCache.ofKeys(keys, uri);
                    case "file" -> new BuildExecutorFileCache(Path.of(uri));
                    default -> throw new IllegalArgumentException(
                            "Unsupported cache URI scheme (expected http, https, or file): " + location);
                };
            }
            return new Configuration(
                    Duration.parse(SequencedProperties.getProperty(keys, "executor.timeout", Duration.ZERO.toString())),
                    SequencedProperties.getProperty(keys, "executor.digest", "MD5"),
                    SequencedProperties.flag(keys, "print.checksum"),
                    SequencedProperties.flag(keys, "print.progress", true),
                    SequencedProperties.flag(keys, "print.cache"),
                    SequencedProperties.flag(keys, "executor.rebuild"),
                    SequencedProperties.flag(keys, "executor.aggregate"),
                    SequencedProperties.number(keys, "executor.concurrency", 0),
                    cache,
                    new Output());
        }

        public Configuration timeout(Duration timeout) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration digest(String digest) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration verbose(boolean verbose) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration progress(boolean progress) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration cacheHits(boolean cacheHits) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration rebuild(boolean rebuild) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration aggregate(boolean aggregate) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration concurrency(int concurrency) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration cache(BuildExecutorCache cache) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public Configuration output(Output output) {
            return new Configuration(timeout, digest, verbose, progress, cacheHits, rebuild, aggregate, concurrency, cache, output);
        }

        public BuildExecutor of(Path target) throws IOException {
            return BuildExecutor.of(target,
                    timeout,
                    new HashDigestFunction(digest),
                    BuildStepHashFunction.ofSerializationDigest(digest),
                    progress
                            ? BuildExecutorCallback.printing(output.out(), verbose, cacheHits, target)
                            : BuildExecutorCallback.nop(),
                    cache == null ? BuildExecutorCache.nop() : cache,
                    rebuild,
                    aggregate,
                    concurrency);
        }
    }

    static BuildExecutor of(Path target,
                            Duration timeout,
                            HashDigestFunction hash,
                            BuildStepHashFunction stepHash,
                            BuildExecutorCallback callback,
                            BuildExecutorCache cache,
                            boolean rebuild,
                            boolean aggregate,
                            int concurrency) throws IOException {
        if (concurrency < 0) {
            throw new IllegalArgumentException("Concurrency must not be negative: " + concurrency);
        }
        if (rebuild && Files.isDirectory(target)) {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        BuildExecutor executor = new BuildExecutorDefault(target, timeout, hash, stepHash, callback, cache, aggregate, concurrency == 0 ? null : new BuildExecutorDefault.Permits(concurrency), "", Map.of());
        if (!Files.exists(target.resolve(SKIP_MARKER))) {
            Files.createFile(target.resolve(SKIP_MARKER));
        }
        return executor;
    }

    void addSource(String identity, Path path);

    default void addSource(String identity, BuildStep step, Path... paths) {
        addSource(identity, step, sequencedSetOf(paths));
    }

    void addSource(String identity, BuildStep step, SequencedSet<Path> paths);

    void replaceSource(String identity, Path path);

    default void replaceSource(String identity, BuildStep step, Path... paths) {
        replaceSource(identity, step, sequencedSetOf(paths));
    }

    default void replaceSource(String identity, BuildStep step, Stream<Path> paths) {
        replaceSource(identity, step, (SequencedSet<Path>) paths.collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    void replaceSource(String identity, BuildStep step, SequencedSet<Path> paths);

    default void addStep(String identity, BuildStep step, String... dependencies) {
        addStep(identity, step, sequencedMapOf(dependencies));
    }

    default void addStep(String identity, BuildStep step, Stream<String> dependencies) {
        addStep(identity, step, sequencedMapOf(dependencies));
    }

    default void addStep(String identity, BuildStep step, SequencedSet<String> dependencies) {
        addStep(identity, step, sequencedMapOf(dependencies));
    }

    void addStep(String identity, BuildStep step, SequencedMap<String, String> dependencies);

    void replaceStep(String identity, BuildStep step);

    void prependStep(String identity, String prepended, BuildStep step);

    void appendStep(String identity, String original, BuildStep step);

    default void addModule(String identity, BuildExecutorModule module, String... dependencies) {
        addModule(identity, module, Optional::of, sequencedMapOf(dependencies));
    }

    default void addModule(String identity,
                           BuildExecutorModule module,
                           Function<String, Optional<String>> resolver,
                           String... dependencies) {
        addModule(identity, module, resolver, sequencedMapOf(dependencies));
    }

    default void addModule(String identity, BuildExecutorModule module, Stream<String> dependencies) {
        addModule(identity, module, Optional::of, sequencedMapOf(dependencies));
    }

    default void addModule(String identity,
                           BuildExecutorModule module,
                           Function<String, Optional<String>> resolver,
                           Stream<String> dependencies) {
        addModule(identity, module, resolver, sequencedMapOf(dependencies));
    }

    default void addModule(String identity, BuildExecutorModule module, SequencedSet<String> dependencies) {
        addModule(identity, module, Optional::of, sequencedMapOf(dependencies));
    }

    default void addModule(String identity,
                           BuildExecutorModule module,
                           Function<String, Optional<String>> resolver,
                           SequencedSet<String> dependencies) {
        addModule(identity, module, resolver, sequencedMapOf(dependencies));
    }

    default void addModule(String identity, BuildExecutorModule module, SequencedMap<String, String> dependencies) {
        addModule(identity, module, Optional::of, dependencies);
    }

    void addModule(String identity,
                   BuildExecutorModule module,
                   Function<String, Optional<String>> resolver,
                   SequencedMap<String, String> dependencies);

    default void replaceModule(String identity, BuildExecutorModule module) {
        replaceModule(identity, module, Optional::of);
    }

    void replaceModule(String identity,
                       BuildExecutorModule module,
                       Function<String, Optional<String>> resolver);

    default void prependModule(String identity, String prepended, BuildExecutorModule module) {
        prependModule(identity, prepended, module, Optional::of);
    }

    void prependModule(String identity,
                       String prepended,
                       BuildExecutorModule module,
                       Function<String, Optional<String>> resolver);

    default void appendModule(String identity, String appended, BuildExecutorModule module) {
        appendModule(identity, appended, module, Optional::of);
    }

    void appendModule(String identity,
                      String appended,
                      BuildExecutorModule module,
                      Function<String, Optional<String>> resolver);

    default SequencedMap<String, Path> execute(String... selectors) {
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            return execute(executorService, selectors).toCompletableFuture().join();
        }
    }

    CompletionStage<SequencedMap<String, Path>> execute(Executor executor, String... selectors);

    @SafeVarargs
    private static <T> SequencedSet<T> sequencedSetOf(T... values) {
        SequencedSet<T> set = new LinkedHashSet<>();
        for (T value : values) {
            if (!set.add(value)) {
                throw new IllegalArgumentException("Duplicated argument: " + value);
            }
        }
        return set;
    }

    @SafeVarargs
    private static <T> SequencedMap<T, T> sequencedMapOf(T... values) {
        SequencedMap<T, T> map = new LinkedHashMap<>();
        for (T value : values) {
            map.put(value, value);
        }
        return map;
    }

    private static <T> SequencedMap<T, T> sequencedMapOf(Stream<T> values) {
        SequencedMap<T, T> map = new LinkedHashMap<>();
        values.forEach(value -> map.put(value, value));
        return map;
    }

    private static <T> SequencedMap<T, T> sequencedMapOf(Set<T> values) {
        SequencedMap<T, T> map = new LinkedHashMap<>();
        for (T value : values) {
            map.put(value, value);
        }
        return map;
    }
}
