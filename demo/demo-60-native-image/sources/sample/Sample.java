package sample;

public class Sample {

    public static void main(String[] args) throws Exception {
        String who = args.length == 0 ? "world" : args[0];
        String name = args.length > 1 ? args[1] : "Greeter";
        Class<?> greeter = Class.forName("sample." + name);
        Object greeting = greeter.getMethod("greet", String.class)
                .invoke(greeter.getConstructor().newInstance(), who);
        System.out.println(greeting);
    }
}
