package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
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

    public static final String SIGNATURES = "signatures.properties";

    public static final String ALGORITHM = "OpenPGP";

    private static final String STATUS = "[GNUPG:] ";

    private static final String LIST_PREFIX = "signature-", LIST_SUFFIX = ".properties";

    private static final List<String> VERIFY = List.of("--batch", "--no-tty", "--status-fd", "1", "--verify");

    private final transient Map<String, Repository> repositories;
    private final String path;
    private final Verification verification;
    private final SequencedMap<String, SequencedSet<String>> declared;
    private final String command;

    public Signatures(Map<String, Repository> repositories, String path) {
        this(repositories,
                path,
                Verification.fromProperty(),
                new LinkedHashMap<>(),
                System.getProperty("jenesis.signature.command", "gpg"),
                null);
    }

    private Signatures(Map<String, Repository> repositories,
                       String path,
                       Verification verification,
                       SequencedMap<String, SequencedSet<String>> declared,
                       String command,
                       Function<List<String>, ? extends ProcessHandler> factory) {
        super("gpg", factory == null ? ProcessHandler.OfProcess.ofCommand(command) : factory);
        this.repositories = repositories;
        this.path = path;
        this.verification = verification;
        this.declared = declared;
        this.command = command;
    }

    public Signatures verification(Verification verification) {
        return new Signatures(repositories, path, verification, declared, command, factory);
    }

    public Signatures declared(SequencedMap<String, SequencedSet<String>> declared) {
        return new Signatures(repositories, path, verification, declared, command, factory);
    }

    public Signatures command(String command) {
        return new Signatures(repositories, path, verification, declared, command, factory);
    }

    public Signatures factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new Signatures(repositories, path, verification, declared, command, factory);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties) {
        return CompletableFuture.completedStage(VERIFY);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (verification == Verification.NONE) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        List<String> prefix = new ArrayList<>(prepended(properties(arguments)));
        prefix.addAll(VERIFY);
        SequencedMap<String, Inventory.Dependency> closure = Inventory.closure(arguments.values(), path);
        Set<String> internal = new LinkedHashSet<>();
        for (String identity : Inventory.identities(arguments.values())) {
            internal.add(identity);
            int firstSlash = identity.indexOf('/'), lastSlash = identity.lastIndexOf('/');
            if (firstSlash > 0 && lastSlash > firstSlash) {
                internal.add(identity.substring(0, lastSlash));
            }
        }
        Set<Path> verifiedElsewhere = new HashSet<>();
        closure.forEach((key, dependency) -> {
            String coordinate = key.substring(dependency.group().length() + 1);
            if (!coordinate.startsWith("module/") && dependency.jar() != null) {
                verifiedElsewhere.add(dependency.jar());
            }
        });
        SequencedMap<String, String> recorded = new TreeMap<>(), verified = new TreeMap<>();
        SequencedSet<String> visited = new LinkedHashSet<>();
        List<String> violations = new ArrayList<>(), unsigned = new ArrayList<>(), skipped = new ArrayList<>();
        for (Map.Entry<String, Inventory.Dependency> entry : closure.entrySet()) {
            Inventory.Dependency dependency = entry.getValue();
            String group = dependency.group();
            String key = entry.getKey().substring(group.length() + 1);
            if (internal.contains(key)) {
                continue;
            }
            int lastSlash = key.lastIndexOf('/'), firstSlash = key.indexOf('/');
            if (lastSlash <= 0 || lastSlash == firstSlash) {
                continue;
            }
            String coordinate = key.substring(0, lastSlash), version = key.substring(lastSlash + 1);
            String token = group + "/" + coordinate;
            if (!visited.add(token)) {
                continue;
            }
            if (verification == Verification.UNPINNED && !dependency.checksum().isEmpty()) {
                continue;
            }
            Path jar = dependency.jar();
            if (jar == null || !Files.isRegularFile(jar)) {
                continue;
            }
            if (coordinate.startsWith("module/") && verifiedElsewhere.contains(jar)) {
                continue;
            }
            int repositorySlash = coordinate.indexOf('/');
            Repository repository = repositorySlash < 1
                    ? null
                    : repositories.get(Resolver.base(coordinate.substring(0, repositorySlash)));
            if (repository == null) {
                continue;
            }
            String relative = coordinate.substring(coordinate.indexOf('/') + 1) + "/" + version;
            Path signature = materialise(executor, context, repository, relative, true);
            if (signature == null) {
                unsigned.add(token + " " + version);
                continue;
            }
            Status status = verify(executor, context, prefix, jar, signature);
            if (status.failure() != null) {
                violations.add(token + " " + version + ": " + status.failure());
                continue;
            }
            if (status.fingerprint() == null) {
                skipped.add(token + " " + version + ": gpg reported no validated signature");
                continue;
            }
            String fingerprint = ALGORITHM + "/" + status.fingerprint().toUpperCase(Locale.ROOT);
            SequencedSet<String> accepted = new LinkedHashSet<>();
            for (Map.Entry<String, SequencedSet<String>> declaration : declared.entrySet()) {
                if (declaration.getValue().stream().anyMatch(value -> covers(value, token))) {
                    accepted.add(declaration.getKey());
                }
            }
            if (!accepted.isEmpty() && accepted.stream().anyMatch(fingerprint::equalsIgnoreCase)) {
                verified.put(token, fingerprint);
                continue;
            }
            if (!accepted.isEmpty()) {
                violations.add(token + " " + version + ": signed by " + fingerprint
                        + " but only " + String.join(", ", accepted) + " is declared for it; add "
                        + fingerprint + " to a @jenesis.signature line to accept a key rotation,"
                        + " then run pin again");
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
            String pomFailure = null;
            if (pomKey != null) {
                String descriptor = new MavenDependencyKey(pomKey.key().groupId(),
                        pomKey.key().artifactId(),
                        "pom",
                        null).coordinate(null, version);
                Path pomSignature = materialise(executor, context, repository, descriptor, true);
                Path pom = pomSignature == null
                        ? null
                        : materialise(executor, context, repository, descriptor, false);
                if (pomSignature == null) {
                    if (verification == Verification.STRICT) {
                        pomFailure = "its artifact is signed but its POM publishes no signature";
                    }
                } else if (pom == null) {
                    pomFailure = "a signature is published for its POM but the POM itself did not resolve";
                } else {
                    Status pomStatus = verify(executor, context, prefix, pom, pomSignature);
                    String signer = pomStatus.fingerprint() == null
                            ? null
                            : ALGORITHM + "/" + pomStatus.fingerprint().toUpperCase(Locale.ROOT);
                    if (pomStatus.failure() != null) {
                        pomFailure = "for its POM, " + pomStatus.failure()
                                + "; a repository that re-serialises POMs invalidates them, so resolve from"
                                + " one that serves the published bytes";
                    } else if (signer == null) {
                        pomFailure = "gpg reported no validated signature for its POM";
                    } else if (!signer.equalsIgnoreCase(fingerprint)) {
                        pomFailure = "its POM is signed by " + signer + " but its artifact by " + fingerprint;
                    }
                }
            }
            if (pomFailure != null) {
                violations.add(token + " " + version + ": " + pomFailure);
                continue;
            }
            recorded.put(token, fingerprint);
        }
        if (verification == Verification.STRICT) {
            unsigned.forEach(entry -> violations.add(entry + ": no detached signature is published"));
            violations.addAll(skipped);
        }
        SequencedProperties properties = new SequencedProperties();
        recorded.forEach(properties::setProperty);
        properties.store(context.next().resolve(SIGNATURES));
        SequencedMap<String, String> reported = new TreeMap<>(verified);
        reported.putAll(recorded);
        StringBuilder builder = new StringBuilder();
        if (reported.isEmpty()) {
            builder.append("No dependency signatures were verified.\n");
        } else {
            reported.forEach((token, fingerprint) -> builder.append(token)
                    .append(" ")
                    .append(fingerprint)
                    .append("\n"));
        }
        SequencedMap<String, List<String>> sections = new LinkedHashMap<>();
        sections.put("Unsigned", unsigned);
        sections.put("Unchecked", skipped);
        sections.put("Rejected", violations);
        sections.forEach((heading, entries) -> {
            if (!entries.isEmpty()) {
                builder.append("\n").append(heading).append(":\n");
                entries.forEach(entry -> builder.append("  ").append(entry).append("\n"));
            }
        });
        Files.writeString(Files.createDirectories(context.next().resolve(REPORTS + "signatures"))
                .resolve("signatures.txt"), builder.toString());
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unverified dependency signatures:\n  "
                    + String.join("\n  ", violations));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    public static void declare(SequencedMap<String, SequencedSet<String>> declared,
                               String fingerprint,
                               String rest,
                               String line,
                               String origin,
                               SequencedSet<Path> locations) throws IOException {
        if (rest != null && !rest.isBlank()) {
            declare(declared, fingerprint, rest, line, origin);
            return;
        }
        String name = fingerprint.substring(fingerprint.lastIndexOf('/') + 1);
        if (!name.startsWith(LIST_PREFIX) || !name.endsWith(LIST_SUFFIX)) {
            throw new IllegalArgumentException("Malformed @jenesis.signature declaration '"
                    + line
                    + "' in "
                    + origin
                    + ": expected <algorithm>/<fingerprint> <token>... or a local "
                    + LIST_PREFIX
                    + "<name>"
                    + LIST_SUFFIX
                    + "; a key list is never resolved from a repository, as a list that had to be"
                    + " downloaded would itself need verifying");
        }
        for (Path location : locations) {
            Path file = location.resolve(name);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String listed : properties.stringPropertyNames()) {
                declare(declared, listed, properties.getProperty(listed),
                        listed + " " + properties.getProperty(listed), file.toString());
            }
            return;
        }
        throw new IllegalStateException("No " + name + " found for '"
                + line
                + "' in "
                + origin
                + ": looked in "
                + locations
                + " (set -Djenesis.project.signatures to name other locations)");
    }

    public static void declare(SequencedMap<String, SequencedSet<String>> declared,
                        String fingerprint,
                        String rest,
                        String line,
                        String origin) {
        String tokens = rest == null ? "" : rest.trim().replaceAll("\\s+", " ");
        if (fingerprint.indexOf('/') < 1 || fingerprint.endsWith("/") || tokens.isEmpty()) {
            throw new IllegalArgumentException("Malformed @jenesis.signature declaration '"
                    + line
                    + "' in "
                    + origin
                    + ": expected <algorithm>/<fingerprint> <token>...");
        }
        String scheme = fingerprint.substring(0, fingerprint.indexOf('/'));
        String value = fingerprint.substring(fingerprint.indexOf('/') + 1);
        if (!scheme.equalsIgnoreCase(ALGORITHM)) {
            throw new IllegalArgumentException("Unknown signature scheme '"
                    + scheme
                    + "' in '"
                    + line
                    + "' in "
                    + origin
                    + ": expected "
                    + ALGORITHM);
        }
        if (value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("Malformed "
                    + ALGORITHM
                    + " fingerprint '"
                    + value
                    + "' in '"
                    + line
                    + "' in "
                    + origin
                    + ": expected hexadecimal characters only");
        }
        String normalized = ALGORITHM + "/" + value.toUpperCase(Locale.ROOT);
        for (String token : tokens.split(" ")) {
            int first = token.indexOf('/');
            String expanded;
            if (first < 0) {
                expanded = "main/module/" + token;
            } else {
                expanded = token.indexOf('/', first + 1) < 0 ? "main/maven/" + token : token;
            }
            if (expanded.endsWith("/*")) {
                String[] segments = expanded.split("/");
                if (segments.length != 4 || !segments[1].equals("maven")) {
                    throw new IllegalArgumentException("Malformed @jenesis.signature token '"
                            + expanded
                            + "' in '"
                            + line
                            + "' in "
                            + origin
                            + ": a trailing /* covers every artifact of one Maven groupId,"
                            + " so it needs <groupId>/* or <group>/maven/<groupId>/*");
                }
            }
            declared.computeIfAbsent(normalized, _ -> new TreeSet<>()).add(expanded);
        }
    }

    public static boolean covers(String declaration, String token) {
        if (declaration.equals(token)) {
            return true;
        }
        return declaration.endsWith("/*") && token.startsWith(declaration.substring(0, declaration.length() - 1));
    }

    public static SequencedMap<String, String> recorded(Iterable<BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, String> recorded = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path file = argument.folder().resolve(SIGNATURES);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                recorded.putIfAbsent(key, properties.getProperty(key));
            }
        }
        return recorded;
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

    private Status verify(Executor executor, BuildStepContext context, List<String> prefix, Path jar, Path signature)
            throws IOException {
        Path output = context.supplement().resolve("output");
        Path error = context.supplement().resolve("error");
        List<String> commands = new ArrayList<>(prefix);
        commands.add(signature.toAbsolutePath().toString());
        commands.add(jar.toAbsolutePath().toString());
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
            throw new InterruptedIOException("Interrupted while verifying " + jar);
        }
        List<String> lines = Files.isRegularFile(output) ? Files.readAllLines(output) : List.of();
        String fingerprint = null, failure = null;
            for (String line : lines) {
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
                            + " and import it before running pin again";
                    case "ERRSIG" -> {
                        if (failure == null) {
                            failure = "the signature could not be checked";
                        }
                    }
                    default -> {
                    }
                }
            }
        Status status = new Status(fingerprint, failure);
        if (exitCode != 0 && status.fingerprint() == null && status.failure() == null) {
            throw new IllegalStateException("Unexpected exit code " + exitCode + " and no signature verdict from "
                    + command
                    + "\nTo reproduce, execute:\n "
                    + String.join(" ", handler.commands())
                    + (Files.isRegularFile(error) ? "\n\nError:\n" + Files.readString(error) : ""));
        }
        return status;
    }

    record Status(String fingerprint, String failure) {
    }
}
