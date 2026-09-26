package library;

public class Library {

    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    public String greet(String name, String title) {
        return "Hello, " + title + " " + name + "!";
    }

    public String farewell(String name) {
        return "Goodbye, " + name + "!";
    }
}
