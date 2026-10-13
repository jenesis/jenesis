package demo.legacy.library;

import build.jenesis.launcher.Launcher;
import demo.legacy.spi.Beans;
import java.lang.invoke.MethodHandles;

public class Library {

    public String describe() {
        return Launcher.instance(MethodHandles.lookup(), "beans", Beans.class).describe();
    }
}
