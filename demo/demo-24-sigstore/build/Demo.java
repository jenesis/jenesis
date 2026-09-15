package build;

import module java.base;

public class Demo {

    static void main(String[] args) throws Exception {
        // Sigstore answers who produced an artifact without anyone holding a key. The signer
        // authenticates to an identity provider, a certificate authority issues a certificate that
        // is valid for ten minutes and names that identity, the signature is recorded in a public
        // append-only log, and the private key is discarded. What a build verifies afterwards is an
        // identity and a log entry, both carried in a bundle published beside the artifact.
        built("attested: a dependency verified against the identity its declaration names",
                true, "attested", "declared", null);

        // The bundle is genuine and the log entry is real, so only the comparison against the
        // declaration catches a release built somewhere else.
        built("forked: the same bundle, declared to come from a different repository",
                false, "forked", "declared", null);

        // Under strict a coordinate's POM must carry a bundle from the same identity, which closes
        // the gap that POMs are read during resolution but never pinned.
        built("attested: under strict, the POM carries a bundle from that identity too",
                true, "attested", "strict", null);

        // Nothing above configures a trust root: the tool carries the published root of the public
        // Sigstore instance as source. Naming another replaces it wholesale, which is how a private
        // instance is reached - and how a build stops when what it names vouches for nothing.
        Path vouching = Files.writeString(Files.createDirectories(Path.of("target"))
                        .resolve("vouches-for-nothing.json"),
                "{\"certificateAuthorities\":[],\"tlogs\":[]}");
        built("named: the same build against a trust root that vouches for nothing",
                false, "attested", "declared", vouching.toUri());

        System.out.println();
        System.out.println("No key was fetched, no keyring assembled and no gpg forked: a bundle");
        System.out.println("carries its own certificate and log entry, and the trust root to check");
        System.out.println("them against ships with the tool.");
    }

    private static void built(String description,
                              boolean success,
                              String project,
                              String verification,
                              URI trustedRoot) throws Exception {
        List<String> command = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java",
                "-Djenesis.dependency.signature=" + verification,
                "-Djenesis.print.signatures=true",
                "-Djenesis.test.skip"));
        if (trustedRoot != null) {
            command.add("-Djenesis.sigstore.uri=" + trustedRoot);
        }
        command.addAll(List.of("build/jenesis/Make.java", "build"));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.directory(Path.of(project).toFile());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean built = process.waitFor() == 0;
        if (built != success) {
            if (success && output.contains("UnknownHostException")) {
                System.out.println("[skipped] a published bundle could not be reached: " + description);
                return;
            }
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
}
