package demo.reproducible;

public record Greeting(String name) {

    public String text() {
        return "Hello, " + name + ", from the same bytes on every machine!";
    }
}
