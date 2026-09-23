package build.jenesis;

import module java.base;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.docker.DockerizedJava;
import build.jenesis.step.Inventory;

public record Execution(Project<?> project, String mainClass, String module, Container container) {

    public Execution(Project<?> project) {
        this(project, null, null, null);
    }

    public static Execution ofEnvironment(Environment environment, Project<?> project) {
        return new Execution(project,
                environment.getProperty("execute.mainClass"),
                environment.getProperty("execute.module"),
                Container.ofEnvironment(environment));
    }

    public record Container(String image, String mount, String mountWritable, String env, boolean announcing) {

        public static Container ofEnvironment(Environment environment) {
            return environment.flag("execute.docker")
                    ? new Container(environment.getProperty("execute.docker.image"),
                                    environment.getProperty("execute.docker.mount"),
                                    environment.getProperty("execute.docker.mountWritable"),
                                    environment.getProperty("execute.docker.env"),
                                    environment.flag("print.docker", true))
                    : null;
        }
    }

    public Execution mainClass(String mainClass) {
        return new Execution(project, mainClass, module, container);
    }

    public Execution module(String module) {
        return new Execution(project, mainClass, module, container);
    }

    public Execution container(Container container) {
        return new Execution(project, mainClass, module, container);
    }

    public int execute(String... arguments) throws IOException, InterruptedException {
        return execute(project.build(module == null
                ? Project.BUILD
                : "+" + module.replace('/', '+')), arguments);
    }

    public int execute(SequencedMap<String, Path> outputs, String... arguments)
            throws IOException, InterruptedException {
        Path argumentFile = Files.createTempFile(Files.createDirectories(project.target()), "execute.", ".args");
        try {
            return doExecute(outputs, argumentFile, arguments);
        } finally {
            Files.deleteIfExists(argumentFile);
        }
    }

    private int doExecute(SequencedMap<String, Path> outputs,
                          Path argumentFile,
                          String... arguments) throws IOException, InterruptedException {
        SequencedProperties merged = new SequencedProperties();
        SequencedMap<String, Path> sourceByPrefix = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : outputs.entrySet()) {
            Path inventory = entry.getValue().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventory)) {
                continue;
            }
            SequencedProperties loaded = SequencedProperties.ofFiles(inventory);
            for (String key : loaded.stringPropertyNames()) {
                merged.setProperty(key, loaded.getProperty(key));
                int dot = key.indexOf('.');
                if (dot > 0 && key.endsWith(".path")) {
                    sourceByPrefix.put(key.substring(0, dot), entry.getValue());
                }
            }
        }
        String selectedPrefix = module == null
                ? null
                : (module.isEmpty() ? "module" : "module-" + module.replace('+', '/'));
        if (selectedPrefix != null && !sourceByPrefix.containsKey(selectedPrefix)) {
            throw new IllegalStateException("No module at path: " + (module.isEmpty() ? "<root>" : module));
        }
        SequencedMap<String, Candidate> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : sourceByPrefix.entrySet()) {
            String prefix = entry.getKey();
            if (selectedPrefix != null && !selectedPrefix.equals(prefix)) {
                continue;
            }
            String prefixDot = prefix + ".";
            String resolvedMainClass = mainClass != null ? mainClass : merged.getProperty(prefixDot + "mainClass");
            if (resolvedMainClass == null) {
                continue;
            }
            String userPath = "module".equals(prefix) ? "" : prefix.substring("module-".length());
            candidates.put(prefix, new Candidate(userPath,
                    resolvedMainClass,
                    merged.getProperty(prefixDot + "module"),
                    Inventory.paths(merged, entry.getValue(), prefixDot + "runtime"),
                    entry.getValue()));
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(module != null
                    ? "No module at path: " + (module.isEmpty() ? "<root>" : module)
                    : "No module declares a main class");
        }
        if (candidates.size() > 1) {
            StringBuilder message = new StringBuilder("Multiple modules declare a main class, select one explicitly:");
            for (Candidate candidate : candidates.values()) {
                message.append(System.lineSeparator())
                        .append("  ")
                        .append(candidate.path.isEmpty() ? "<root>" : candidate.path)
                        .append(" -> ")
                        .append(candidate.mainClass);
            }
            throw new IllegalStateException(message.toString());
        }
        Map.Entry<String, Candidate> selected = candidates.firstEntry();
        Candidate candidate = selected.getValue();
        if (candidate.runtime == null || candidate.runtime.isEmpty()) {
            throw new IllegalStateException("No runtime artifacts for module: "
                    + (candidate.path.isEmpty() ? "<root>" : candidate.path));
        }
        List<String> jars = new ArrayList<>();
        for (Path resolved : candidate.runtime) {
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalStateException("Missing runtime artifact for module "
                        + (candidate.path.isEmpty() ? "<root>" : candidate.path)
                        + ": " + resolved);
            }
            jars.add(resolved.toString());
        }
        List<String> javaArgs = new ArrayList<>();
        for (int index = 0; ; index++) {
            String agent = merged.getProperty(selected.getKey() + ".agent." + index);
            if (agent == null) {
                break;
            }
            String coordinate = merged.getProperty(selected.getKey() + ".agent." + index + ".coordinate");
            Path jar = candidate.folder.resolve(agent).normalize();
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Missing agent artifact " + coordinate + ": " + jar);
            }
            try (JarFile file = new JarFile(jar.toFile())) {
                Manifest manifest = file.getManifest();
                if (manifest == null || manifest.getMainAttributes().getValue("Premain-Class") == null) {
                    throw new IllegalStateException("Attached agent "
                            + coordinate
                            + " does not declare Premain-Class: "
                            + jar);
                }
            }
            String options = merged.getProperty(selected.getKey() + ".agent." + index + ".arguments");
            javaArgs.add("-javaagent:" + jar.toAbsolutePath() + (options == null ? "" : "=" + options));
        }
        List<Path> granted = Inventory.paths(merged, candidate.folder, selected.getKey() + ".nativeAccess");
        ModuleGraph graph = new ModuleGraph();
        if (candidate.module != null) {
            List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
            for (String jar : jars) {
                Path file = Path.of(jar);
                boolean placed = graph.place(PathPlacement.INFERRED, file);
                (placed ? modulePath : classPath).add(jar);
                if (granted.contains(file)) {
                    graph.enableNativeAccess(file, placed);
                }
            }
            SequencedMap<String, String> options = new LinkedHashMap<>();
            options.put("--module-path", String.join(File.pathSeparator, modulePath));
            options.put("--class-path", String.join(File.pathSeparator, classPath));
            javaArgs.addAll(ProcessBuildStep.argumentFile(argumentFile, options));
            javaArgs.addAll(graph.arguments());
            javaArgs.add("-m");
            javaArgs.add(candidate.module + "/" + candidate.mainClass);
        } else {
            for (Path file : granted) {
                graph.enableNativeAccess(file, false);
            }
            javaArgs.addAll(ProcessBuildStep.argumentFile(argumentFile,
                    new LinkedHashMap<>(Map.of("-cp", String.join(File.pathSeparator, jars)))));
            javaArgs.addAll(graph.arguments());
            javaArgs.add(candidate.mainClass);
        }
        javaArgs.addAll(List.of(arguments));
        if (container != null) {
            Path root = project.root().toAbsolutePath().normalize();
            DockerizedJava docker = container.image() == null
                    ? new DockerizedJava(root)
                    : new DockerizedJava(root, container.image());
            for (Path path : List.of(project.target(), project.artifacts())) {
                Path absolute = (path.isAbsolute() ? path : root.resolve(path)).normalize();
                if (!absolute.startsWith(root)) {
                    docker = docker.mount(absolute, absolute.toString(), false);
                }
            }
            docker = docker.mounts(container.mount(), root, true)
                    .mounts(container.mountWritable(), root, false)
                    .envs(container.env());
            if (container.announcing()) {
                project.environment().out().accept("Launching Java execution within Docker image: " + docker.image());
            }
            return docker.execute(javaArgs);
        }
        String home = System.getProperty("java.home");
        if (home == null) {
            home = System.getenv("JAVA_HOME");
        }
        if (home == null) {
            throw new IllegalStateException("Neither java.home property nor JAVA_HOME environment is set");
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path javaExecutable = Path.of(home, "bin", "java" + (windows ? ".exe" : ""));
        if (!Files.isRegularFile(javaExecutable)) {
            throw new IllegalStateException("No java executable at " + javaExecutable);
        }
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.addAll(javaArgs);
        return new ProcessBuilder(command).inheritIO().start().waitFor();
    }

    public static int run(Environment environment,
                          Path root,
                          SequencedSet<Path> profiles,
                          String... arguments) throws IOException, InterruptedException {
        SequencedMap<String, Path> outputs = Project.perform(environment, root, profiles, Project.BUILD);
        if (outputs == null) {
            return 1;
        }
        Project<?> project = Project.ofEnvironment(environment, root).profiles(profiles.toArray(Path[]::new));
        return ofEnvironment(environment, project).execute(outputs, arguments);
    }

    private record Candidate(String path,
                             String mainClass,
                             String module,
                             List<Path> runtime,
                             Path folder) {
    }
}
