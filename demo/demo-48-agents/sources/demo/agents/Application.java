package demo.agents;

public class Application {

    private final Greeter greeter;

    public Application(Greeter greeter) {
        this.greeter = greeter;
    }

    public String run(String name) {
        return greeter.greet(name);
    }

    public static void main(String[] arguments) {
        String name = arguments.length > 0 ? String.join(" ", arguments) : "world";
        System.out.println(new Application(new PoliteGreeter()).run(name));
    }
}
