package build;

import module java.base;

public class Demo {

    static void main(String[] args) throws Exception {
        Path source = Path.of("sources", "calc", "Adder.java"),
                subtractor = Path.of("sources", "calc", "Subtractor.java");
        Path ran = Path.of("target", "ran");
        Path unreached = Path.of("sources", "calc", "Multiplier.java");
        String original = Files.readString(source), subtracting = Files.readString(subtractor);
        try {
            build("incremental");
            require(ran, "AdderTest", true);
            require(ran, "SubtractorTest", true);
            System.out.println("First build ran both tests.");

            delete(ran);
            Files.writeString(source, original.replace(
                    "return left + right;",
                    "int sum = left + right;\n        return sum;"));
            build("incremental");
            require(ran, "AdderTest", true);
            require(ran, "SubtractorTest", false);
            System.out.println("Editing Adder re-ran only AdderTest; SubtractorTest stayed cached.");

            delete(ran);
            Files.writeString(unreached, """
                    package calc;

                    public class Multiplier {

                        public int multiply(int left, int right) {
                            return left * right;
                        }
                    }
                    """);
            build("incremental");
            require(ran, "AdderTest", false);
            require(ran, "SubtractorTest", false);
            System.out.println("Adding Multiplier, which no test reaches, ran no test.");

            delete(ran);
            Files.writeString(subtractor, subtracting.replace(
                    "return left - right;",
                    "int difference = left - right;\n        return difference;"));
            build("prioritized");
            List<String> order = Files.readAllLines(ran.resolve("order"));
            if (!order.equals(List.of("SubtractorTest", "AdderTest"))) {
                throw new AssertionError("SubtractorTest was expected to run first and AdderTest after it, but the"
                        + " tests ran as " + order);
            }
            System.out.println("Editing Subtractor in prioritized mode ran SubtractorTest first, then AdderTest.");
        } finally {
            Files.writeString(source, original);
            Files.writeString(subtractor, subtracting);
            Files.deleteIfExists(unreached);
        }
    }

    private static void build(String mode) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElseGet(() -> Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString());
        Process process = new ProcessBuilder(
                java,
                "-Djenesis.test.mode=" + mode,
                "build/jenesis/Make.java")
                .inheritIO()
                .start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Build exited with a non-zero status");
        }
    }

    private static void require(Path directory, String marker, boolean expected) {
        if (Files.exists(directory.resolve(marker)) != expected) {
            throw new AssertionError(expected
                    ? marker + " was expected to run but did not"
                    : marker + " was expected to stay cached but ran");
        }
    }

    private static void delete(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }
}
