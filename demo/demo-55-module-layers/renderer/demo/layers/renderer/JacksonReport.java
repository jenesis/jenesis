package demo.layers.renderer;

import com.fasterxml.jackson.core.json.PackageVersion;
import demo.layers.api.Report;

public class JacksonReport implements Report {

    @Override
    public String render() {
        return "the layer sees jackson-core "
                + PackageVersion.VERSION
                + ", loaded by "
                + PackageVersion.class.getClassLoader();
    }
}
