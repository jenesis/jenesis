package demo.layers.nested;

import com.fasterxml.jackson.core.json.PackageVersion;
import demo.layers.spi.Nested;

public class NestedReport implements Nested {

    @Override
    public String describe() {
        return "jackson-core " + PackageVersion.VERSION;
    }
}
