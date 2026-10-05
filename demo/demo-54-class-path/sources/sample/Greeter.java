package sample;

import java.io.PrintWriter;
import java.util.spi.ToolProvider;

public class Greeter implements ToolProvider {

    @Override
    public String name() {
        return "greeter";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... arguments) {
        out.println("Hello from a service, found by " + (Greeter.class.getModule().isNamed()
                ? "the module path"
                : "the class path"));
        return 0;
    }
}
