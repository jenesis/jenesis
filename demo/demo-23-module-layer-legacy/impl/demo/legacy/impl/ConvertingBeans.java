package demo.legacy.impl;

import demo.legacy.spi.Beans;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.CodeSource;
import org.apache.commons.beanutils.ConvertUtils;

public class ConvertingBeans implements Beans {

    @Override
    public String describe() {
        Object converted = ConvertUtils.convert("42", Integer.class);
        return "commons-beanutils converted \"42\" to " + converted.getClass().getSimpleName()
                + " " + converted
                + ", logging through " + logging();
    }

    /**
     * Where commons-logging came from. The layer's own copy is on its class path, so it is reached
     * through the loader rather than named: this module could not require it if it wanted to, since
     * a module with a descriptor of its own cannot read the unnamed module.
     */
    private static String logging() {
        try {
            Class<?> factory = Class.forName("org.apache.commons.logging.LogFactory", false,
                    ConvertUtils.class.getClassLoader());
            CodeSource source = factory.getProtectionDomain().getCodeSource();
            if (source == null) {
                return "a jar with no code source";
            }
            String name = Path.of(source.getLocation().toURI()).getFileName().toString();
            return URLDecoder.decode(name, StandardCharsets.UTF_8)
                    + ", loaded by " + factory.getClassLoader();
        } catch (ReflectiveOperationException | java.net.URISyntaxException e) {
            throw new IllegalStateException("Failed to locate commons-logging", e);
        }
    }
}
