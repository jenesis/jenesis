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

    // The jar's digest as recorded when the demo was written. A deliberate change to the
    // sources, or to what Jenesis writes into a jar, changes it, and this line with it.
    private static final String EXPECTED = "e0be85154f2ddea0e0ce464cd3a548c12a4b5ed154c8a1ad08db8d6925f96186";

    static void main(String[] args) throws Exception {
        // The tool as the command line runs it: a non-zero status is the failure a shell would see.
        if (new Make("build.jenesis.Project").build().code() != 0) {
            throw new IllegalStateException("The build exited with a non-zero status");
        }

        // The archiver writes the module's jar under .../artifacts/jar/output/artifacts/classes.jar.
        Path jar;
        try (Stream<Path> walk = Files.walk(Path.of("target"))) {
            jar = walk.filter(path -> path.getFileName().toString().equals("classes.jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No classes.jar was produced"));
        }

        // Every byte counts: the entry order, the time recorded on each entry, the manifest, the
        // compiled declaration and the embedded SBOM all have to come out the same.
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        if (!digest.equals(EXPECTED)) {
            throw new IllegalStateException("classes.jar has SHA-256 " + digest + " where " + EXPECTED
                    + " was recorded: something about this build reached the jar, and `unzip -Z -v "
                    + jar + "` next to the same listing from another machine shows what");
        }
        System.out.println("classes.jar has the recorded SHA-256 " + digest);
    }
}
