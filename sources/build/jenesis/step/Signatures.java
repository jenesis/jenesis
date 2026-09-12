package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.Verification;
import build.jenesis.maven.MavenDependencyKey;
import build.jenesis.maven.MavenRepository;

public class Signatures extends ProcessBuildStep {

    private static final String STATUS = "[GNUPG:] ";

    private static final List<String> VERIFY = List.of("--batch", "--no-tty", "--status-fd", "1", "--verify");

    private final transient Map<String, Repository> repositories;
    private final Verification verification;
    private final String command;

    public Signatures(Map<String, Repository> repositories) {
        this(repositories,
                Verification.fromProperty(),
                System.getProperty("jenesis.signature.command", "gpg"),
                null);
    }

    private Signatures(Map<String, Repository> repositories,
                       Verification verification,
                       String command,
                       Function<List<String>, ? extends ProcessHandler> factory) {
        super("gpg", factory == null ? ProcessHandler.OfProcess.ofCommand(command) : factory);
        this.repositories = repositories;
        this.verification = verification;
        this.command = command;
    }

    public Signatures verification(Verification verification) {
        return new Signatures(repositories, verification, command, factory);
    }

    public Signatures command(String command) {
        return new Signatures(repositories, verification, command, factory);
    }

    public Signatures factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new Signatures(repositories, verification, command, factory);
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties) {
        return CompletableFuture.completedStage(null);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (verification == Verification.NONE) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, SequencedSet<String>> declared = new TreeMap<>();
        SequencedMap<String, String> resolved = new LinkedHashMap<>();
        Set<String> internal = new HashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path declarations = argument.folder().resolve(SIGNATURES);
            if (Files.isRegularFile(declarations)) {
                SequencedProperties properties = SequencedProperties.ofFiles(declarations);
                for (String fingerprint : properties.stringPropertyNames()) {
                    declared.computeIfAbsent(fingerprint, _ -> new TreeSet<>())
                            .addAll(List.of(properties.getProperty(fingerprint).split(" ")));
                }
            }
            Path produced = argument.folder().resolve(Dependencies.INTERNAL);
            if (Files.isRegularFile(produced)) {
                internal.addAll(SequencedProperties.ofFiles(produced).stringPropertyNames());
            }
            Path index = argument.folder().resolve(DEPENDENCIES);
            if (Files.isRegularFile(index)) {
                SequencedProperties properties = SequencedProperties.ofFiles(index);
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    int space = value.indexOf(' ');
                    Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize();
                    if (Files.isRegularFile(jar)) {
                        resolved.putIfAbsent(key, jar.toString());
                    }
                }
            }
        }
        List<String> prefix = new ArrayList<>(prepended(properties(arguments)));
        prefix.addAll(VERIFY);
        SequencedSet<String> visited = new LinkedHashSet<>();
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String key = entry.getKey();
            int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
            if (second < 0 || internal.contains(key)) {
                continue;
            }
            String rest = key.substring(second + 1);
            int last = rest.lastIndexOf('/');
            if (last <= 0 || last == rest.indexOf('/')) {
                continue;
            }
            String coordinate = rest.substring(0, last), version = rest.substring(last + 1);
            String token = key.substring(0, first) + "/" + coordinate;
            if (!visited.add(token)) {
                continue;
            }
            SequencedSet<String> accepted = new LinkedHashSet<>();
            for (Map.Entry<String, SequencedSet<String>> declaration : declared.entrySet()) {
                if (declaration.getValue().stream().anyMatch(value -> value.equals(token)
                        || value.endsWith("/*")
                        && token.startsWith(value.substring(0, value.length() - 1)))) {
                    accepted.add(declaration.getKey());
                }
            }
            if (accepted.isEmpty()) {
                if (verification == Verification.STRICT) {
                    violations.add(token + " " + version + ": no @jenesis.signature line declares a key for it");
                }
                continue;
            }
            int repositorySlash = coordinate.indexOf('/');
            Repository repository = repositorySlash < 1
                    ? null
                    : repositories.get(Resolver.base(coordinate.substring(0, repositorySlash)));
            if (repository == null) {
                violations.add(token + " " + version + ": no repository resolves it, so its signature cannot be read");
                continue;
            }
            String relative = coordinate.substring(repositorySlash + 1) + "/" + version;
            Path signature = materialise(executor, context, repository, relative, true);
            if (signature == null) {
                violations.add(token + " " + version + ": a key is declared for it but no signature is published");
                continue;
            }
            Status status = verify(executor, context, prefix, Path.of(entry.getValue()), signature);
            if (status.failure() != null) {
                violations.add(token + " " + version + ": " + status.failure());
                continue;
            }
            if (status.fingerprint() == null) {
                violations.add(token + " " + version + ": gpg reported no validated signature");
                continue;
            }
            String fingerprint = "OpenPGP/" + status.fingerprint().toUpperCase(Locale.ROOT);
            if (accepted.stream().noneMatch(fingerprint::equalsIgnoreCase)) {
                violations.add(token + " " + version + ": signed by " + fingerprint
                        + " but only " + String.join(", ", accepted)
                        + (accepted.size() == 1 ? " is" : " are") + " declared for it; add "
                        + fingerprint + " to a @jenesis.signature line to accept a key rotation");
                continue;
            }
            MavenDependencyKey.Versioned pomKey = null;
            if (repository instanceof MavenRepository) {
                try {
                    pomKey = MavenDependencyKey.parse(relative);
                } catch (IllegalArgumentException _) {
                    pomKey = null;
                }
            }
            if (pomKey == null) {
                continue;
            }
            String descriptor = new MavenDependencyKey(pomKey.key().groupId(),
                    pomKey.key().artifactId(),
                    "pom",
                    null).coordinate(null, version);
            Path pomSignature = materialise(executor, context, repository, descriptor, true);
            Path pom = pomSignature == null ? null : materialise(executor, context, repository, descriptor, false);
            if (pomSignature == null) {
                if (verification == Verification.STRICT) {
                    violations.add(token + " " + version
                            + ": its artifact is signed but its POM publishes no signature");
                }
                continue;
            }
            if (pom == null) {
                violations.add(token + " " + version
                        + ": a signature is published for its POM but the POM itself did not resolve");
                continue;
            }
            Status pomStatus = verify(executor, context, prefix, pom, pomSignature);
            String signer = pomStatus.fingerprint() == null
                    ? null
                    : "OpenPGP/" + pomStatus.fingerprint().toUpperCase(Locale.ROOT);
            if (pomStatus.failure() != null) {
                violations.add(token + " " + version + ": for its POM, " + pomStatus.failure()
                        + "; a repository that re-serialises POMs invalidates them, so resolve from one that"
                        + " serves the published bytes");
            } else if (signer == null) {
                violations.add(token + " " + version + ": gpg reported no validated signature for its POM");
            } else if (!signer.equalsIgnoreCase(fingerprint)) {
                violations.add(token + " " + version + ": its POM is signed by " + signer
                        + " but its artifact by " + fingerprint);
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unverified dependency signatures:\n  "
                    + String.join("\n  ", violations));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private Path materialise(Executor executor,
                             BuildStepContext context,
                             Repository repository,
                             String coordinate,
                             boolean detached) throws IOException {
        RepositoryItem item = (detached
                ? repository.signature(executor, coordinate)
                : repository.fetch(executor, coordinate)).orElse(null);
        if (item == null) {
            return null;
        }
        Path file = item.file().orElse(null);
        if (file != null) {
            return file;
        }
        Path target = Files.createTempFile(context.supplement(), "fetched", detached ? ".asc" : ".tmp");
        try (InputStream inputStream = item.toInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private Status verify(Executor executor, BuildStepContext context, List<String> prefix, Path file, Path signature)
            throws IOException {
        Path output = Files.createTempFile(context.supplement(), "status", ".txt");
        Path error = Files.createTempFile(context.supplement(), "error", ".txt");
        List<String> commands = new ArrayList<>(prefix);
        commands.add(signature.toAbsolutePath().toString());
        commands.add(file.toAbsolutePath().toString());
        ProcessHandler handler;
        try {
            handler = factory.apply(commands);
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Could not locate '" + command
                    + "' to verify dependency signatures: install GnuPG, or name another command with"
                    + " -Djenesis.signature.command", e);
        }
        int exitCode;
        try {
            exitCode = execute(handler, output, error, tee(executor, handler));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while verifying " + file);
        }
        String fingerprint = null, failure = null;
        for (String line : Files.readAllLines(output)) {
            if (!line.startsWith(STATUS)) {
                continue;
            }
            String[] tokens = line.substring(STATUS.length()).trim().split(" ");
            switch (tokens[0]) {
                case "VALIDSIG" -> {
                    if (tokens.length > 10) {
                        fingerprint = tokens[10];
                    } else if (tokens.length > 1) {
                        fingerprint = tokens[1];
                    }
                }
                case "BADSIG" -> failure = "the signature does not match the file";
                case "EXPKEYSIG" -> failure = "the signing key has expired";
                case "REVKEYSIG" -> failure = "the signing key was revoked";
                case "NO_PUBKEY" -> failure = "the public key "
                        + (tokens.length > 1 ? tokens[1] : "")
                        + " is not available; obtain it, verify it against the project's published keys,"
                        + " and import it";
                case "ERRSIG" -> {
                    if (failure == null) {
                        failure = "the signature could not be checked";
                    }
                }
                default -> {
                }
            }
        }
        if (exitCode != 0 && fingerprint == null && failure == null) {
            throw new IllegalStateException("Unexpected exit code " + exitCode + " and no signature verdict from "
                    + command
                    + "\nTo reproduce, execute:\n "
                    + String.join(" ", handler.commands())
                    + (Files.isRegularFile(error) ? "\n\nError:\n" + Files.readString(error) : ""));
        }
        return new Status(fingerprint, failure);
    }

    private record Status(String fingerprint, String failure) {
    }
}
