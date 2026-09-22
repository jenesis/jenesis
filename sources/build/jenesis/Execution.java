package build.jenesis;

import module java.base;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.docker.DockerizedJava;
import build.jenesis.step.Inventory;
import static build.jenesis.SequencedProperties.SYSTEM;

public record Execution(Project project, String mainClass, String module, Container container) {

    public Execution(Project project) {
        this(project, null, null, null);
    }

    public static Execution ofKeys(Function<String, String> keys, Project project) {
        return new Execution(project,
                SequencedProperties.getProperty(keys, "execute.mainClass"),
                SequencedProperties.getProperty(keys, "execute.module"),
                Container.ofKeys(keys));
    }

    public record Container(String image, String mount, String mountWritable, String env, boolean announcing) {

        public static Container ofKeys(Function<String, String> keys) {
            return SequencedProperties.flag(keys, "execute.docker")
                    ? new Container(SequencedProperties.getProperty(keys, "execute.docker.image"),
                            SequencedProperties.getProperty(keys, "execute.docker.mount"),
                            SequencedProperties.getProperty(keys, "execute.docker.mountWritable"),
                            SequencedProperties.getProperty(keys, "execute.docker.env"),
                            SequencedProperties.flag(keys, "print.docker", true))
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
        if (candidate.module != null) {
            ModuleGraph graph = new ModuleGraph();
            List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
            for (String jar : jars) {
                graph.place(PathPlacement.INFERRED, Path.of(jar), modulePath, classPath);
            }
            SequencedMap<String, String> options = new LinkedHashMap<>();
            options.put("--module-path", String.join(File.pathSeparator, modulePath));
            options.put("--class-path", String.join(File.pathSeparator, classPath));
            javaArgs.addAll(ProcessBuildStep.argumentFile(argumentFile, options));
            javaArgs.addAll(graph.arguments());
            javaArgs.add("-m");
            javaArgs.add(candidate.module + "/" + candidate.mainClass);
        } else {
            javaArgs.addAll(ProcessBuildStep.argumentFile(argumentFile,
                    new LinkedHashMap<>(Map.of("-cp", String.join(File.pathSeparator, jars)))));
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
                System.out.println("Launching Java execution within Docker image: " + docker.image());
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

    public static void main(String... arguments) {
        try {
            Path root = Path.of(SequencedProperties.getProperty(SYSTEM, "make.root", "."));
            Function<String, String> keys = Make.settings(root).keys();
            Make.Result result = new Make(Project.class.getName()).build(Project.BUILD);
            if (result.code() != 0) {
                System.exit(result.code());
            }
            Project project = Project.ofKeys(keys, root);
            int code = Execution.ofKeys(keys, project).execute(result.outputs(), arguments);
            if (code != 0) {
                System.exit(code);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed using arguments " + List.of(arguments), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed using arguments " + List.of(arguments), e);
        }
    }

    private record Candidate(String path,
                             String mainClass,
                             String module,
                             List<Path> runtime,
                             Path folder) {
    }
}
