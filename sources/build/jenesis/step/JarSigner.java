package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;

public class JarSigner extends ProcessBuildStep {

    private final String keystore;
    private final String alias;
    private final List<String> storepass;
    private final List<String> keypass;
    private final String storetype;
    private final String tsa;
    private final List<String> arguments;

    public JarSigner() {
        this(System.getProperty("jenesis.jarsigner.keystore"),
                System.getProperty("jenesis.jarsigner.alias"),
                password("-storepass", System.getProperty("jenesis.jarsigner.storepass")),
                password("-keypass", System.getProperty("jenesis.jarsigner.keypass")),
                System.getProperty("jenesis.jarsigner.storetype"),
                System.getProperty("jenesis.jarsigner.tsa"),
                words(System.getProperty("jenesis.jarsigner.arguments")),
                printing("jarsigner"));
    }

    private JarSigner(String keystore,
                      String alias,
                      List<String> storepass,
                      List<String> keypass,
                      String storetype,
                      String tsa,
                      List<String> arguments,
                      BiConsumer<Boolean, String> printing) {
        super("jarsigner", ProcessHandler.OfProcess.ofJavaHome("bin/jarsigner"), printing);
        this.keystore = keystore;
        this.alias = alias;
        this.storepass = storepass;
        this.keypass = keypass;
        this.storetype = storetype;
        this.tsa = tsa;
        this.arguments = arguments;
    }

    public boolean configured() {
        return keystore != null
                || alias != null
                || storepass != null
                || keypass != null
                || storetype != null
                || tsa != null
                || !arguments.isEmpty();
    }

    public JarSigner keystore(String keystore) {
        return new JarSigner(keystore, alias, storepass, keypass, storetype, tsa, arguments, printing);
    }

    public JarSigner alias(String alias) {
        return new JarSigner(keystore, alias, storepass, keypass, storetype, tsa, arguments, printing);
    }

    public JarSigner storepass(String storepass) {
        return new JarSigner(keystore, alias, password("-storepass", storepass), keypass, storetype, tsa, arguments, printing);
    }

    public JarSigner keypass(String keypass) {
        return new JarSigner(keystore, alias, storepass, password("-keypass", keypass), storetype, tsa, arguments, printing);
    }

    public JarSigner storetype(String storetype) {
        return new JarSigner(keystore, alias, storepass, keypass, storetype, tsa, arguments, printing);
    }

    public JarSigner tsa(String tsa) {
        return new JarSigner(keystore, alias, storepass, keypass, storetype, tsa, arguments, printing);
    }

    public JarSigner arguments(List<String> arguments) {
        return new JarSigner(keystore, alias, storepass, keypass, storetype, tsa, arguments, printing);
    }

    public JarSigner verbose(BiConsumer<Boolean, String> printing) {
        return new JarSigner(keystore, alias, storepass, keypass, storetype, tsa, arguments, printing);
    }

    private static List<String> words(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
    }

    private static List<String> password(String option, String value) {
        if (value == null) {
            return null;
        }
        String[] words = value.trim().split("\\s+", 2);
        if (words.length != 2
                || !words[0].equals("env") && !words[0].equals("file")
                || words[1].isBlank()) {
            throw new IllegalArgumentException("Malformed jenesis.jarsigner"
                    + option.replace("-", ".")
                    + " '"
                    + value
                    + "': expected 'env <variable>' or 'file <path>', so that no password is a value anything records");
        }
        return List.of(option + ":" + words[0], words[1].trim());
    }

    @Override
    public CompletionStage<List<String>> process(Executor executor,
                                                 BuildStepContext context,
                                                 SequencedMap<String, BuildStepArgument> arguments,
                                                 SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        SequencedSet<Path> jars = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path folder = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
                for (Path file : files) {
                    if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".jar")) {
                        jars.add(file);
                    }
                }
            }
        }
        if (jars.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        if (jars.size() > 1) {
            throw new IllegalStateException("Expected a single artifact to sign but resolved " + jars);
        }
        if (keystore == null) {
            throw new IllegalStateException("This project signs its jar but no key store is named"
                    + " (pass -Djenesis.jarsigner.keystore=<path>, which a release machine supplies)");
        }
        if (alias == null) {
            throw new IllegalStateException("This project signs its jar but no key within "
                    + keystore
                    + " is named (pass -Djenesis.jarsigner.alias=<name>)");
        }
        if (storepass == null) {
            throw new IllegalStateException("This project signs its jar but nothing says where the password of "
                    + keystore
                    + " is read from (pass -Djenesis.jarsigner.storepass=env <variable> or file <path>;"
                    + " without it jarsigner asks for the password and a build that cannot answer hangs)");
        }
        if (!Files.isRegularFile(Path.of(keystore))) {
            throw new IllegalStateException("No key store at " + keystore
                    + " (jenesis.jarsigner.keystore names a path that holds none)");
        }
        Path source = jars.getFirst();
        Path signed = Files.createDirectory(context.next().resolve(BuildStep.ARTIFACTS))
                .resolve(source.getFileName());
        List<String> commands = new ArrayList<>(List.of("-keystore", keystore));
        if (storetype != null) {
            commands.add("-storetype");
            commands.add(storetype);
        }
        if (storepass != null) {
            commands.addAll(storepass);
        }
        if (keypass != null) {
            commands.addAll(keypass);
        }
        if (tsa != null) {
            commands.add("-tsa");
            commands.add(tsa);
        }
        commands.addAll(this.arguments);
        commands.add("-signedjar");
        commands.add(signed.toString());
        commands.add(source.toString());
        commands.add(alias);
        return CompletableFuture.completedStage(commands);
    }
}
