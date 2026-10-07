package build;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Pinning;
import build.jenesis.Project;

public class Demo {

    static void main(String[] args) throws Exception {
        built("pinned: a dependency pinned to a version and a checksum", true, "pinned", null, Map.of());

        built("unpinned: a version-only dependency builds by default", true, "unpinned", null, Map.of());

        built("unpinned: the same dependency under strict pinning", false, "unpinned", Pinning.STRICT, Map.of());

        built("tampered: a dependency whose pinned checksum does not match", false, "tampered", null, Map.of());

        built("pinned: rebuilt offline from the artifacts it stored", true, "pinned", null,
                Map.of("repository.offline", "true"));

        wipe(Path.of(".jenesis", "artifacts"));
        built("pinned: offline, with nothing stored to build from", false, "pinned", null,
                Map.of("repository.offline", "true", "maven.local", "target/empty"));

        System.out.println();
        System.out.println("Strict pinning decides whether an unverifiable dependency may be used;");
        System.out.println("the checksum decides whether the bytes are the ones that were vetted.");
        System.out.println("Once they are stored, a build needs no network at all.");
    }

    private static void built(String description,
                              boolean success,
                              String project,
                              Pinning pinning,
                              Map<String, String> settings) throws Exception {
        wipe(Path.of("target"));
        Files.createDirectories(Path.of("target", "empty"));
        try {
            Map<String, String> keys = new HashMap<>(Make.settings(Path.of(project)).keys());
            keys.putAll(settings);
            Environment environment = new Environment(keys);
            Project.ofEnvironment(environment, Path.of(project)).pinning(pinning).build();
        } catch (Throwable _) {
            if (success) {
                throw new AssertionError("Expected the build to succeed: " + description);
            }
            System.out.println("[blocked] " + description);
            return;
        }
        if (!success) {
            throw new AssertionError("Expected the build to fail: " + description);
        }
        System.out.println("[ok]      " + description);
    }

    private static void wipe(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
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
