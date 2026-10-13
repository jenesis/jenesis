package sample;

import java.util.function.Supplier;
import net.bytebuddy.build.ToStringPlugin;

@ToStringPlugin.Enhance
public class Greeting implements Supplier<String> {

    private final String audience;

    public Greeting(String audience) {
        this.audience = audience;
    }

    @Override
    public String get() {
        return "Hello from javac, " + audience + "!";
    }
}
