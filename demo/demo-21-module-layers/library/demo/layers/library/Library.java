package demo.layers.library;

import java.lang.invoke.MethodHandles;
import build.jenesis.launcher.Launcher;
import demo.layers.spi.Report;

public class Library {

    public String report() {
        return Launcher.instance(MethodHandles.lookup(), "render", Report.class).render();
    }
}
