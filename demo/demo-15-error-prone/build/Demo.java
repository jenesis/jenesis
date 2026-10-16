package build;

import module java.base;

public class Demo {

    static void main(String[] args) throws Exception {
        wipe();
        if (!make("-Djenesis.make.profiles=errorprone").contains("[ReferenceEquality]")) {
            throw new AssertionError("The build with Error Prone was expected to fail on the reference comparison");
        }
        System.out.println("[blocked] a reference comparison of two strings, with ReferenceEquality promoted to an error");
        System.out.println();
        wipe();
        if (!make().contains("[COMPLETED]")) {
            throw new AssertionError("The build without Error Prone was expected to compile the same sources");
        }
        System.out.println();
        System.out.println("Error Prone blocked the build; javac alone compiles the same sources.");
    }

    private static String make(String... flags) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElseGet(() -> Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe"
                        : "java").toString());
        List<String> command = new ArrayList<>(List.of(java));
        command.addAll(List.of(flags));
        command.add("build/jenesis/Make.java");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = process.inputReader()) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                System.out.println(line);
                output.append(line).append('\n');
            }
        }
        process.waitFor();
        return output.toString();
    }

    private static void wipe() throws IOException {
        Path target = Path.of("target");
        if (!Files.isDirectory(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
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
