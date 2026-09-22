package build.jenesis;

import module java.base;

abstract class JenesisTool implements ToolProvider {

    @Override
    public int run(PrintWriter out, PrintWriter err, String... arguments) {
        SequencedMap<String, String> settings = new LinkedHashMap<>();
        List<String> remaining;
        try {
            remaining = List.of(Make.partitioned(arguments, settings));
        } catch (IOException | RuntimeException e) {
            err.println(e.getMessage());
            err.flush();
            return 1;
        }
        try {
            return run(key -> settings.get("jenesis." + key), new Output(out, err), remaining);
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
            out.flush();
            err.flush();
        }
    }

    protected abstract int run(Function<String, String> requested, Output output, List<String> arguments)
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
}
