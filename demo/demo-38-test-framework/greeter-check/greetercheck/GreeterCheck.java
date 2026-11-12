package greetercheck;

import sample.greeter.Greeter;

public class GreeterCheck {

    public static void main(String[] args) {
        String prefix = new Greeter().prefix();
        if (!prefix.equals("Hello")) {
            System.err.println("GreeterCheck failed: the prefix is " + prefix);
            System.exit(1);
        }
        System.out.println("GreeterCheck passed");
    }
}
