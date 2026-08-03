package build;

import module java.base;

/**
 * Drives both agent attachments end to end.
 *
 * First it runs the ordinary build, whose test step attaches Mockito as a
 * {@code -javaagent} to the test JVM (declared in {@code test/module-info.java}
 * next to {@code @jenesis.test}). Then it runs the application through
 * {@code build.jenesis.Execute}, which attaches the OpenTelemetry agent declared
 * in {@code sources/module-info.java} next to {@code @jenesis.main}. The
 * OpenTelemetry exporters are switched off through the standard {@code OTEL_*}
 * environment variables so the run stays quiet - the agent still logs its
 * version banner, which is the proof that it attached.
 *
 * Run it from this directory, forwarding any arguments to the application:
 *
 *     java build/Demo.java Ada Lovelace
 */
public class Demo {

    static void main(String[] arguments) throws Exception {
        String java = ProcessHandle.current().info().command().orElseGet(() -> Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe"
                        : "java").toString());

        System.out.println("== Building and testing (Mockito attaches to the test JVM) ==");
        run(new ProcessBuilder(java, "build/jenesis/Project.java"));

        System.out.println();
        System.out.println("== Running the application (OpenTelemetry attaches to the app JVM) ==");
        List<String> command = new ArrayList<>(List.of(java, "build/jenesis/Execute.java"));
        command.addAll(List.of(arguments));
        ProcessBuilder application = new ProcessBuilder(command);
        application.environment().put("OTEL_TRACES_EXPORTER", "none");
        application.environment().put("OTEL_METRICS_EXPORTER", "none");
        application.environment().put("OTEL_LOGS_EXPORTER", "none");
        application.environment().put("OTEL_SERVICE_NAME", "demo-agents");
        run(application);
    }

    private static void run(ProcessBuilder builder) throws IOException, InterruptedException {
        if (builder.inheritIO().start().waitFor() != 0) {
            throw new IllegalStateException("Command exited with a non-zero status: " + builder.command());
        }
    }
}
