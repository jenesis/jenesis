package greetertesting;

public class Greetings {

    public static boolean isGreeting(String value) {
        return value.startsWith("hello") && value.contains("packaged resource");
    }
}
