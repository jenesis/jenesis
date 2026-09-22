package build.jenesis;

import module java.base;

abstract class JenesisTool implements ToolProvider {

    @Override
    public int run(PrintWriter out, PrintWriter err, String... arguments) {
        SequencedMap<String, String> settings = new LinkedHashMap<>();
        int index = 0;
        while (index < arguments.length && arguments[index].startsWith("-D")) {
            String assignment = arguments[index++].substring(2);
            int equals = assignment.indexOf('=');
            String key = equals < 0 ? assignment : assignment.substring(0, equals);
            if (!key.startsWith("jenesis.")) {
                err.println("Not a Jenesis setting: -D" + assignment
                        + " (a tool invocation configures the build it runs, so only jenesis.* is accepted)");
                err.flush();
                return 1;
            }
            settings.put(key, equals < 0 ? "" : assignment.substring(equals + 1));
        }
        List<String> remaining = List.of(arguments).subList(index, arguments.length);
        PrintStream systemOut = System.out, systemErr = System.err;
        System.setOut(new PrintStream(new WriterStream(out), true));
        System.setErr(new PrintStream(new WriterStream(err), true));
        try {
            return run(key -> settings.get("jenesis." + key), remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(name() + " was interrupted running " + String.join(" ", remaining));
            return 1;
        } catch (IOException e) {
            err.println(name() + " failed: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println(e.getMessage());
            return 1;
        } finally {
            System.out.flush();
            System.err.flush();
            System.setOut(systemOut);
            System.setErr(systemErr);
            out.flush();
            err.flush();
        }
    }

    protected abstract int run(Function<String, String> requested, List<String> arguments)
            throws IOException, InterruptedException;

    protected Path root(Function<String, String> requested) {
        return Path.of(SequencedProperties.getProperty(requested, "make.root", "")).toAbsolutePath().normalize();
    }

    protected void requireInProcess(Function<String, String> requested) {
        if (requested.apply("toolchain.version") != null) {
            throw new IllegalStateException("jenesis.toolchain.version cannot be honored by the "
                    + name() + " tool, because a toolchain replaces the JVM a build runs on"
                    + " - run the " + name() + " command instead");
        }
        if (SequencedProperties.flag(requested, "project.docker")) {
            throw new IllegalStateException("A dockerized build cannot be run by the "
                    + name() + " tool, because a container replaces the process a build runs in"
                    + " - unset jenesis.project.docker, or run the " + name() + " command instead");
        }
    }

    private static final class WriterStream extends OutputStream {

        private final Writer writer;
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        private WriterStream(Writer writer) {
            this.writer = writer;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            pending.write(bytes, offset, length);
            for (int index = offset; index < offset + length; index++) {
                if (bytes[index] == '\n') {
                    flush();
                    return;
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if (pending.size() == 0) {
                return;
            }
            writer.write(pending.toString(StandardCharsets.UTF_8));
            pending.reset();
            writer.flush();
        }
    }
}
