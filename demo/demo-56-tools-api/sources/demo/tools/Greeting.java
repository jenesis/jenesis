package demo.tools;

public class Greeting {

    public String text() {
        return "hello";
    }

    public static void main(String... args) {
        System.out.println(new Greeting().text());
    }
}
