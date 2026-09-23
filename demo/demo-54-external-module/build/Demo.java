package build;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Execution;
import build.jenesis.Make;
import build.jenesis.Project;

public class Demo {

    static void main(String[] args) throws Exception {
        Function<String, String> settings = Make.settings(Path.of(".")).keys();
        String local = Path.of("target", "local").toAbsolutePath().toString();
        Environment environment = new Environment(key -> key.equals("module.local") ? local : settings.apply(key));
        Files.createDirectories(Path.of("target"));
        Project.ofEnvironment(environment, Path.of("plugin")).target(Path.of("target", "plugin")).build("export");
        System.exit(new Execution(Project.ofEnvironment(environment, Path.of("."))).execute(args));
    }
}
