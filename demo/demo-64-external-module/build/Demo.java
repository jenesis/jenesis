package build;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Execution;
import build.jenesis.Make;
import build.jenesis.Project;

public class Demo {

    static void main(String[] args) throws Exception {
        Map<String, String> settings = new HashMap<>(Make.settings(Path.of(".")).keys());
        settings.put("module.local", Path.of("target", "local").toAbsolutePath().toString());
        settings.put("maven.local", Files.createDirectories(Path.of("target", "m2")).toAbsolutePath().toString());
        Environment environment = new Environment(settings);
        Project.ofEnvironment(environment, Path.of("plugin")).target(Path.of("target", "plugin")).version("1.0.0").build("export");
        System.exit(new Execution(Project.ofEnvironment(environment, Path.of("."))).execute(args));
    }
}
