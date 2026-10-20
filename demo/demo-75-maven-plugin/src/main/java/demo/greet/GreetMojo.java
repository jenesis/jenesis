package demo.greet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Greets whoever runs the build.
 */
@Mojo(name = "hello", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class GreetMojo extends AbstractMojo {

    /**
     * The name to greet.
     */
    @Parameter(property = "greet.name", defaultValue = "${user.name}")
    private String name;

    @Override
    public void execute() {
        getLog().info("Hello, " + name + "!");
    }
}
