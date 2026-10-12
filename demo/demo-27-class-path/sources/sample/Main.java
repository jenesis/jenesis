package sample;

import java.util.spi.ToolProvider;

public class Main {

    public static void main(String[] arguments) {
        ToolProvider.findFirst("greeter")
                .orElseThrow(() -> new IllegalStateException("No greeter: the jar declares no such service"))
                .run(System.out, System.err, arguments);
    }
}
