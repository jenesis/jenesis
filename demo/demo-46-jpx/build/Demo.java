package build;

import module java.base;
import build.jenesis.HashDigestFunction;
import build.jenesis.Jpx;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;

public class Demo {

    static void main(String[] args) throws Exception {
        // The wiring the jpx command carries, named out in full because this demo installs
        // into its own target/ folder instead of ~/.jenesis/jpx: module names are looked up
        // in the Jenesis module repository and resolved through their published POM, Maven
        // coordinates come from Maven Central, and every jar is placed as it describes a
        // module. So the demo resolves from scratch and leaves nothing behind outside this
        // directory.
        Repository modules = JenesisModuleRepository.of(JenesisRepository.Scope.ARTIFACT);
        MavenPomResolver maven = new MavenPomResolver();
        Jpx jpx = new Jpx(Path.of("target", "jpx"),
                Map.of("maven", MavenDefaultRepository.of(), "module", modules),
                Map.<String, Resolver>of("maven", maven, "module", new MavenModuleResolver("maven", maven, modules)),
                new HashDigestFunction("SHA-256"),
                PathPlacement.INFERRED);

        // The same console launcher named twice: once by its Java module name, whose
        // Maven coordinates the module repository discovers, and once by the Maven
        // coordinate itself. Both name a version and the installation's full digest.
        // The name also decides how the program runs: as a module for the module name,
        // on the class path for the coordinate.
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
        // A module name is launched as a module, over a module path; a coordinate names an
        // artifact rather than a module, and is launched from the class path in full.
        String mainModule = properties.getProperty("mainModule");
        String modulepath = properties.getProperty("modulepath");
        System.out.println("  [verified] " + installation.folder()
                + " as " + (mainModule == null ? "" : mainModule + "/") + properties.getProperty("mainClass")
                + (modulepath == null
                ? " over " + properties.getProperty("classpath").split(",").length + " jars on the class path"
                : " over " + modulepath.split(",").length + " modules"));
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
