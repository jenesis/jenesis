package demo.signing;

public class Greeting {

    public static void main(String[] args) {
        System.out.println(new Greeting().greet());
    }

    public String greet() {
        return "Hello from a jar that carries a signature";
    }
}
