package demo.greeter;

public class Greeter {

    public static String greet(String name) {
        return "Hello, " + name + ", from demo.greeter "
                + Greeter.class.getModule().getDescriptor().rawVersion().orElse("(unversioned)");
    }
}
