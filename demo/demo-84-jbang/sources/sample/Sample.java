package sample;

public class Sample {

    public static void main(String[] args) {
        System.out.println("Hello, " + (args.length == 0 ? "world" : String.join(" ", args))
                + ", from a module that a JBang script built and staged!");
    }
}
