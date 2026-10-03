package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildExecutorCallback {

    String RESET = "\033[0m";
    String RED = "\033[31m";
    String GREEN = "\033[32m";
    String YELLOW = "\033[33m";
    String BLUE = "\033[34m";
    String CYAN = "\033[36m";

    record Provenance(String run, Instant time, boolean loaded, boolean failed, SequencedMap<String, String> summary) {
    }

    BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys);

    default void run(String id) {
    }

    default void recorded(String identity, Provenance provenance) {
    }

    default Consumer<Throwable> module(String identity) {
        return _ -> {
        };
    }

    default void loaded(String identity, long duration) {
    }

    default void stored(String identity, long duration) {
    }

    default BuildExecutorCallback andThen(BuildExecutorCallback other) {
        BuildExecutorCallback first = this;
        return new BuildExecutorCallback() {
            @Override
            public BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys) {
                return first.step(identity, keys).andThen(other.step(identity, keys));
            }

            @Override
            public void run(String id) {
                first.run(id);
                other.run(id);
            }

            @Override
            public void recorded(String identity, Provenance provenance) {
                first.recorded(identity, provenance);
                other.recorded(identity, provenance);
            }

            @Override
            public Consumer<Throwable> module(String identity) {
                return first.module(identity).andThen(other.module(identity));
            }

            @Override
            public void loaded(String identity, long duration) {
                first.loaded(identity, duration);
                other.loaded(identity, duration);
            }

            @Override
            public void stored(String identity, long duration) {
                first.stored(identity, duration);
                other.stored(identity, duration);
            }
        };
    }

    static BuildExecutorCallback nop() {
        return (_, _) -> (_, _) -> {
        };
    }

    static BuildExecutorCallback printing(Consumer<String> out, boolean verbose, boolean cache, Path target) {
        return printing(out, verbose, cache, target, false);
    }

    static BuildExecutorCallback printing(Consumer<String> out,
                                          boolean verbose,
                                          boolean cache,
                                          Path target,
                                          boolean events) {
        return new BuildExecutorCallback() {

            private final SequencedMap<String, Provenance> summarized = new TreeMap<>();
            private volatile String run;

            @Override
            public void run(String id) {
                run = id;
            }

            @Override
            public void recorded(String identity, Provenance provenance) {
                if (provenance.summary() != null) {
                    synchronized (summarized) {
                        summarized.put(identity, provenance);
                    }
                }
            }

            @Override
            public BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys) {
                long started = System.nanoTime();
                if (identity == null) {
                    synchronized (summarized) {
                        summarized.clear();
                    }
                    out.accept("%s%-11s%s Building in '%s'%s...".formatted(GREEN, "[STARTED]", RESET, target,
                            run == null ? "" : " as run " + run));
                    if (events) {
                        out.accept("%s%-11s%s Recording each step's outcome as a JSON line in '%s'".formatted(
                                GREEN, "[EVENTS]", RESET, target.resolve(BuildExecutor.EVENTS)));
                    }
                    return (_, throwable) -> {
                        synchronized (summarized) {
                            int width = summarized.sequencedKeySet().stream().mapToInt(String::length).max().orElse(0);
                            summarized.forEach((step, provenance) -> {
                                String scope = provenance.summary().get("scope"), result = provenance.summary().get("result");
                                out.accept("%s%-11s%s %s  %s%s%s".formatted(
                                        provenance.failed() ? RED : CYAN, "[SUMMARY]", RESET,
                                        step + " ".repeat(width - step.length()),
                                        provenance.failed() ? "failed now"
                                                : !Objects.equals(provenance.run(), run) ? (provenance.time() == null
                                                ? "reused from an earlier run"
                                                : "reused from the run at " + moment(provenance.time()))
                                                : provenance.loaded() ? "loaded from the build cache" : "executed now",
                                        scope == null ? "" : " (" + scope + ")",
                                        result == null ? "" : ": " + result));
                            });
                        }
                        double time = ((double) (System.nanoTime() - started) / 1_000_000) / 1_000;
                        out.accept("%s%-11s%s Finished %sin %.2f seconds%s".formatted(
                                throwable == null ? GREEN : RED,
                                throwable == null ? "[COMPLETED]" : "[FAILED]",
                                RESET,
                                CYAN,
                                time,
                                RESET));
                    };
                }
                return (executed, throwable) -> {
                    if (throwable != null) {
                        out.accept("%s%-11s%s %s: %s".formatted(RED, "[FAILED]", RESET, identity,
                                String.valueOf(throwable instanceof BuildExecutorException
                                        ? throwable.getCause().getMessage()
                                        : throwable.getMessage()).lines().findFirst().orElse("")));
                    } else if (executed) {
                        double time = ((double) (System.nanoTime() - started) / 1_000_000) / 1_000;
                        synchronized (out) {
                            out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                                    GREEN, "[EXECUTED]", RESET, identity, CYAN, time, RESET));
                            if (verbose) {
                                Path checksums = target.resolve(identity)
                                        .resolve("checksum")
                                        .resolve("output.properties");
                                if (Files.isRegularFile(checksums)) {
                                    try {
                                        HashFunction.read(checksums).forEach((file, hash) -> out.accept(
                                                "            %s  %s".formatted(
                                                        HexFormat.of().formatHex(hash),
                                                        file)));
                                    } catch (IOException e) {
                                        out.accept("            Failed to list files: %s".formatted(e.getMessage()));
                                    }
                                }
                            }
                        }
                    } else {
                        out.accept("%s%-11s%s %s".formatted(BLUE, "[SKIPPED]", RESET, identity));
                    }
                };
            }

            @Override
            public Consumer<Throwable> module(String identity) {
                long started = System.nanoTime();
                return throwable -> {
                    if (throwable == null) {
                        double time = ((double) (System.nanoTime() - started) / 1_000_000) / 1_000;
                        out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                                GREEN, "[RESOLVED]", RESET, identity, CYAN, time, RESET));
                    }
                };
            }

            @Override
            public void loaded(String identity, long duration) {
                if (cache) {
                    out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                            YELLOW, "[LOADED]", RESET, identity, CYAN, ((double) duration / 1_000_000) / 1_000, RESET));
                }
            }

            @Override
            public void stored(String identity, long duration) {
                if (cache) {
                    out.accept("%s%-11s%s %s %sin %.2f seconds%s".formatted(
                            YELLOW, "[STORED]", RESET, identity, CYAN, ((double) duration / 1_000_000) / 1_000, RESET));
                }
            }
        };
    }

    static BuildExecutorCallback events(Path target) {
        Path root = target.toAbsolutePath().normalize(), file = root.resolve(BuildExecutor.EVENTS);
        return new BuildExecutorCallback() {

            private final Map<String, Provenance> recorded = new HashMap<>();
            private Writer writer;
            private int executed, skipped, failed;
            private String run;

            @Override
            public synchronized void run(String id) {
                run = id;
            }

            @Override
            public synchronized void recorded(String identity, Provenance provenance) {
                recorded.put(identity, provenance);
            }

            @Override
            public BiConsumer<Boolean, Throwable> step(String identity, SequencedSet<String> keys) {
                long started = System.nanoTime();
                if (identity == null) {
                    synchronized (this) {
                        close();
                        try {
                            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Cannot write the build events to " + file, e);
                        }
                        executed = 0;
                        skipped = 0;
                        failed = 0;
                        recorded.clear();
                        write("{\"status\":\"started\""
                                + (run == null ? "" : ",\"run\":\"" + Json.escaped(run) + "\"")
                                + ",\"target\":\"" + Json.escaped(root.toString()) + "\"}");
                    }
                    return (_, throwable) -> {
                        synchronized (this) {
                            write("{\"status\":\"" + (throwable == null ? "completed" : "failed") + "\""
                                    + ",\"seconds\":" + seconds(System.nanoTime() - started)
                                    + ",\"executed\":" + executed
                                    + ",\"skipped\":" + skipped
                                    + ",\"failed\":" + failed
                                    + (throwable == null ? "" : failure(throwable)) + "}");
                            close();
                        }
                    };
                }
                String step = ",\"step\":\"" + Json.escaped(identity) + "\"",
                        folder = ",\"folder\":\"" + Json.escaped(root.resolve(identity).toString()) + "\"";
                return (ran, throwable) -> {
                    synchronized (this) {
                        Provenance provenance = recorded.remove(identity);
                        StringBuilder produced = new StringBuilder();
                        if (provenance != null && provenance.run() != null) {
                            produced.append(",\"run\":\"").append(Json.escaped(provenance.run())).append('"');
                        }
                        if (provenance != null && provenance.time() != null) {
                            produced.append(",\"time\":\"").append(provenance.time()).append('"');
                        }
                        if (provenance != null && provenance.summary() != null && !provenance.summary().isEmpty()) {
                            produced.append(",\"summary\":{").append(provenance.summary().entrySet().stream()
                                    .map(entry -> "\"" + Json.escaped(entry.getKey()) + "\":\"" + Json.escaped(entry.getValue()) + "\"")
                                    .collect(Collectors.joining(","))).append('}');
                        }
                        if (throwable != null) {
                            failed++;
                            write("{\"status\":\"failed\"" + step + failure(throwable) + produced
                                    + ",\"folder\":\"" + Json.escaped(root.resolve(identity + "~").toString()) + "\"}");
                        } else if (ran) {
                            executed++;
                            write("{\"status\":\"executed\"" + step + ",\"seconds\":"
                                    + seconds(System.nanoTime() - started) + produced + folder + "}");
                        } else {
                            skipped++;
                            write("{\"status\":\"skipped\"" + step + produced + folder + "}");
                        }
                    }
                };
            }

            @Override
            public Consumer<Throwable> module(String identity) {
                long started = System.nanoTime();
                return throwable -> write("{\"status\":\"" + (throwable == null ? "resolved" : "failed") + "\""
                        + ",\"module\":\"" + Json.escaped(identity) + "\""
                        + (throwable == null
                        ? ",\"seconds\":" + seconds(System.nanoTime() - started)
                        : failure(throwable)) + "}");
            }

            @Override
            public void loaded(String identity, long duration) {
                write("{\"status\":\"loaded\",\"step\":\"" + Json.escaped(identity) + "\",\"seconds\":"
                        + seconds(duration) + "}");
            }

            @Override
            public void stored(String identity, long duration) {
                write("{\"status\":\"stored\",\"step\":\"" + Json.escaped(identity) + "\",\"seconds\":"
                        + seconds(duration) + "}");
            }

            private synchronized void write(String line) {
                if (writer == null) {
                    return;
                }
                try {
                    writer.write(line);
                    writer.write('\n');
                    writer.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot write the build events to " + file, e);
                }
            }

            private synchronized void close() {
                if (writer == null) {
                    return;
                }
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot write the build events to " + file, e);
                } finally {
                    writer = null;
                }
            }
        };
    }

    private static String moment(Instant time) {
        ZonedDateTime local = time.atZone(ZoneId.systemDefault());
        return local.toLocalDate().equals(LocalDate.now(ZoneId.systemDefault()))
                ? local.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                : local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String seconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000_000d);
    }

    private static String failure(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof BuildExecutorException || cause instanceof CompletionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return ",\"error\":\"" + cause.getClass().getName() + "\",\"message\":\""
                + Json.escaped(cause.getMessage() == null ? cause.toString() : cause.getMessage()) + "\"";
    }
}
