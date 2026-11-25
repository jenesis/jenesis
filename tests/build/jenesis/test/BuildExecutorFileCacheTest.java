package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorFileCache;
import build.jenesis.BuildExecutorHttpCache;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildExecutorFileCacheTest {

    private final Map<String, String> settings = new HashMap<>();

    private static final AtomicInteger EXECUTIONS = new AtomicInteger();

    @TempDir
    private Path cacheRoot, output, target, source, firstTarget, secondTarget;
    private transient HashDigestFunction hash;

    @BeforeEach
    public void setUp() {
        hash = new HashDigestFunction("SHA-256");
    }

    @Test
    public void stores_and_fetches_round_trip() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1, 2, 3, 4};
        SequencedMap<String, Map<Path, byte[]>> inputs = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, inputs, output, "SHA-256", Map.of());
        Path stepFolder = steps().resolve(HexFormat.of().formatHex(step));
        assertThat(stepFolder).exists();
        try (Stream<Path> entries = Files.list(stepFolder)) {
            List<Path> list = entries.toList();
            assertThat(list).hasSize(1);
            assertThat(list.getFirst()).content().isEqualTo(hex("result") + " 6 file\n");
        }
        assertThat(blob("result")).content().isEqualTo("result");
        Optional<Map<Path, byte[]>> result = cache.fetch(Runnable::run, "step", step, inputs, new LinkedHashMap<>(), target, "SHA-256");
        assertThat(result).isPresent();
        assertThat(result.get()).as("the checksums of what was restored, which the executor hashes no second time")
                .containsOnlyKeys(Path.of("file"));
        assertThat(result.get().get(Path.of("file"))).asHexString().isEqualToIgnoringCase(hex("result"));
        assertThat(target.resolve("file")).content().isEqualTo("result");
    }

    @Test
    public void marks_its_root_so_that_no_project_scan_reads_the_cached_sources() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Files.writeString(output.resolve("module-info.java"), "module sample {}");
        cache.store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(cacheRoot.resolve(BuildExecutor.SKIP_MARKER)).isRegularFile();
    }

    @Test
    public void fetch_returns_empty_on_miss() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Optional<Map<Path, byte[]>> result = cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), new LinkedHashMap<>(), target, "SHA-256");
        assertThat(result).isEmpty();
        try (Stream<Path> entries = Files.list(target)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    public void fetch_materializes_via_hard_link() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> inputs = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, inputs, output, "SHA-256", Map.of());
        cache.fetch(Runnable::run, "step", step, inputs, new LinkedHashMap<>(), target, "SHA-256");
        Path cached = blob("result");
        Object targetKey = Files.readAttributes(target.resolve("file"), BasicFileAttributes.class).fileKey();
        Object cachedKey = Files.readAttributes(cached, BasicFileAttributes.class).fileKey();
        Assumptions.assumeTrue(targetKey != null && cachedKey != null);
        assertThat(targetKey).isEqualTo(cachedKey);
    }

    @Test
    public void reads_digest_from_cache_properties() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "digest=MD5\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            String name = entries.findFirst().orElseThrow().getFileName().toString();
            assertThat(name).hasSize(32);
        }
    }

    @Test
    public void configuration_resolves_cache_from_uri() {
        assertThat(BuildExecutor.Configuration.ofEnvironment(new Environment(settings)).cache()).isNull();
        settings.put("cache.uri", "");
        assertThat(BuildExecutor.Configuration.ofEnvironment(new Environment(settings)).cache()).isNull();
        settings.put("cache.uri", cacheRoot.toUri().toString());
        assertThat(BuildExecutor.Configuration.ofEnvironment(new Environment(settings)).cache())
                .isInstanceOf(BuildExecutorFileCache.class);
        settings.put("cache.uri", "https://cache.example.test/");
        assertThat(BuildExecutor.Configuration.ofEnvironment(new Environment(settings)).cache())
                .isInstanceOf(BuildExecutorHttpCache.class);
        settings.put("cache.uri", cacheRoot.toString());
        assertThatThrownBy(() -> BuildExecutor.Configuration.ofEnvironment(new Environment(settings)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void serves_step_output_from_cache_across_builds() throws IOException {
        EXECUTIONS.set(0);
        Files.writeString(source.resolve("file"), "foo");
        BuildExecutorCache cache = new BuildExecutorFileCache(cacheRoot);
        BuildStep buildStep = (_, context, arguments) -> {
            EXECUTIONS.incrementAndGet();
            Files.writeString(
                    context.next().resolve("file"),
                    Files.readString(arguments.get("source").folder().resolve("file")) + "bar");
            return CompletableFuture.completedStage(new BuildStepResult());
        };

        BuildExecutor first = BuildExecutor.of(firstTarget,
                Duration.ZERO,
                hash,
                BuildStepHashFunction.ofSerializationDigest("SHA-256"),
                BuildExecutorCallback.nop(),
                cache,
                false,
                false,
                0);
        first.addSource("source", source);
        first.addStep("step", buildStep, "source");
        first.execute(Runnable::run).toCompletableFuture().join();
        assertThat(firstTarget.resolve("step").resolve("output").resolve("file")).content().isEqualTo("foobar");
        assertThat(EXECUTIONS).hasValue(1);

        BuildExecutor second = BuildExecutor.of(secondTarget,
                Duration.ZERO,
                hash,
                BuildStepHashFunction.ofSerializationDigest("SHA-256"),
                BuildExecutorCallback.nop(),
                cache,
                false,
                false,
                0);
        second.addSource("source", source);
        second.addStep("step", buildStep, "source");
        second.execute(Runnable::run).toCompletableFuture().join();
        assertThat(secondTarget.resolve("step").resolve("output").resolve("file")).content().isEqualTo("foobar");
        assertThat(EXECUTIONS).hasValue(1);
    }

    @Test
    public void changed_input_misses_and_leaves_target_empty() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        Optional<Map<Path, byte[]>> result = cache.fetch(Runnable::run, "step", step, inputs("source", "file", new byte[]{8}), new LinkedHashMap<>(), target, "SHA-256");
        assertThat(result).isEmpty();
        try (Stream<Path> entries = Files.list(target)) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    public void changed_source_re_executes_past_a_populated_cache() throws IOException {
        EXECUTIONS.set(0);
        Files.writeString(source.resolve("file"), "foo");
        BuildExecutorCache cache = new BuildExecutorFileCache(cacheRoot);
        BuildStep buildStep = (_, context, arguments) -> {
            EXECUTIONS.incrementAndGet();
            Files.writeString(
                    context.next().resolve("file"),
                    Files.readString(arguments.get("source").folder().resolve("file")) + "bar");
            return CompletableFuture.completedStage(new BuildStepResult());
        };

        BuildExecutor first = BuildExecutor.of(firstTarget,
                Duration.ZERO,
                hash,
                BuildStepHashFunction.ofSerializationDigest("SHA-256"),
                BuildExecutorCallback.nop(),
                cache,
                false,
                false,
                0);
        first.addSource("source", source);
        first.addStep("step", buildStep, "source");
        first.execute(Runnable::run).toCompletableFuture().join();
        assertThat(firstTarget.resolve("step").resolve("output").resolve("file")).content().isEqualTo("foobar");
        assertThat(EXECUTIONS).hasValue(1);

        Files.writeString(source.resolve("file"), "baz");

        BuildExecutor second = BuildExecutor.of(secondTarget,
                Duration.ZERO,
                hash,
                BuildStepHashFunction.ofSerializationDigest("SHA-256"),
                BuildExecutorCallback.nop(),
                cache,
                false,
                false,
                0);
        second.addSource("source", source);
        second.addStep("step", buildStep, "source");
        second.execute(Runnable::run).toCompletableFuture().join();
        assertThat(secondTarget.resolve("step").resolve("output").resolve("file")).content().isEqualTo("bazbar");
        assertThat(EXECUTIONS).hasValue(2);
    }

    @Test
    public void a_manifest_naming_a_path_outside_the_target_leaves_the_target_empty() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Path manifest;
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            manifest = entries.findFirst().orElseThrow();
        }
        Files.writeString(manifest, hex("result") + " 6 file\n" + hex("result") + " 6 ../escape\n");
        Files.writeString(target.resolve("stale"), "leftover");
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256")).isEmpty();
        try (Stream<Path> contents = Files.list(target)) {
            assertThat(contents.findAny()).isEmpty();
        }
        assertThat(target.resolveSibling("escape")).doesNotExist();
        assertThat(manifest).as("a manifest that cannot be restored is dropped").doesNotExist();
    }

    @Test
    public void a_build_told_not_to_write_reads_the_cache_and_stores_nothing() throws IOException {
        byte[] stored = {1}, declined = {2};
        SequencedMap<String, Map<Path, byte[]>> inputs = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        new BuildExecutorFileCache(cacheRoot).store(Runnable::run, "step", stored, inputs, output, "SHA-256", Map.of());
        BuildExecutorFileCache cache = BuildExecutorFileCache.ofEnvironment(
                new Environment(Map.of("cache.write", "false")),
                cacheRoot);
        assertThat(cache.stores()).isFalse();
        cache.store(Runnable::run, "step", declined, inputs, output, "SHA-256", Map.of());
        assertThat(steps().resolve(HexFormat.of().formatHex(declined))).doesNotExist();
        assertThat(cache.fetch(Runnable::run, "step", stored, inputs, new LinkedHashMap<>(), target, "SHA-256")).isPresent();
        assertThat(target.resolve("file")).content().isEqualTo("result");
        assertThat(BuildExecutorFileCache.ofEnvironment(new Environment(Map.of()), cacheRoot).stores()).isTrue();
    }

    @Test
    public void a_folder_that_cannot_be_written_to_is_read_and_fails_no_build() throws IOException {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        byte[] stored = {1}, tampered = {2}, declined = {3};
        SequencedMap<String, Map<Path, byte[]>> inputs = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        new BuildExecutorFileCache(cacheRoot).store(Runnable::run, "step", stored, inputs, output, "SHA-256", Map.of());
        Files.delete(output.resolve("file"));
        Files.writeString(output.resolve("file"), "other");
        new BuildExecutorFileCache(cacheRoot).store(Runnable::run, "step", tampered, inputs, output, "SHA-256", Map.of());
        Files.writeString(blob("other"), "wrong");
        List<Path> folders;
        try (Stream<Path> walk = Files.walk(cacheRoot)) {
            folders = walk.filter(Files::isDirectory).toList();
        }
        for (Path folder : folders) {
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("r-xr-xr-x"));
        }
        try {
            Assumptions.assumeFalse(Files.isWritable(cacheRoot), "a user no permission holds back writes anywhere");
            BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
            assertThat(cache.fetch(Runnable::run, "step", stored, inputs, new LinkedHashMap<>(), target, "SHA-256")).isPresent();
            assertThat(target.resolve("file")).content().isEqualTo("result");
            assertThat(cache.fetch(Runnable::run, "step", tampered, inputs, new LinkedHashMap<>(), firstTarget, "SHA-256"))
                    .as("a file that is not what its checksum names is a miss where it cannot be dropped either")
                    .isEmpty();
            try (Stream<Path> contents = Files.list(firstTarget)) {
                assertThat(contents.findAny()).isEmpty();
            }
            cache.store(Runnable::run, "step", declined, inputs, output, "SHA-256", Map.of());
            cache.touch(Runnable::run, "step", stored, inputs, "SHA-256");
            assertThat(steps().resolve(HexFormat.of().formatHex(declined))).doesNotExist();
        } finally {
            for (Path folder : folders) {
                Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        }
    }

    @Test
    public void a_tampered_file_is_a_miss_that_drops_the_file_and_its_manifest() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        Files.writeString(output.resolve("other"), "untouched");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Files.writeString(blob("result"), "tampered");
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256")).isEmpty();
        try (Stream<Path> contents = Files.list(target)) {
            assertThat(contents.findAny()).as("nothing of a tampered entry is handed on").isEmpty();
        }
        assertThat(blob("result")).doesNotExist();
        assertThat(blob("untouched")).as("a file that is what its name says stays for other steps").exists();
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    public void a_manifest_naming_a_file_the_store_lost_is_a_miss_that_drops_the_manifest() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Files.delete(blob("result"));
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256")).isEmpty();
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            assertThat(entries.toList()).isEmpty();
        }
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256"))
                .as("the next run stores the step again")
                .isPresent();
    }

    @Test
    public void a_file_two_steps_wrote_is_kept_once() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Files.writeString(output.resolve("file"), "shared");
        Files.writeString(source.resolve("copy"), "shared");
        Files.writeString(source.resolve("own"), "apart");
        cache.store(Runnable::run, "first", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        cache.store(Runnable::run, "second", new byte[]{2}, inputs("source", "file", new byte[]{9}), source, "SHA-256", Map.of());
        try (Stream<Path> files = Files.walk(cacheRoot.resolve("sha-256").resolve("blobs"))) {
            assertThat(files.filter(Files::isRegularFile).toList())
                    .containsExactlyInAnyOrder(blob("shared"), blob("apart"));
        }
        assertThat(cache.fetch(Runnable::run, "second", new byte[]{2}, inputs("source", "file", new byte[]{9}), new LinkedHashMap<>(), target, "SHA-256")).isPresent();
        assertThat(target.resolve("copy")).content().isEqualTo("shared");
        assertThat(target.resolve("own")).content().isEqualTo("apart");
    }

    @Test
    public void an_empty_folder_of_the_output_is_restored() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.createDirectories(output.resolve("classes").resolve("empty"));
        Files.writeString(output.resolve("classes").resolve("file"), "result");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256")).isPresent();
        assertThat(target.resolve("classes").resolve("empty")).isEmptyDirectory();
        assertThat(target.resolve("classes").resolve("file")).content().isEqualTo("result");
    }

    @Test
    public void a_store_written_under_one_digest_is_kept_apart_from_another() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-1"))
                .as("a build hashing with another digest finds nothing it could not verify")
                .isEmpty();
        cache.store(Runnable::run, "step", step, in, output, "SHA-1", Map.of());
        assertThat(cacheRoot.resolve("sha-1").resolve("steps").resolve(HexFormat.of().formatHex(step))).exists();
        Optional<Map<Path, byte[]>> result = cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-1");
        assertThat(result).isPresent();
        assertThat(result.get().get(Path.of("file"))).hasSize(20);
        assertThat(blob("result")).as("the other digest's files are untouched").exists();
    }

    @Test
    public void store_is_idempotent_and_keeps_the_first_entry() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "first");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Files.delete(output.resolve("file"));
        Files.writeString(output.resolve("file"), "second");
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            assertThat(entries.toList()).hasSize(1);
        }
        Optional<Map<Path, byte[]>> result = cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256");
        assertThat(result).isPresent();
        assertThat(target.resolve("file")).content().isEqualTo("first");
    }

    @Test
    public void concurrent_stores_of_one_key_settle_on_a_single_entry() throws Exception {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        int threads = 8;
        ExecutorService service = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(service.submit(() -> {
                latch.await();
                cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
                return null;
            }));
        }
        latch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        service.shutdown();
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            List<Path> list = entries.toList();
            assertThat(list).hasSize(1);
            assertThat(list.getFirst().getFileName().toString()).hasSize(64);
        }
        assertThat(cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256")).isPresent();
        assertThat(target.resolve("file")).content().isEqualTo("result");
    }

    @Test
    public void evicts_least_recently_updated_step_folder() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "steps=2\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        store(cache, new byte[]{1}, new byte[]{9});
        store(cache, new byte[]{2}, new byte[]{9});
        Path a = steps().resolve(HexFormat.of().formatHex(new byte[]{1}));
        Path b = steps().resolve(HexFormat.of().formatHex(new byte[]{2}));
        Files.setLastModifiedTime(a, FileTime.from(Instant.now().minusSeconds(300)));
        Files.setLastModifiedTime(b, FileTime.from(Instant.now().minusSeconds(60)));
        store(cache, new byte[]{3}, new byte[]{9});
        assertThat(a).doesNotExist();
        assertThat(b).exists();
        assertThat(steps().resolve(HexFormat.of().formatHex(new byte[]{3}))).exists();
    }

    @Test
    public void evicts_least_recently_updated_version() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "versions=2\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        Path v1 = store(cache, step, new byte[]{1});
        Path v2 = store(cache, step, new byte[]{2});
        Files.setLastModifiedTime(v1, FileTime.from(Instant.now().minusSeconds(300)));
        Files.setLastModifiedTime(v2, FileTime.from(Instant.now().minusSeconds(60)));
        Path v3 = store(cache, step, new byte[]{3});
        assertThat(v1).doesNotExist();
        assertThat(v2).exists();
        assertThat(v3).exists();
    }

    @Test
    public void evicts_entries_idle_longer_than_ttl_on_a_background_sweep() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "ttl=PT60S\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        Path stale = store(cache, step, new byte[]{1});
        Path fresh = store(cache, step, new byte[]{2});
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minusSeconds(300)));
        assertThat(stale).as("a build applies the age of its cache once, when it first stores a step").exists();
        Path latest = store(new BuildExecutorFileCache(cacheRoot), step, new byte[]{3});
        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
        assertThat(latest).exists();
    }

    @Test
    public void evicts_most_recently_updated_when_configured() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "steps=2\nlru=false\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        store(cache, new byte[]{1}, new byte[]{9});
        store(cache, new byte[]{2}, new byte[]{9});
        Path a = steps().resolve(HexFormat.of().formatHex(new byte[]{1}));
        Path b = steps().resolve(HexFormat.of().formatHex(new byte[]{2}));
        Files.setLastModifiedTime(a, FileTime.from(Instant.now().minusSeconds(300)));
        Files.setLastModifiedTime(b, FileTime.from(Instant.now().minusSeconds(200)));
        store(cache, new byte[]{3}, new byte[]{9});
        assertThat(steps().resolve(HexFormat.of().formatHex(new byte[]{3}))).doesNotExist();
        assertThat(a).exists();
        assertThat(b).exists();
    }

    @Test
    public void evicts_the_files_no_output_folder_links_oldest_first_until_under_size() throws Exception {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        Files.writeString(cacheRoot.resolve("cache.properties"), "size=2500\nsteps=0\nversions=0\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Path linked = storeSized(cache, new byte[]{1}, 1000);
        Files.createLink(target.resolve("held"), linked);
        Files.delete(output.resolve("file"));
        Thread.sleep(20);
        Path oldest = storeSized(cache, new byte[]{2}, 1001);
        Files.delete(output.resolve("file"));
        Thread.sleep(20);
        Path newer = storeSized(cache, new byte[]{3}, 1002);
        Files.delete(output.resolve("file"));
        Thread.sleep(20);
        assertThat(oldest).as("a build applies the size of its cache once, when it first stores a step").exists();
        Path latest = storeSized(new BuildExecutorFileCache(cacheRoot), new byte[]{4}, 1003);
        assertThat(linked).as("a file an output folder still links costs no space of its own").exists();
        assertThat(oldest).doesNotExist();
        assertThat(newer).doesNotExist();
        assertThat(latest).as("the output that was just stored still links its file").exists();
        assertThat(cache.fetch(Runnable::run, "step", new byte[]{2}, inputs("source", "file", new byte[]{9}), new LinkedHashMap<>(), firstTarget, "SHA-256"))
                .as("the manifest of an evicted file is dropped when it is next asked for")
                .isEmpty();
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(new byte[]{2})))) {
            assertThat(entries.toList()).isEmpty();
        }
    }

    @Test
    public void a_build_that_stores_several_steps_applies_size_and_age_once_and_in_the_background() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "size=1\nttl=PT60S\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        List<Runnable> queued = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (byte step = 1; step <= 3; step++) {
            Files.write(output.resolve("file"), new byte[1000 + step]);
            cache.store(queued::add, "step", new byte[]{step}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
            String hex = HexFormat.of().formatHex(hash.hash(output.resolve("file")));
            files.add(cacheRoot.resolve("sha-256").resolve("blobs").resolve(hex.substring(0, 2)).resolve(hex.substring(2)));
            Files.delete(output.resolve("file"));
            Files.setLastModifiedTime(manifest(new byte[]{step}), FileTime.from(Instant.now().minusSeconds(300)));
        }
        assertThat(queued).as("three steps stored, one pass over the cache asked for").hasSize(1);
        assertThat(files).as("no step waited for it").allSatisfy(file -> assertThat(file).exists());
        queued.getFirst().run();
        assertThat(files).allSatisfy(file -> assertThat(file).doesNotExist());
        try (Stream<Path> manifests = Files.walk(steps())) {
            assertThat(manifests.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    public void a_build_that_stores_nothing_evicts_nothing() throws IOException {
        byte[] step = {1};
        Path stored = storeSized(new BuildExecutorFileCache(cacheRoot), step, 1000);
        Files.delete(output.resolve("file"));
        Files.writeString(cacheRoot.resolve("cache.properties"), "size=1\nttl=PT60S\n");
        Files.setLastModifiedTime(manifest(step), FileTime.from(Instant.now().minusSeconds(300)));
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        List<Runnable> queued = new ArrayList<>();
        assertThat(cache.fetch(queued::add, "step", step, inputs("source", "file", new byte[]{9}), new LinkedHashMap<>(), target, "SHA-256")).isPresent();
        cache.touch(queued::add, "step", step, inputs("source", "file", new byte[]{9}), "SHA-256");
        assertThat(queued).isEmpty();
        assertThat(stored).exists();
    }

    @Test
    public void an_evicted_manifest_takes_the_files_only_it_named() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "steps=1\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Files.writeString(output.resolve("shared"), "shared");
        Files.writeString(output.resolve("file"), "first");
        cache.store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        Files.setLastModifiedTime(steps().resolve(HexFormat.of().formatHex(new byte[]{1})), FileTime.from(Instant.now().minusSeconds(300)));
        Files.delete(output.resolve("file"));
        Files.writeString(output.resolve("file"), "second");
        cache.store(Runnable::run, "step", new byte[]{2}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(blob("first")).doesNotExist();
        assertThat(blob("shared")).exists();
        assertThat(blob("second")).exists();
    }

    @Test
    public void touch_updates_timestamps_on_read() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = store(cache, step, new byte[]{9});
        Path folder = steps().resolve(HexFormat.of().formatHex(step));
        Files.setLastModifiedTime(entry, FileTime.from(Instant.now().minusSeconds(300)));
        Files.setLastModifiedTime(folder, FileTime.from(Instant.now().minusSeconds(300)));
        FileTime entryBefore = Files.getLastModifiedTime(entry);
        FileTime folderBefore = Files.getLastModifiedTime(folder);
        cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256");
        assertThat(Files.getLastModifiedTime(entry)).isGreaterThan(entryBefore);
        assertThat(Files.getLastModifiedTime(folder)).isGreaterThan(folderBefore);
    }

    @Test
    public void does_not_touch_when_touch_off() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "touch=false\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = store(cache, step, new byte[]{9});
        Files.setLastModifiedTime(entry, FileTime.from(Instant.now().minusSeconds(300)));
        FileTime before = Files.getLastModifiedTime(entry);
        cache.fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256");
        assertThat(Files.getLastModifiedTime(entry)).isEqualTo(before);
    }

    @Test
    public void touch_refreshes_timestamps_for_a_present_entry() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = store(cache, step, new byte[]{9});
        Path folder = steps().resolve(HexFormat.of().formatHex(step));
        Files.setLastModifiedTime(entry, FileTime.from(Instant.now().minusSeconds(300)));
        Files.setLastModifiedTime(folder, FileTime.from(Instant.now().minusSeconds(300)));
        FileTime entryBefore = Files.getLastModifiedTime(entry);
        FileTime folderBefore = Files.getLastModifiedTime(folder);
        cache.touch(Runnable::run, "step", step, in, "SHA-256");
        assertThat(Files.getLastModifiedTime(entry)).isGreaterThan(entryBefore);
        assertThat(Files.getLastModifiedTime(folder)).isGreaterThan(folderBefore);
    }

    @Test
    public void touch_is_a_no_op_when_touching_is_off() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "touch=false\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = store(cache, step, new byte[]{9});
        Files.setLastModifiedTime(entry, FileTime.from(Instant.now().minusSeconds(300)));
        FileTime before = Files.getLastModifiedTime(entry);
        cache.touch(Runnable::run, "step", step, in, "SHA-256");
        assertThat(Files.getLastModifiedTime(entry)).isEqualTo(before);
    }

    @Test
    public void touch_on_a_missing_entry_does_nothing() throws IOException {
        new BuildExecutorFileCache(cacheRoot)
                .touch(Runnable::run, "step", new byte[]{7}, inputs("source", "file", new byte[]{9}), "SHA-256");
        assertThat(steps().resolve(HexFormat.of().formatHex(new byte[]{7}))).doesNotExist();
    }

    @Test
    public void reads_step_limit_from_cache_properties() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "steps=1\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        store(cache, new byte[]{1}, new byte[]{9});
        Path a = steps().resolve(HexFormat.of().formatHex(new byte[]{1}));
        Files.setLastModifiedTime(a, FileTime.from(Instant.now().minusSeconds(300)));
        store(cache, new byte[]{2}, new byte[]{9});
        assertThat(a).doesNotExist();
        assertThat(steps().resolve(HexFormat.of().formatHex(new byte[]{2}))).exists();
    }

    @Test
    public void write_disabled_serves_reads_without_touching() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = store(new BuildExecutorFileCache(cacheRoot), step, new byte[]{9});
        Files.writeString(cacheRoot.resolve("cache.properties"), "write=false\n");
        Files.setLastModifiedTime(entry, FileTime.from(Instant.now().minusSeconds(300)));
        FileTime before = Files.getLastModifiedTime(entry);
        Optional<Map<Path, byte[]>> result = new BuildExecutorFileCache(cacheRoot)
                .fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256");
        assertThat(result).isPresent();
        assertThat(target.resolve("file")).content().isEqualTo("x");
        assertThat(Files.getLastModifiedTime(entry)).isEqualTo(before);
    }

    @Test
    public void reads_write_flag_from_cache_properties() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "write=false\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(steps().resolve(HexFormat.of().formatHex(new byte[]{1}))).doesNotExist();
    }

    @Test
    public void write_disabled_does_not_store() throws IOException {
        Files.writeString(cacheRoot.resolve("cache.properties"), "write=false\n");
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        byte[] step = {1};
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", step, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(steps().resolve(HexFormat.of().formatHex(step))).doesNotExist();
    }

    @Test
    public void read_disabled_does_not_fetch() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        store(new BuildExecutorFileCache(cacheRoot), step, new byte[]{9});
        Files.writeString(cacheRoot.resolve("cache.properties"), "read=false\n");
        Optional<Map<Path, byte[]>> result = new BuildExecutorFileCache(cacheRoot)
                .fetch(Runnable::run, "step", step, in, new LinkedHashMap<>(), target, "SHA-256");
        assertThat(result).isEmpty();
    }

    @Test
    public void folds_inputs_independent_of_separator() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Files.writeString(output.resolve("file"), "x");
        byte[] step = {1};
        cache.store(Runnable::run, "step", step, inputPath(Path.of("a", "b")), output, "SHA-256", Map.of());
        assertThat(cache.fetch(Runnable::run, "step", step, inputPath(Path.of("a\\b")), new LinkedHashMap<>(), target, "SHA-256")).isPresent();
    }

    @Test
    public void folds_inputs_independent_of_file_iteration_order() throws IOException {
        BuildExecutorFileCache cache = new BuildExecutorFileCache(cacheRoot);
        Files.writeString(output.resolve("file"), "result");
        byte[] step = {1};
        Map<Path, byte[]> forward = new LinkedHashMap<>();
        forward.put(Path.of("a"), new byte[]{1});
        forward.put(Path.of("b"), new byte[]{2});
        SequencedMap<String, Map<Path, byte[]>> stored = new LinkedHashMap<>();
        stored.put("source", forward);
        cache.store(Runnable::run, "step", step, stored, output, "SHA-256", Map.of());

        Map<Path, byte[]> reversed = new LinkedHashMap<>();
        reversed.put(Path.of("b"), new byte[]{2});
        reversed.put(Path.of("a"), new byte[]{1});
        SequencedMap<String, Map<Path, byte[]>> fetched = new LinkedHashMap<>();
        fetched.put("source", reversed);
        assertThat(cache.fetch(Runnable::run, "step", step, fetched, new LinkedHashMap<>(), target, "SHA-256")).isPresent();
        assertThat(target.resolve("file")).content().isEqualTo("result");
    }

    private static SequencedMap<String, Map<Path, byte[]>> inputPath(Path path) {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        files.put(path, new byte[]{9});
        SequencedMap<String, Map<Path, byte[]>> inputs = new LinkedHashMap<>();
        inputs.put("source", files);
        return inputs;
    }

    private Path store(BuildExecutorFileCache cache, byte[] step, byte[] inputHash) throws IOException {
        Files.deleteIfExists(output.resolve("file"));
        Files.writeString(output.resolve("file"), "x");
        Path folder = steps().resolve(HexFormat.of().formatHex(step));
        Set<Path> before = children(folder);
        cache.store(Runnable::run, "step", step, inputs("source", "file", inputHash), output, "SHA-256", Map.of());
        Set<Path> after = children(folder);
        after.removeAll(before);
        return after.isEmpty() ? null : after.iterator().next();
    }

    private Path storeSized(BuildExecutorFileCache cache, byte[] step, int bytes) throws IOException {
        Files.write(output.resolve("file"), new byte[bytes]);
        cache.store(Runnable::run, "step", step, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        String hex = HexFormat.of().formatHex(hash.hash(output.resolve("file")));
        return cacheRoot.resolve("sha-256").resolve("blobs").resolve(hex.substring(0, 2)).resolve(hex.substring(2));
    }

    private Path manifest(byte[] step) throws IOException {
        try (Stream<Path> entries = Files.list(steps().resolve(HexFormat.of().formatHex(step)))) {
            return entries.findFirst().orElseThrow();
        }
    }

    private Path steps() {
        return cacheRoot.resolve("sha-256").resolve("steps");
    }

    private Path blob(String content) {
        String hex = hex(content);
        return cacheRoot.resolve("sha-256").resolve("blobs").resolve(hex.substring(0, 2)).resolve(hex.substring(2));
    }

    private static String hex(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static Set<Path> children(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return new HashSet<>();
        }
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.collect(Collectors.toCollection(HashSet::new));
        }
    }

    private static SequencedMap<String, Map<Path, byte[]>> inputs(String argument, String file, byte[] hash) {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        files.put(Path.of(file), hash);
        SequencedMap<String, Map<Path, byte[]>> inputs = new LinkedHashMap<>();
        inputs.put(argument, files);
        return inputs;
    }
}
