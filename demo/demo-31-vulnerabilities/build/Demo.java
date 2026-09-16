package build;

import module java.base;
import build.jenesis.Make;

public class Demo {

    static void main(String[] args) throws Exception {
        expectFailure("log4j-core 2.14.1's critical Log4Shell advisory at the configured severity threshold");
        System.out.println();
        System.out.println("The vulnerability check blocked the build, as expected.");
    }

    private static void expectFailure(String description) throws Exception {
        wipe();
        // The tool as the command line runs it, so what is asserted is the status a shell would see.
        if (new Make("build.jenesis.Project").build().code() != 0) {
            System.out.println("[blocked] " + description);
            return;
        }
        throw new AssertionError("Build was expected to fail but succeeded: " + description);
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
