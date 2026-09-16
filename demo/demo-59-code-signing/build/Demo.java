package build;

import module java.base;
import java.util.jar.JarFile;
import build.jenesis.Make;
import build.jenesis.Project;

public class Demo {

    private static final Path KEYSTORE = Path.of("target", "keystore", "demo.p12");
    private static final Path PASSWORD = Path.of("target", "keystore", "demo.pass");

    static void main(String[] args) throws Exception {
        generateKey();
        if (Project.perform(Path.of("."), Make.loadProperties(Path.of(".")), "stage") == null) {
            throw new IllegalStateException("The build did not stage the signed jar");
        }
        Path staged = staged();
        System.out.println();
        System.out.println("Staged " + staged);
        for (String entry : signatures(staged)) {
            System.out.println("  carries " + entry);
        }
        verify(staged);
    }

    private static void generateKey() throws IOException, InterruptedException {
        if (Files.isRegularFile(KEYSTORE)) {
            return;
        }
        Files.createDirectories(KEYSTORE.getParent());
        Files.writeString(PASSWORD, "a throwaway password for a throwaway key");
        int code = new ProcessBuilder(tool("keytool"),
                "-genkeypair",
                "-alias", "demo",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-dname", "CN=Jenesis Demo, OU=Demos, O=Jenesis",
                "-keystore", KEYSTORE.toString(),
                "-storetype", "PKCS12",
                "-storepass:file", PASSWORD.toString())
                .inheritIO()
                .start()
                .waitFor();
        if (code != 0) {
            throw new IllegalStateException("keytool did not generate a key store: " + code);
        }
        System.out.println("Generated a throwaway key store in " + KEYSTORE);
    }

    private static Path staged() throws IOException {
        try (Stream<Path> walk = Files.walk(Path.of("target", "stage"))) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No jar was staged"));
        }
    }

    private static SequencedSet<String> signatures(Path jar) throws IOException {
        SequencedSet<String> entries = new LinkedHashSet<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            file.stream().map(entry -> entry.getName()).forEach(name -> {
                if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA"))) {
                    entries.add(name);
                }
            });
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("The staged jar carries no signature: " + jar);
        }
        return entries;
    }

    private static void verify(Path jar) throws IOException, InterruptedException {
        int code = new ProcessBuilder(tool("jarsigner"),
                "-verify",
                "-strict",
                "-keystore", KEYSTORE.toString(),
                "-storepass:file", PASSWORD.toString(),
                jar.toString(),
                "demo")
                .inheritIO()
                .start()
                .waitFor();
        if (code != 0 && code != 4) {
            throw new IllegalStateException("jarsigner did not verify the staged jar: " + code);
        }
    }

    private static String tool(String name) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? name + ".exe" : name).toString();
    }
}
