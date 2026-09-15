package demo.agents;

public class PoliteGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name + ", from an application instrumented by a Java agent.";
    }
}
