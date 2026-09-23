package build;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.Project;
import build.jenesis.Environment;
import build.jenesis.Make;

public class Demo {

    static void main(String[] args) throws Exception {
        built("pinned: a dependency pinned to a version and a checksum", true, "pinned", null);

        built("unpinned: a version-only dependency builds by default", true, "unpinned", null);

        built("unpinned: the same dependency under strict pinning", false, "unpinned", Pinning.STRICT);

        built("tampered: a dependency whose pinned checksum does not match", false, "tampered", null);

        System.out.println();
        System.out.println("Strict pinning decides whether an unverifiable dependency may be used;");
        System.out.println("the checksum decides whether the bytes are the ones that were vetted.");
    }

    private static void built(String description, boolean success, String project, Pinning pinning)
            throws Exception {
        wipe(project);
        try {
            Project.ofEnvironment(new Environment(Make.settings(Path.of(project)).keys()), Path.of(project)).pinning(pinning).build();
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

    private static void wipe(String project) throws IOException {
        Path target = Path.of(project, "target");
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
