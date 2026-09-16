package demo.layers.library;

import build.jenesis.launcher.Launcher;
import demo.layers.spi.Report;

public class Library {

    public String report() {
        return Launcher.instance("render", Report.class).render();
    }
}
