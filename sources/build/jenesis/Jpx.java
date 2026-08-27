package build.jenesis;

import module java.base;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.docker.DockerizedJava;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenRepository;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;
import build.jenesis.module.ModularJarResolver;

public record Jpx(Path storage,
                  Map<String, Repository> repositories,
                  Map<String, Resolver> resolvers,
                  HashDigestFunction hashFunction,
                  PathPlacement placement) {

    public static final String PROPERTIES = "jpx.properties";

    private static final int MINIMUM_CHECKSUM_LENGTH = 32;

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    public Jpx(PathPlacement placement) {
        boolean modular = placement == PathPlacement.MODULE_PATH;
        Repository module = JenesisModuleRepository.of(modular
                ? JenesisRepository.Scope.MODULE
                : JenesisRepository.Scope.ARTIFACT);
        Map<String, Repository> repositories = new LinkedHashMap<>();
        repositories.put("maven", MavenDefaultRepository.of());
        repositories.put("module", module);
        Map<String, Resolver> resolvers = new LinkedHashMap<>();
        MavenPomResolver maven = new MavenPomResolver();
        resolvers.put("maven", maven);
        resolvers.put("module", modular
                ? new ModularJarResolver(false)
                : new MavenModuleResolver("maven", maven, module));
        this(Path.of(System.getProperty("user.home")).resolve(".jenesis").resolve("jpx"),
                Collections.unmodifiableMap(repositories),
                Collections.unmodifiableMap(resolvers),
                new HashDigestFunction("SHA-256"),
                placement);
    }

    public record Command(String name, String version, String mainClass) {

        public static Command parse(String argument) {
            int slash = argument.indexOf('/');
            String mainClass = slash < 0 ? null : argument.substring(slash + 1);
            if (mainClass != null) {
                for (String segment : mainClass.split("\\.", -1)) {
                    if (segment.isEmpty() || !segment.chars().allMatch(Character::isJavaIdentifierPart)) {
                        throw new IllegalArgumentException("Not a class name: " + mainClass);
                    }
                }
            }
            String head = slash < 0 ? argument : argument.substring(0, slash);
            int at = head.lastIndexOf('@');
            String version = at < 0 ? null : head.substring(at + 1);
            if (version != null && version.isEmpty()) {
                throw new IllegalArgumentException("Empty version in: " + argument);
            }
            String name = at < 0 ? head : head.substring(0, at);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Empty name in: " + argument);
            }
            return new Command(name, version, mainClass);
        }

        String folder(String version) {
            return name.replace(":", "--") + "@" + version;
        }
    }

    public record Installation(Path folder, HashDigestFunction hashFunction) {

        public SequencedProperties properties() throws IOException {
            return SequencedProperties.ofFiles(folder.resolve(PROPERTIES));
        }

        public Installation verify(String checksum) throws IOException {
            String expected = requireValidChecksum(checksum);
            SequencedProperties properties = properties();
            SequencedSet<String> names = new TreeSet<>();
            for (String path : new String[]{properties.getProperty("modulepath"), properties.getProperty("classpath")}) {
                if (path != null) {
                    names.addAll(List.of(path.split(",")));
                }
            }
            String computed = HexFormat.of().formatHex(checksum(names));
            if (!computed.startsWith(expected)) {
                throw new IllegalStateException("Checksum mismatch for " + folder.getFileName()
                        + ": expected a digest starting with " + expected + " but computed " + computed);
            }
            return this;
        }

        public int launch(List<String> arguments) throws IOException, InterruptedException {
            return launch(null, arguments);
        }

        public int launch(String mainClass, List<String> arguments) throws IOException, InterruptedException {
            Path argumentFile = Files.createTempFile(folder, "jpx.", ".args");
            try {
                List<String> command = new ArrayList<>();
                command.add(Path.of(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java").toString());
                command.addAll(javaArguments(mainClass, arguments, argumentFile));
                return new ProcessBuilder(command).inheritIO().start().waitFor();
            } finally {
                Files.deleteIfExists(argumentFile);
            }
        }

        public int launch(String mainClass, List<String> arguments, DockerizedJava docker)
                throws IOException, InterruptedException {
            Path argumentFile = Files.createTempFile(folder, "jpx.", ".args");
            try {
                return docker.mount(folder, folder.toString(), true)
                        .execute(javaArguments(mainClass, arguments, argumentFile));
            } finally {
                Files.deleteIfExists(argumentFile);
            }
        }

        public List<String> javaArguments(String mainClass, List<String> arguments, Path argumentFile)
                throws IOException {
            SequencedProperties properties = properties();
            String main = mainClass == null ? properties.getProperty("mainClass") : mainClass;
            if (main == null) {
                throw new IllegalStateException("No main class: the installation " + folder.getFileName()
                        + " declares neither a module main class nor a Main-Class manifest attribute"
                        + " - name one as <name>[@<version>]/<main-class>");
            }
            List<String> command = new ArrayList<>();
            String modulepath = properties.getProperty("modulepath"), classpath = properties.getProperty("classpath");
            SequencedMap<String, String> options = new LinkedHashMap<>();
            options.put("-p", modulepath == null ? null : join(modulepath));
            options.put("-cp", classpath == null ? null : join(classpath));
            command.addAll(ProcessBuildStep.argumentFile(argumentFile, options));
            if (modulepath != null) {
                command.addAll(ModuleGraph.load(properties));
            }
            String mainModule = properties.getProperty("mainModule");
            if (mainModule != null) {
                command.add("-m");
                command.add(mainModule + "/" + main);
            } else {
                command.add(main);
            }
            command.addAll(arguments);
            return command;
        }

        private byte[] checksum(SequencedCollection<String> names) throws IOException {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(hashFunction.algorithm());
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            for (String name : new TreeSet<>(names)) {
                digest.update((name + "\t" + HexFormat.of().formatHex(hashFunction.hash(folder.resolve(name))) + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return digest.digest();
        }

        private String join(String names) {
            return Arrays.stream(names.split(","))
                    .map(name -> folder.resolve(name).toString())
                    .collect(Collectors.joining(File.pathSeparator));
        }
    }

    public static final String HELP = """
            Usage: jpx [--modular] [--docker[=<image>]] [--hash=<checksum>] <target> [argument...]

            Runs the main entry point of a published module, resolving and installing
            it on first use.

            Target: <name>[@<version>][/<main-class>]

              The name is a module name, its coordinates discovered as a POM and the
              graph read from Maven metadata, or a <groupId>:<artifactId> pair, resolved
              directly. Without a version, the latest installed version is preferred,
              then the latest release is resolved. The main class defaults to the jar's
              module main class or Main-Class manifest entry - name one to override it,
              as in java -m <module>/<main-class>.

              A module name runs as a module: every jar that describes one is placed on
              the module path, any jar that does not on the class path. A
              <groupId>:<artifactId> pair names an artifact rather than a module, and
              runs on the class path in full.

            Installations live in ~/.jenesis/jpx/<name>@<version>/ beside a
            jpx.properties descriptor listing paths, entry point and checksum.

            Options:
              --modular           resolve purely over module descriptors, walking requires
                                  clauses, and place every jar on the module path; every
                                  module must then be explicitly named
              --docker[=<image>]  run the program in a Docker container; resolution and
                                  installation still happen on the host, the installation
                                  and the host's Java home are mounted read-only. Without
                                  an image, a minimal hardened image is used
              --hash=<checksum>   verify the installed jars against a SHA-256 digest
                                  prefix (at least 32 hex characters) before launching
              --help              print this help""";

    public static void main(String... arguments) throws IOException, InterruptedException {
        PathPlacement placement = PathPlacement.INFERRED;
        boolean dockerized = false;
        String image = null, checksum = null;
        int target = 0;
        while (target < arguments.length && arguments[target].startsWith("--")) {
            switch (arguments[target]) {
                case "--modular" -> placement = PathPlacement.MODULE_PATH;
                case "--docker" -> dockerized = true;
                case "--help" -> {
                    System.out.println(HELP);
                    System.exit(0);
                }
                default -> {
                    if (arguments[target].startsWith("--docker=")) {
                        dockerized = true;
                        String value = arguments[target].substring("--docker=".length());
                        image = value.isBlank() ? null : value;
                    } else if (arguments[target].startsWith("--hash=")) {
                        checksum = requireValidChecksum(arguments[target].substring("--hash=".length()));
                    } else {
                        System.err.println("Unknown option: " + arguments[target]);
                        System.err.println(HELP);
                        System.exit(64);
                    }
                }
            }
            target++;
        }
        if (arguments.length == target) {
            System.err.println(HELP);
            System.exit(64);
        }
        Command command = Command.parse(arguments[target]);
        if (placement == PathPlacement.MODULE_PATH && command.name().indexOf(':') >= 0) {
            throw new IllegalArgumentException("Pure module resolution requires a module name, "
                    + "not Maven coordinates: " + command.name());
        }
        Installation installation = new Jpx(placement).install(command);
        if (checksum != null) {
            installation.verify(checksum);
        }
        List<String> remaining = List.of(arguments).subList(target + 1, arguments.length);
        if (dockerized) {
            Path workingDirectory = Path.of("").toAbsolutePath();
            System.exit(installation.launch(command.mainClass(), remaining, image == null
                    ? new DockerizedJava(workingDirectory)
                    : new DockerizedJava(workingDirectory, image)));
        } else {
            System.exit(installation.launch(command.mainClass(), remaining));
        }
    }

    public Installation install(String target) throws IOException {
        return install(Command.parse(target));
    }

    private Path layout(Command command) {
        if (command.name().indexOf(':') >= 0) {
            return storage.resolve("maven");
        }
        return storage.resolve(placement == PathPlacement.MODULE_PATH ? "modular" : "modular_to_maven");
    }

    public Installation install(Command command) throws IOException {
        if (command.version() == null) {
            Installation installed = latestInstalled(command.name()).orElse(null);
            if (installed != null) {
                return installed;
            }
        } else {
            Path folder = layout(command).resolve(command.folder(command.version()));
            if (Files.isRegularFile(folder.resolve(PROPERTIES))) {
                return new Installation(folder, hashFunction);
            }
        }
        Path installations = Files.createDirectories(layout(command));
        Path staging = Files.createTempDirectory(installations, "staging-");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Repository> repositories = new LinkedHashMap<>();
            this.repositories.forEach((name, repository) -> repositories.put(name, repository.spilled(staging)));
            Resolver.Resolution resolution;
            String version = command.version(), root;
            int colon = command.name().indexOf(':');
            if (colon < 0) {
                SAFE_SEGMENT.accept("module name", command.name());
                String head = "module/" + command.name();
                SequencedMap<String, String> versions = new LinkedHashMap<>();
                if (version != null) {
                    versions.put(command.name(), version);
                }
                resolution = resolvers.get("module").dependencies(executor,
                        "module",
                        repositories,
                        new LinkedHashMap<>(Map.of(command.name(), Collections.emptyNavigableSet())),
                        versions,
                        DependencyScope.RUNTIME);
                root = null;
                for (String coordinate : resolution.artifacts().sequencedKeySet()) {
                    if (coordinate.equals(head) || coordinate.startsWith(head + "/")) {
                        root = coordinate;
                        break;
                    }
                }
                if (root == null) {
                    throw new IllegalStateException("Resolution did not retain a root entry for " + command.name());
                }
                if (version == null) {
                    int slash = root.lastIndexOf('/');
                    version = slash < head.length() ? null : root.substring(slash + 1);
                }
                if (version == null) {
                    throw new IllegalStateException("Cannot determine a version for " + command.name()
                            + " - specify one as " + command.name() + "@<version>");
                }
            } else {
                String groupId = command.name().substring(0, colon), artifactId = command.name().substring(colon + 1);
                SAFE_SEGMENT.accept("group", groupId);
                SAFE_SEGMENT.accept("artifact", artifactId);
                if (version == null) {
                    version = MavenDefaultVersionNegotiator.maven().get().resolve(executor,
                            MavenRepository.of(repositories.get("maven")),
                            groupId, artifactId, "jar", null, "RELEASE");
                }
                resolution = resolvers.get("maven").dependencies(executor,
                        "maven",
                        repositories,
                        new LinkedHashMap<>(Map.of(groupId + "/" + artifactId, Collections.emptyNavigableSet())),
                        new LinkedHashMap<>(Map.of(groupId + "/" + artifactId, version)),
                        DependencyScope.RUNTIME);
                root = "maven/" + groupId + "/" + artifactId + "/" + version;
                if (!resolution.artifacts().containsKey(root)) {
                    throw new IllegalStateException("Resolution did not retain a root entry for " + command.name());
                }
            }
            SAFE_SEGMENT.accept("version", version);
            Installation installation = new Installation(installations.resolve(command.folder(version)), hashFunction);
            if (!Files.isRegularFile(installation.folder().resolve(PROPERTIES))) {
                try (FileChannel channel = FileChannel.open(installations.resolve(command.folder(version) + ".lock"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE); FileLock _ = channel.lock()) {
                    if (!Files.isRegularFile(installation.folder().resolve(PROPERTIES))) {
                        materialize(command, version, resolution, resolution.artifacts().get(root).file(), staging, installation);
                    }
                }
            }
            return installation;
        } finally {
            if (Files.exists(staging)) {
                clear(staging);
                Files.delete(staging);
            }
        }
    }

    private void materialize(Command command,
                             String version,
                             Resolver.Resolution resolution,
                             Path root,
                             Path staging,
                             Installation installation) throws IOException {
        Path folder = installation.folder();
        if (Files.exists(folder)) {
            clear(folder);
            Files.delete(folder);
        }
        Files.move(staging, folder);
        if (root.startsWith(staging)) {
            root = folder.resolve(root.getFileName());
        }
        PathPlacement placement = command.name().indexOf(':') >= 0
                ? PathPlacement.CLASS_PATH
                : this.placement;
        SequencedMap<String, Path> jars = new TreeMap<>();
        SequencedMap<String, String> tokens = new LinkedHashMap<>(), names = new LinkedHashMap<>();
        for (Map.Entry<String, Resolver.Resolved> entry : resolution.artifacts().entrySet()) {
            String dependency = entry.getKey();
            Path file = entry.getValue().file();
            Path source = file.startsWith(staging) ? folder.resolve(file.getFileName()) : file;
            String coordinate = dependency.substring(dependency.indexOf('/') + 1);
            ModuleDescriptor identity = PathPlacement.moduleDescriptor(source);
            String name = identity == null
                    ? PathPlacement.fileName(coordinate)
                    : PathPlacement.fileName(coordinate, identity.name(), true);
            Path target = folder.resolve(name);
            if (jars.putIfAbsent(name, target) == null && !source.equals(target)) {
                if (source.startsWith(folder)) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    BuildStep.linkOrCopy(target, source);
                }
            }
            names.putIfAbsent(dependency, name);
            int last = coordinate.lastIndexOf('/');
            if (last > 0) {
                tokens.putIfAbsent(coordinate.substring(0, last), dependency);
            }
            if (source.equals(root)) {
                root = target;
            }
        }
        root = rename(jars, tokens, names, folder, root);
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(root);
        if (placement != PathPlacement.CLASS_PATH
                && (descriptor == null || !descriptor.name().equals(command.name()))) {
            throw new IllegalStateException("The jar resolved for module " + command.name() + " is named "
                    + (descriptor == null ? "nothing" : descriptor.name()) + " - the repository mapping appears stale");
        }
        String mainModule = placement.modular() && descriptor != null ? descriptor.name() : null;
        String mainClass = descriptor == null || descriptor.mainClass().isEmpty()
                ? PathPlacement.mainClass(root)
                : descriptor.mainClass().get();
        List<String> modulepath = new ArrayList<>(), classpath = new ArrayList<>();
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            boolean placed = graph.place(placement, folder.resolve(entry.getKey()));
            (placed ? modulepath : classpath).add(entry.getKey());
        }
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("name", command.name());
        properties.setProperty("version", version);
        if (mainClass != null) {
            properties.setProperty("mainClass", mainClass);
        }
        if (mainModule != null) {
            properties.setProperty("mainModule", mainModule);
        }
        if (!modulepath.isEmpty()) {
            properties.setProperty("modulepath", String.join(",", modulepath));
        }
        graph.store(properties);
        if (!classpath.isEmpty()) {
            properties.setProperty("classpath", String.join(",", classpath));
        }
        properties.setProperty("checksum", hashFunction.encoded(installation.checksum(jars.sequencedKeySet())));
        Path temporary = Files.createTempFile(folder, PROPERTIES, ".tmp");
        properties.store(temporary);
        Files.move(temporary, folder.resolve(PROPERTIES), StandardCopyOption.ATOMIC_MOVE);
    }

    private static Path rename(SequencedMap<String, Path> jars,
                               SequencedMap<String, String> tokens,
                               SequencedMap<String, String> names,
                               Path folder,
                               Path root) throws IOException {
        SequencedMap<String, String> aliased = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            String origin = entry.getKey();
            for (Map.Entry<String, String> declaration : PathPlacement.aliases(entry.getValue()).entrySet()) {
                String alias = declaration.getKey(), dependency = tokens.get(declaration.getValue());
                if (dependency == null || !jars.containsKey(names.get(dependency))) {
                    throw new IllegalStateException(origin
                            + " aliases "
                            + alias
                            + " to "
                            + declaration.getValue()
                            + ", which this installation does not contain");
                }
                String previous = aliased.putIfAbsent(alias, dependency);
                if (previous != null && !previous.equals(dependency)) {
                    throw new IllegalStateException("Module alias "
                            + alias
                            + " is declared for "
                            + previous
                            + " and for "
                            + dependency
                            + " within "
                            + folder.getFileName());
                }
            }
        }
        SequencedMap<String, String> owners = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliased.entrySet()) {
            String alias = entry.getKey(), dependency = entry.getValue();
            String previous = owners.putIfAbsent(dependency, alias);
            if (previous != null) {
                throw new IllegalStateException(dependency
                        + " is aliased as both "
                        + previous
                        + " and "
                        + alias
                        + " - a jar can carry only one module name");
            }
            String coordinate = dependency.substring(dependency.indexOf('/') + 1);
            String name = PathPlacement.fileName(coordinate, alias, false);
            if (jars.containsKey(name)) {
                throw new IllegalStateException("Module alias "
                        + alias
                        + " collides with "
                        + name
                        + " within "
                        + folder.getFileName()
                        + " - require it directly");
            }
            Path source = folder.resolve(names.get(dependency)), target = folder.resolve(name);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            jars.remove(names.get(dependency));
            jars.put(name, target);
            names.put(dependency, name);
            PathPlacement.aliased(target, alias, dependency);
            Files.writeString(PathPlacement.declaration(target), alias);
            if (root.equals(source)) {
                root = target;
            }
        }
        return root;
    }

    public Optional<Installation> latestInstalled(String name) throws IOException {
        Path root = layout(new Command(name, null, null));
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        String prefix = new Command(name, null, null).folder("");
        Path latest = null;
        FileTime time = null;
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(root)) {
            for (Path folder : folders) {
                if (!folder.getFileName().toString().startsWith(prefix) || !Files.isRegularFile(folder.resolve(PROPERTIES))) {
                    continue;
                }
                FileTime candidate = Files.getLastModifiedTime(folder.resolve(PROPERTIES));
                if (time == null || candidate.compareTo(time) > 0) {
                    latest = folder;
                    time = candidate;
                }
            }
        }
        return latest == null ? Optional.empty() : Optional.of(new Installation(latest, hashFunction));
    }

    private static void clear(Path folder) throws IOException {
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                if (!directory.equals(folder)) {
                    Files.delete(directory);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String requireValidChecksum(String checksum) {
        String normalized = checksum.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha-256/")) {
            normalized = normalized.substring("sha-256/".length());
        }
        if (normalized.length() < MINIMUM_CHECKSUM_LENGTH) {
            throw new IllegalArgumentException("A checksum requires at least " + MINIMUM_CHECKSUM_LENGTH
                    + " hex characters to remain secure, but got " + normalized.length() + ": " + normalized);
        }
        if (!normalized.chars().allMatch(character -> character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f')) {
            throw new IllegalArgumentException("Not a hexadecimal checksum: " + normalized);
        }
        return normalized;
    }
}
