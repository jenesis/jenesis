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
 * @jenesis.pin build.jenesis.launcher 1-SNAPSHOT SHA-256/23735dd00c6898715a3090749c0e1a5f94bfa85b6936bc86389eee6d5b194efa
 * @jenesis.pin build.jenesis/build.jenesis.launcher 1-SNAPSHOT SHA-256/23735dd00c6898715a3090749c0e1a5f94bfa85b6936bc86389eee6d5b194efa
 * @jenesis.pin layer:render/maven/com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 */
module demo.layers.library {
    requires build.jenesis.launcher;
    requires demo.layers.spi;

    exports demo.layers.library;
}
