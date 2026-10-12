package sample;

public class Sample {

    public static String describe(long value) {
        return switch (value) {
            case int small -> value + " fits an int (" + small + ")";
            case long large -> value + " needs a long (" + large + ")";
        };
    }

    public static void main(String[] args) {
        System.out.println(describe(args.length == 0 ? 42 : Long.parseLong(args[0])));
    }
}
