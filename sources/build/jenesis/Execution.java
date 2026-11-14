package build.jenesis;

import module java.base;
import build.jenesis.Environment.Setting;
import build.jenesis.docker.DockerizedJava;
import build.jenesis.step.Inventory;
import build.jenesis.step.JavaLaunch;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public record Execution(Project project, String mainClass, String module, Container container) {

    public Execution(Project project) {
        this(project, null, null, null);
    }

    public static Execution ofEnvironment(Environment environment, Project project) {
        return new Execution(project,
                environment.getProperty("execute.main", Setting.PROJECT),
                environment.getProperty("execute.module", Setting.PROJECT),
                Container.ofEnvironment(environment));
    }

    public record Container(String image,
                            String runtime,
                            String readable,
                            String writable,
                            String env,
                            boolean announcing) {

        public static Container ofEnvironment(Environment environment) {
            return environment.flag("execute.docker", Setting.MACHINE)
                    ? new Container(environment.getProperty("execute.image", Setting.MACHINE),
                                    environment.value("execute.runtime", Setting.MACHINE),
                                    environment.getProperty("execute.readable", Setting.MACHINE),
                                    environment.getProperty("execute.writable", Setting.MACHINE),
                                    environment.getProperty("execute.env", Setting.MACHINE),
                                    environment.flag("print.docker", true, Setting.PROJECT))
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
        return execute(project.reproduce(null).build(module == null
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
        int jvmOptions = 0;
        while (jvmOptions < arguments.length && arguments[jvmOptions].startsWith("-J")) {
            if (arguments[jvmOptions].length() == 2) {
                throw new IllegalArgumentException("-J names no option for the program's JVM - write the option right"
                        + " after it, as -J<option>");
            }
            jvmOptions++;
        }
        SequencedProperties merged = new SequencedProperties();
        SequencedMap<String, Path> sourceByPrefix = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : outputs.entrySet()) {
            Path inventory = entry.getValue().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventory)) {
                continue;
            }
            SequencedProperties loaded = SequencedProperties.ofFiles(inventory);
            for (String prefix : Inventory.prefixes(loaded)) {
                if (loaded.getProperty(prefix + ".test") != null) {
                    continue;
                }
                loaded.forEachProperty((key, value) -> {
                    if (key.startsWith(prefix + ".")) {
                        merged.setProperty(key, value);
                    }
                });
                sourceByPrefix.put(prefix, entry.getValue());
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
            candidates.put(prefix, new Candidate(userPath, resolvedMainClass, entry.getValue()));
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
        JavaLaunch launch = JavaLaunch.of(merged, candidate.folder, selected.getKey(), candidate.mainClass);
        List<String> javaArgs = new ArrayList<>(launch.options());
        for (int index = 0; index < jvmOptions; index++) {
            javaArgs.add(arguments[index].substring(2));
        }
        javaArgs.addAll(launch.attached());
        javaArgs.addAll(ProcessBuildStep.argumentFile(argumentFile, launch.paths()));
        javaArgs.addAll(launch.relaxations());
        javaArgs.addAll(launch.main());
        javaArgs.addAll(List.of(arguments).subList(jvmOptions, arguments.length));
        if (container != null) {
            Path root = project.root().toAbsolutePath().normalize();
            DockerizedJava docker = (container.image() == null
                    ? new DockerizedJava(root)
                    : new DockerizedJava(root, container.image())).runtime(container.runtime());
            for (Path path : List.of(project.target(), project.artifacts())) {
                Path absolute = (path.isAbsolute() ? path : root.resolve(path)).normalize();
                if (!absolute.startsWith(root)) {
                    docker = docker.mount(absolute, absolute.toString(), false);
                }
            }
            docker = docker.mounts(container.readable(), root, true)
                    .mounts(container.writable(), root, false)
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
        return ProcessHandler.OfProcess.inheriting(command);
    }

    public static int run(Environment environment,
                          Path root,
                          SequencedSet<Path> profiles,
                          String... arguments) throws IOException, InterruptedException {
        Map<String, String> keys = new HashMap<>(environment.keys());
        keys.remove("project.reproduce");
        environment = environment.keys(keys);
        SequencedMap<String, Path> outputs = Project.perform(environment, root, profiles, Project.BUILD);
        if (outputs == null) {
            return 1;
        }
        Project project = Project.ofEnvironment(environment, root).profiles(profiles.toArray(Path[]::new));
        return ofEnvironment(environment, project).execute(outputs, arguments);
    }

    private record Candidate(String path, String mainClass, Path folder) {
    }
}
