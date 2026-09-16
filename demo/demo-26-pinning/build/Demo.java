package build;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.Project;

public class Demo {

    static void main(String[] args) throws Exception {
        // A pin records the version and the SHA-256 of the bytes that version served, in the
        // project's own sources. Every build re-hashes what it downloads and compares.
        built("pinned: a dependency pinned to a version and a checksum", true, "pinned", null);

        // A version without a checksum builds by default: the version is all that was asked
        // for, and the version is what arrived.
        built("unpinned: a version-only dependency builds by default", true, "unpinned", null);

        // Under strict pinning it does not. There is nothing to verify the download against,
        // which is what a hardened build refuses - one unpinned coordinate otherwise costs
        // the whole guarantee.
        built("unpinned: the same dependency under strict pinning", false, "unpinned", Pinning.STRICT);

        // A checksum that does not match fails without strict pinning being asked for at all:
        // the comparison happens on every download, and a mismatch is what a swapped artifact
        // looks like from here.
        built("tampered: a dependency whose pinned checksum does not match", false, "tampered", null);

        System.out.println();
        System.out.println("Strict pinning decides whether an unverifiable dependency may be used;");
        System.out.println("the checksum decides whether the bytes are the ones that were vetted.");
    }

    private static void built(String description, boolean success, String project, Pinning pinning)
            throws Exception {
        wipe(project);
        try {
            new Project(Path.of(project)).pinning(pinning).build();
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
