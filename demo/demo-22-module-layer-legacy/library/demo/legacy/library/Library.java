package demo.legacy.library;

import java.lang.invoke.MethodHandles;
import build.jenesis.launcher.Launcher;
import demo.legacy.spi.Beans;

public class Library {

    public String describe() {
        return Launcher.instance(MethodHandles.lookup(), "beans", Beans.class).describe();
    }
}
