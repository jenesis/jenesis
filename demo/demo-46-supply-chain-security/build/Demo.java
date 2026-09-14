package build;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.Project;

public class Demo {

    static void main(String[] args) throws Exception {
        // Baseline: a version-only dependency resolves and builds by default. The fixture, the
        // throwaway keys and the artifact cache are all built under target/, so the run starts from
        // nothing rather than from what a previous one signed.
        wipe();
        wipe("unpinned");
        new Project(Path.of("unpinned")).pinning(null).build();
        System.out.println("[ok]      unpinned: a version-only dependency builds by default");

        // 1. ... but strict pinning rejects it - there is no checksum to verify.
        expectFailure("unpinned: a version-only dependency under strict pinning",
                "unpinned",
                () -> new Project(Path.of("unpinned")).pinning(Pinning.STRICT).build());

        // 2. A wrong checksum fails the build even without strict pinning: every
        // download is verified against its pin regardless.
        expectFailure("tampered: a dependency whose pinned checksum does not match",
                "tampered",
                () -> new Project(Path.of("tampered")).pinning(null).build());

        // A checksum only proves the bytes did not change since they were vetted.
        // The rest of this demo is about who produced them in the first place, which
        // no hash can answer, and which @jenesis.signature declares.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            System.out.println("[skipped] the gpg Git for Windows ships reads --homedir as an MSYS path,"
                    + " so this half needs a POSIX shell");
            return;
        }
        if (run(null, List.of("gpg", "--version")) != null
                || run(null, List.of("gpgv", "--version")) != null) {
            System.out.println("[skipped] gpg and gpgv are not both installed,"
                    + " so signatures cannot be signed and verified here");
            return;
        }
        Path work = Path.of("target").toAbsolutePath();
        Path jar = published(work);
        Path home = generatedKey(work);
        String fingerprint = signed(home, jar);
        vaulted(home, work, fingerprint);

        // 3. The declared scope verifies exactly the coordinates a declaration covers.
        // This project declares none, so nothing is verified and the build is untouched.
        expectSignature("signed: an undeclared coordinate is left alone under declared",
                true, home, "declared", "signed");

        // 4. Under strict, that same silence is the failure: an external coordinate no
        // line vouches for is refused rather than resolved on trust.
        expectSignature("signed: an undeclared coordinate under strict verification",
                false, home, "strict", "signed");

        // 5. With the signer declared, strict accepts it. The key arrives in the diff
        // the way a pin does, for review against the project's published KEYS; here it
        // is written in from the throwaway key generated above.
        Path declaration = Path.of("signed", "sources", "module-info.java");
        String before = Files.readString(declaration);
        try {
            Files.writeString(declaration, before.replace(" */",
                    " * @jenesis.signature OpenPGP/" + fingerprint + " org.example/*\n */"));
            expectSignature("signed: the artifact and its POM both verify against the declared key",
                    true, home, "strict", "signed");
        } finally {
            Files.writeString(declaration, before);
        }

        // 6. This project declares a different key. The signature itself is perfectly
        // valid, so only the comparison against the declaration catches it - which is
        // the whole point of naming the key rather than only hashing the bytes.
        expectSignature("rotated: the declared key is not the one that signed the artifact",
                false, home, "declared", "rotated");

        // 7. The same rejection, with the key taken from a local list instead of an
        // inline declaration - the shape @jenesis.bom uses for a local pin file. A list
        // is only ever read from disk: one fetched from a repository would itself need
        // verifying, which is the problem being solved.
        expectSignature("vendored: a local key list is consulted like an inline declaration",
                false, home, "declared", "vendored");

        // 8. Verification is opt-in. Without the property the contradiction is never
        // looked for: an ordinary build enforces the pin and needs no gpg at all.
        expectSignature("rotated: the same contradiction is never looked for by default",
                true, home, null, "rotated");

        // 9. A key that has since expired still vouches for what it signed while it was
        // valid, which is the common case for an old release: the keyservers publish no
        // extended expiry, yet the signature was made years before the key lapsed. The
        // key here is given seconds to live and the demo waits for it, because gpgv
        // reads the real clock and has no option to pretend otherwise.
        String expiringFingerprint = expiringKey(home, jar);
        vaulted(home, work, expiringFingerprint);
        Path cached = Path.of("target", "artifacts");
        if (Files.isDirectory(cached)) {
            delete(cached);
        }
        long expired = System.currentTimeMillis() + 6_000;
        while (System.currentTimeMillis() < expired) {
            Thread.sleep(200);
        }
        String declared = Files.readString(declaration);
        try {
            Files.writeString(declaration, declared.replace(" */",
                    " * @jenesis.signature OpenPGP/" + expiringFingerprint + " org.example/*\n */"));
            expectSignature("expired: what the key signed before it lapsed is still accepted",
                    true, home, "strict", "signed", List.of());

            // 10. The same artifact under the strictest reading of expiry, where the key
            // must be unexpired today. That is the previous behaviour, kept as an option.
            expectSignature("expired: the same signature under expiry measured against today",
                    false, home, "strict", "signed",
                    List.of("-Djenesis.openpgp.expiry=current"));
        } finally {
            Files.writeString(declaration, declared);
        }

        // 11. The same question answered without anyone holding a key. Sigstore issues a
        // certificate that names the identity which authenticated to an identity provider,
        // valid for ten minutes, and records the signature in a public log; what a consumer
        // verifies afterwards is that identity and that log entry. @jenesis.signature declares
        // it instead of a fingerprint. Nothing is fetched to check one: the bundle beside the
        // artifact carries the certificate, and the tool carries the trust root, so this half
        // forks no gpg at all. It does need a published bundle, since a certificate authority
        // and a public log cannot be stood up here the way a throwaway key can.
        if (!published()) {
            System.out.println("[skipped] no published bundle could be reached, so identities are not verified here");
            return;
        }
        Path vouching = Files.writeString(Path.of("target", "vouches-for-nothing.json"),
                "{\"certificateAuthorities\":[],\"tlogs\":[]}");

        expectIdentity("attested: a dependency verified against the identity its declaration names",
                true, "declared", "attested", List.of());

        // 12. The bundle is genuine and the log entry is real, so only the comparison against
        // the declaration catches a release that came from somewhere else.
        expectIdentity("forked: the same bundle, declared to come from a different repository",
                false, "declared", "forked", List.of());

        // 13. Under strict the POM must carry a bundle from the same identity, which closes the
        // gap that POMs are read during resolution but never pinned.
        expectIdentity("attested: under strict, the POM carries a bundle from that identity too",
                true, "strict", "attested", List.of());

        // 14. The trust root the tool carries is the published one of the public Sigstore
        // instance. Naming another replaces it wholesale, which is how a private instance is
        // reached - and how a build stops, when what it names vouches for nothing.
        expectIdentity("named: the same build against a trust root that vouches for nothing",
                false, "declared", "attested", List.of("-Djenesis.sigstore.uri=" + vouching.toUri()));

        System.out.println();
        System.out.println("Pinning blocked the unverified and the tampered dependency, a declared key");
        System.out.println("blocked the artifact signed by another, and a declared identity blocked the");
        System.out.println("one built somewhere else - the last of them without a key anywhere.");
    }

    private static boolean published() {
        try {
            URLConnection connection = URI.create(
                    "https://repo1.maven.org/maven2/dev/sigstore/protobuf-specs/0.5.2/"
                            + "protobuf-specs-0.5.2.jar.sigstore.json").toURL().openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            try (InputStream stream = connection.getInputStream()) {
                return stream.readAllBytes().length > 0;
            }
        } catch (IOException _) {
            return false;
        }
    }

    private static void expectIdentity(String description,
                                       boolean success,
                                       String verification,
                                       String project,
                                       List<String> options) throws Exception {
        List<String> command = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java",
                "-Djenesis.maven.local=" + Path.of("target", "artifacts").toAbsolutePath(),
                "-Djenesis.dependency.signature=" + verification,
                "-Djenesis.print.signatures=true"));
        command.addAll(options);
        command.addAll(List.of("build/jenesis/Make.java", "build"));
        String failure = run(Path.of(project), command);
        if ((failure == null) != success) {
            throw new AssertionError("Expected "
                    + (success ? "success" : "failure")
                    + " but the build "
                    + (failure == null ? "succeeded" : "failed")
                    + ": "
                    + description
                    + (failure == null ? "" : "\n" + failure));
        }
        System.out.println((success ? "[ok]      " : "[blocked] ") + description);
    }

    private static Path published(Path work) throws Exception {
        Path folder = Files.createDirectories(work.resolve("repository/org/example/lib/1.0"));
        byte[] content = "org.example.Lib\n".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(content);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            ZipEntry entry = new ZipEntry("org/example/lib.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc.getValue());
            entry.setTimeLocal(LocalDateTime.of(2000, 1, 1, 0, 0, 0));
            zip.putNextEntry(entry);
            zip.write(content);
            zip.closeEntry();
        }
        Path jar = folder.resolve("lib-1.0.jar");
        Files.write(jar, buffer.toByteArray());
        Files.writeString(folder.resolve("lib-1.0.pom"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>lib</artifactId>
                    <version>1.0</version>
                </project>
                """);
        return jar;
    }

    private static Path generatedKey(Path work) throws Exception {
        Path home = work.resolve("gnupg");
        if (Files.isDirectory(home)) {
            delete(home);
        }
        Files.createDirectories(home);
        if (home.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwx------"));
        }
        gpg(home, "--quick-generate-key", "Jenesis Demo (throwaway) <demo@jenesis.invalid>",
                "default", "default", "never");
        return home;
    }

    private static String expiringKey(Path home, Path jar) throws Exception {
        gpg(home, "--quick-generate-key", "Jenesis Demo (expiring) <expiry@jenesis.invalid>",
                "default", "default", "seconds=5");
        String expiring = fingerprintOf(home, "expiry@jenesis.invalid");
        gpg(home, "--detach-sign", "--armor", "--local-user", expiring,
                "--output", jar + ".asc", jar.toString());
        Path pom = jar.resolveSibling("lib-1.0.pom");
        gpg(home, "--detach-sign", "--armor", "--local-user", expiring,
                "--output", pom + ".asc", pom.toString());
        return expiring;
    }

    private static String fingerprintOf(Path home, String identity) throws Exception {
        Process process = new ProcessBuilder("gpg",
                "--homedir", home.toString(),
                "--batch", "--with-colons", "--fingerprint").redirectErrorStream(false).start();
        String listed = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        String candidate = null;
        for (String line : listed.split("\n")) {
            if (line.startsWith("fpr:")) {
                candidate = line.split(":")[9];
            } else if (line.startsWith("uid:") && line.contains(identity)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No key for " + identity + " reported by gpg:\n" + listed);
    }

    private static void vaulted(Path home, Path work, String key) throws Exception {
        Path vault = work.resolve("keys");
        Files.createDirectories(vault);
        gpg(home, "--export", "--output", vault.resolve(key + ".gpg").toString(), key);
    }

    private static String signed(Path home, Path jar) throws Exception {
        gpg(home, "--detach-sign", "--armor", "--output", jar + ".asc", jar.toString());
        Path pom = jar.resolveSibling("lib-1.0.pom");
        gpg(home, "--detach-sign", "--armor", "--output", pom + ".asc", pom.toString());
        Process process = new ProcessBuilder("gpg",
                "--homedir", home.toString(),
                "--batch", "--with-colons", "--fingerprint").redirectErrorStream(false).start();
        String listed = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor();
        for (String line : listed.split("\n")) {
            if (line.startsWith("fpr:")) {
                return line.split(":")[9];
            }
        }
        throw new IllegalStateException("No fingerprint reported by gpg:\n" + listed);
    }

    private static void gpg(Path home, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("gpg",
                "--homedir", home.toString(),
                "--batch", "--yes", "--passphrase", "", "--pinentry-mode", "loopback"));
        command.addAll(List.of(arguments));
        String failure = run(null, command);
        if (failure != null) {
            throw new IllegalStateException("Failed to run gpg " + String.join(" ", arguments) + ":\n" + failure);
        }
    }

    private static void expectSignature(String description,
                                        boolean success,
                                        Path home,
                                        String verification,
                                        String project) throws Exception {
        expectSignature(description, success, home, verification, project, List.of());
    }

    private static void expectSignature(String description,
                                        boolean success,
                                        Path home,
                                        String verification,
                                        String project,
                                        List<String> options) throws Exception {
        Path artifacts = Files.createDirectories(Path.of("target", "artifacts").toAbsolutePath());
        List<String> command = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java",
                "-Djenesis.maven.uri=" + Path.of("target", "repository").toAbsolutePath().toUri(),
                "-Djenesis.maven.local=" + artifacts));
        if (verification != null) {
            command.add("-Djenesis.dependency.signature=" + verification);
            command.add("-Djenesis.openpgp.local=" + Path.of("target", "keys").toAbsolutePath());
            command.add("-Djenesis.openpgp.uri=");
        }
        command.addAll(options);
        command.addAll(List.of("build/jenesis/Make.java", "build"));
        String failure = run(Path.of(project), command, home);
        if ((failure == null) != success) {
            throw new AssertionError("Expected "
                    + (success ? "success" : "failure")
                    + " but the build "
                    + (failure == null ? "succeeded" : "failed")
                    + ": "
                    + description
                    + (failure == null ? "" : "\n" + failure));
        }
        System.out.println((success ? "[ok]      " : "[blocked] ") + description);
    }

    private static String run(Path directory, List<String> command) throws Exception {
        return run(directory, command, null);
    }

    private static String run(Path directory, List<String> command, Path home) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (home == null) {
            builder.environment().remove("GNUPGHOME");
        } else {
            builder.environment().put("GNUPGHOME", home.toString());
        }
        if (directory != null) {
            builder.directory(directory.toFile());
        }
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return process.waitFor() == 0 ? null : output;
    }

    private static void expectFailure(String description, String project, Build build) throws IOException {
        wipe(project);
        try {
            build.run();
        } catch (Throwable _) {
            System.out.println("[blocked] " + description);
            return;
        }
        throw new AssertionError("Build was expected to fail but succeeded: " + description);
    }

    private static void wipe() throws IOException {
        wipe(".");
    }

    private static void wipe(String project) throws IOException {
        Path target = Path.of(project, "target");
        if (Files.isDirectory(target)) {
            delete(target);
        }
    }

    private static void delete(Path folder) throws IOException {
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

    @FunctionalInterface
    private interface Build {
        void run() throws Exception;
    }
}
