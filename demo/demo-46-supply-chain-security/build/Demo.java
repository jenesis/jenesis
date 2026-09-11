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
        // no hash can answer, and which @jenesis.signature records.
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

        // 3. Nothing is declared for this coordinate yet, so pin verifies the detached
        // signature and records the key it found, for review in the diff.
        Path declaration = Path.of("signed", "sources", "module-info.java");
        String before = Files.readString(declaration);
        try {
            expectSignature("signed: pin records the key that signed an undeclared coordinate",
                    true, home, "all", "signed");
            if (!Files.readString(declaration).contains("OpenPGP/" + fingerprint)) {
                throw new AssertionError("pin did not record OpenPGP/" + fingerprint + " in " + declaration);
            }
        } finally {
            Files.writeString(declaration, before);
        }

        // 4. This project declares a different key. The signature itself is
        // perfectly valid, so only the comparison against the declaration catches
        // it - which is the whole point of recording the signer.
        expectSignature("rotated: the declared key is not the one that signed the artifact",
                false, home, "all", "rotated");

        // 5. The unpinned scope verifies only what pin is about to write fresh. This
        // coordinate already carries a pin checksum, so nothing is re-verified and
        // even the contradicting declaration is left alone: signatures are an
        // update-time check, and the pin carries the earlier verdict forward.
        expectSignature("rotated: an already-pinned coordinate is not re-verified under unpinned",
                true, home, "unpinned", "rotated");

        // 6. The same rejection, with the key taken from a local list instead of an
        // inline declaration. A list is only ever read from disk - one fetched from a
        // repository would itself need verifying, which is the problem being solved.
        expectSignature("vendored: a local key list is consulted like an inline declaration",
                false, home, "all", "vendored");

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
                                        String scope,
                                        String project) throws Exception {
        Path artifacts = Files.createDirectories(Path.of("target", "artifacts").toAbsolutePath());
        List<String> command = new ArrayList<>(List.of(System.getProperty("java.home") + "/bin/java",
                "-Djenesis.maven.uri=" + Path.of("target", "repository").toAbsolutePath().toUri(),
                "-Djenesis.maven.local=" + artifacts));
        if (scope != null) {
            command.add("-Djenesis.dependency.signature=" + scope);
        }
        command.addAll(List.of("build/jenesis/Make.java", "pin"));
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
