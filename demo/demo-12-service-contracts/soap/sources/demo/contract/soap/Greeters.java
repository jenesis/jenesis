package demo.contract.soap;

import demo.greeter.GreeterPort;
import demo.greeter.GreeterService;

public class Greeters {

    public static GreeterPort port() {
        return new GreeterService().getGreeterPort();
    }

    public static String greet(GreeterPort port, String name) {
        return port.greet(name);
    }
}
