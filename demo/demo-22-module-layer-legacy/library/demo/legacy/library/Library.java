package demo.legacy.library;

import build.jenesis.launcher.Launcher;
import demo.legacy.spi.Beans;

public class Library {

    public String describe() {
        return Launcher.instance("beans", Beans.class).describe();
    }
}
