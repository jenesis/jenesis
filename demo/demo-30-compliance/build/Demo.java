package build;

import module java.base;
import build.jenesis.Make;

public class Demo {

    static void main(String[] args) throws Exception {
        expectFailure("a GPL dependency under a permissive-only license policy");
        System.out.println();
        System.out.println("The license check blocked the build, as expected.");
    }

    private static void expectFailure(String description) throws Exception {
        wipe();
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
