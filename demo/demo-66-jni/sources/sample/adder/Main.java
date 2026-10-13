package sample.adder;

public class Main {

    public static void main(String[] args) {
        int a = args.length > 0 ? Integer.parseInt(args[0]) : 2, b = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        System.out.println(a + " + " + b + " = " + Adder.add(a, b) + ", added in C");
    }
}
