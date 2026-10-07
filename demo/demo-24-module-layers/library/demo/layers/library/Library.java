package demo.layers.library;

import build.jenesis.launcher.Launcher;
import demo.layers.spi.Report;
import java.lang.invoke.MethodHandles;

public class Library {

    public String report() {
        return Launcher.instance(MethodHandles.lookup(), "render", Report.class).render();
    }
}
