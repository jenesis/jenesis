package demo.layers.impl;

import com.fasterxml.jackson.core.json.PackageVersion;
import demo.layers.spi.Report;

public class JacksonReport implements Report {

    @Override
    public String render() {
        return "the library's private jackson-core " + PackageVersion.VERSION
                + ", loaded by " + PackageVersion.class.getClassLoader();
    }
}
