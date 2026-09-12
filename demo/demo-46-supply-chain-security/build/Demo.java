package build;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.Project;

public class Demo {

    static void main(String[] args) throws Exception {
        // Baseline: a version-only dependency resolves and builds by default.
        wipe();
        new Project(Path.of(".")).pinning(null).build("+unpinned");
        System.out.println("[ok]      unpinned: a version-only dependency builds by default");

        // 1. ... but strict pinning rejects it - there is no checksum to verify.
        expectFailure("unpinned: a version-only dependency under strict pinning",
                () -> new Project(Path.of(".")).pinning(Pinning.STRICT).build("+unpinned"));

        // 2. A wrong checksum fails the build even without strict pinning: every
        // download is verified against its pin regardless.
        expectFailure("tampered: a dependency whose pinned checksum does not match",
                () -> new Project(Path.of(".")).pinning(null).build("+tampered"));

        // A checksum only proves the bytes did not change since they were vetted.
        // The rest of this demo is about who produced them in the first place, which
        // no hash can answer, and which @jenesis.signature declares.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            System.out.println("[skipped] the gpg Git for Windows ships reads --homedir as an MSYS path,"
                    + " so this half needs a POSIX shell");
            return;
        }
        if (run(null, List.of("gpg", "--version")) != null) {
            System.out.println("[skipped] gpg is not installed, so signatures cannot be verified here");
            return;
        }
        Path work = Path.of("target").toAbsolutePath();
        Path jar = published(work);
        Path home = generatedKey(work);
        String fingerprint = signed(home, jar);

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

        // 7. Verification is opt-in. Without the property the contradiction is never
        // looked for: an ordinary build enforces the pin and needs no gpg at all.
        expectSignature("rotated: the same contradiction is never looked for by default",
                true, home, null, "rotated");

        System.out.println();
        System.out.println("Pinning blocked the unverified and the tampered dependency,");
        System.out.println("and signature verification blocked the one signed by another key.");
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
        Path artifacts = Files.createDirectories(Path.of("target", "artifacts").toAbsolutePath());
        List<String> command = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java",
                "-Djenesis.maven.uri=" + Path.of("target", "repository").toAbsolutePath().toUri(),
                "-Djenesis.maven.local=" + artifacts));
        if (verification != null) {
            command.add("-Djenesis.dependency.signature=" + verification);
        }
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

    private static void expectFailure(String description, Build build) throws IOException {
        wipe();
        try {
            build.run();
        } catch (Throwable _) {
            System.out.println("[blocked] " + description);
            return;
        }
        throw new AssertionError("Build was expected to fail but succeeded: " + description);
    }

    private static void wipe() throws IOException {
        Path target = Path.of("target");
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
