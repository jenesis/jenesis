package demo.layers.library;

import build.jenesis.launcher.Launcher;
import demo.layers.spi.Report;

public class Library {

    public String report() {
        return Launcher.load("render", Report.class).findFirst().orElseThrow().render();
    }
}
