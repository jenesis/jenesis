package build;

import module java.base;
import build.jenesis.Make;

/**
 * Builds this module and compares the SHA-256 of the jar it produced with the digest
 * recorded below. The digest was taken once, on one machine; CI runs this demo on
 * Linux, macOS and Windows, each with whatever update of JDK 25 its runner provides,
 * so a match on every runner shows that nothing about the machine, its operating
 * system, its time zone or the moment of the build reaches the jar. Run it from this
 * directory:
 *
 *     java build/Demo.java
 *
 * which builds the jar and prints:
 *
 *     classes.jar has the recorded SHA-256 ...
 */
public class Demo {

    private static final String EXPECTED = "e90d68a2bc7492b27c031c2235123e3a20a732cc8e5564f9a2d143d36f8a534a";

    static void main(String[] args) throws Exception {
        if (new Make("build.jenesis.Project").build().code() != 0) {
            throw new IllegalStateException("The build exited with a non-zero status");
        }

        Path jar;
        try (Stream<Path> walk = Files.walk(Path.of("target"))) {
            jar = walk.filter(path -> path.getFileName().toString().equals("classes.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No classes.jar was produced"));
        }

        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        if (!digest.equals(EXPECTED)) {
            throw new IllegalStateException("classes.jar has SHA-256 " + digest + " where " + EXPECTED
                    + " was recorded: something about this build reached the jar, and `unzip -Z -v "
                    + jar + "` next to the same listing from another machine shows what");
        }
        System.out.println("classes.jar has the recorded SHA-256 " + digest);
    }
}
