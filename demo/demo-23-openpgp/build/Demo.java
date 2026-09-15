package build;

import module java.base;

public class Demo {

    static void main(String[] args) throws Exception {
        if (located("gpgv") == null) {
            System.out.println("[skipped] gpgv is not installed, so no signature can be checked here");
            return;
        }

        // A pin says the bytes did not change since they were vetted. It cannot say who produced
        // them, because it was taken from whatever the repository served. @jenesis.signature names
        // the key that signs a coordinate, and the build fetches that key by fingerprint, assembles
        // a keyring from the fingerprints declared, and asks gpgv to check the detached signature
        // published beside each artifact.
        built("declared: the key that signed the dependency is the key declared for it",
                true, "declared", "declared");

        // The signature upstream published is perfectly valid, so only the comparison against the
        // declaration catches a different signer. That is what naming the key buys over a checksum.
        built("rotated: a different key is declared for the same dependency",
                false, "rotated", "declared");

        // One vetted list can serve many modules. It is only ever read from disk: a list you had to
        // download would itself need verifying.
        built("vendored: the same key, read from a list rather than a line",
                true, "vendored", "declared");

        // declared verifies what is declared and claims nothing about the rest. strict refuses a
        // coordinate no line covers - here the transitive dependency the library brings - because
        // one unvetted coordinate otherwise costs the whole mode.
        built("declared: under strict, a transitive dependency nothing vouches for",
                false, "declared", "strict");

        // Declaring a key for every coordinate in the closure is what strict asks for.
        built("complete: a key declared for everything the closure resolves",
                true, "complete", "strict");

        System.out.println();
        System.out.println("A checksum proves the bytes are the ones that were vetted; a key proves");
        System.out.println("who produced them. Verification is opt-in, and strict is the mode that");
        System.out.println("leaves no coordinate unaccounted for.");
    }

    private static void built(String description, boolean success, String project, String verification)
            throws Exception {
        List<String> command = List.of(System.getProperty("java.home") + "/bin/java",
                "-Djenesis.dependency.signature=" + verification,
                "-Djenesis.print.signatures=true",
                "-Djenesis.test.skip",
                "build/jenesis/Make.java",
                "build");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.directory(Path.of(project).toFile());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean built = process.waitFor() == 0;
        if (built != success) {
            throw new AssertionError("Expected the build to "
                    + (success ? "succeed" : "fail")
                    + ": "
                    + description
                    + "\n"
                    + output);
        }
        output.lines()
                .map(line -> line.replaceAll("\\[[0-9;]*m", "").trim())
                .filter(line -> line.startsWith("[VERIFIED]") || line.startsWith("main/maven"))
                .forEach(line -> System.out.println("          " + line));
        System.out.println((success ? "[ok]      " : "[blocked] ") + description);
    }

    private static Path located(String command) {
        String path = System.getenv("PATH");
        for (String folder : path == null ? new String[0] : path.split(File.pathSeparator)) {
            Path candidate = Path.of(folder).resolve(command);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
