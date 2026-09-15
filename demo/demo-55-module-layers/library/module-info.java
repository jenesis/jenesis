/**
 * The library that keeps a dependency private. It declares the layer, names the module it shares
 * with it, and asks for it by name - so its own consumers declare nothing and never learn that a
 * second jackson-core exists.
 *
 * It cannot {@code requires} what it isolates: an isolated module is off its path, and javac says
 * so. What crosses is {@code Report}, from the shared API module.
 *
 * @jenesis.release 25
 * @jenesis.layer render api demo.layers.spi
 * @jenesis.layer render module/demo.layers.impl
 * @jenesis.pin build.jenesis.launcher 1-SNAPSHOT
 * @jenesis.pin build.jenesis/build.jenesis.launcher 1-SNAPSHOT
 */
module demo.layers.library {
    requires build.jenesis.launcher;
    requires demo.layers.spi;

    exports demo.layers.library;
}
