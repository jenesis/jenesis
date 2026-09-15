package demo.layers.impl;

import build.jenesis.launcher.Launcher;
import com.fasterxml.jackson.core.json.PackageVersion;
import demo.layers.spi.Nested;
import demo.layers.spi.Report;

public class JacksonReport implements Report {

    @Override
    public String render() {
        Nested nested = Launcher.load("inner", Nested.class).findFirst().orElseThrow();
        return "the library's private jackson-core " + PackageVersion.VERSION
                + ", loaded by " + PackageVersion.class.getClassLoader()
                + "\n    and one layer deeper: " + nested.describe();
    }
}
