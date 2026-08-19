package build;

import module java.base;
import build.jenesis.Jpx;
import build.jenesis.SequencedProperties;

public class Demo {

    static void main(String[] args) throws Exception {
        // Install into the demo's own target/ folder instead of ~/.jenesis/jpx, so the
        // demo resolves from scratch and leaves nothing behind outside this directory.
        Jpx jpx = new Jpx(Path.of("target", "jpx"));

        // The same console launcher named twice: once by its Java module name, whose
        // Maven coordinates the module repository discovers, and once by the Maven
        // coordinate itself. Both name a version and the installation's full digest.
        launch(jpx, "org.junit.platform.console@6.1.3",
                "9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8");
        launch(jpx, "org.junit.platform:junit-platform-console@6.1.3",
                "9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8");

        // A leading fraction of that digest is enough: 32 hex characters is the
        // shortest prefix accepted, and it is matched against the recomputed digest.
        launch(jpx, "org.junit.platform.console@6.1.3", "9b60dfc3d10f0b4fdf69050eec7b7332");

        // A digest that does not match the installed jars refuses to launch, on the
        // run that installed them and on every run after it.
        reject(jpx, "org.junit.platform.console@6.1.3",
                "9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01c0ffee");
    }

    private static void launch(Jpx jpx, String target, String hash) throws Exception {
        Jpx.Installation installation = verify(jpx, target, hash);
        SequencedProperties properties = installation.properties();
        System.out.println("  [verified] " + installation.folder()
                + " as " + properties.getProperty("mainModule")
                + "/" + properties.getProperty("mainClass")
                + " over " + properties.getProperty("modulepath").split(",").length + " modules");
        int status = installation.launch(List.of("--version"));
        if (status != 0) {
            throw new IllegalStateException(target + " exited with status " + status);
        }
    }

    private static void reject(Jpx jpx, String target, String hash) throws Exception {
        try {
            verify(jpx, target, hash);
        } catch (IllegalStateException e) {
            System.out.println("  [blocked]  " + e.getMessage());
            return;
        }
        throw new AssertionError("A mismatching hash was expected to block " + target);
    }

    private static Jpx.Installation verify(Jpx jpx, String target, String hash) throws IOException {
        System.out.println();
        System.out.println("jpx --hash=" + hash + " " + target + " --version");
        return jpx.install(target).verify(hash);
    }
}
