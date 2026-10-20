package demo.greet;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GreetPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("hello", task -> task.doLast(action ->
                System.out.println("Hello from a Gradle plugin that Jenesis built!")));
    }
}
