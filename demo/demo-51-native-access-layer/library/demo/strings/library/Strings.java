package demo.strings.library;

import java.lang.invoke.MethodHandles;
import build.jenesis.launcher.Launcher;
import demo.strings.spi.Length;

public class Strings {

    public String measure(String text) {
        Length length = Launcher.instance(MethodHandles.lookup(), "strings", Length.class);
        return "strlen(\"" + text + "\") = " + length.of(text)
                + ", measured in the library's layer with native access " + length.granted();
    }
}
