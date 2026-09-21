/**
 * The library that keeps a dependency private. It declares the layer, names the module it shares
 * with it, and asks for it by name - so its own consumers declare nothing and never learn that a
 * second jackson-core exists.
 *
 * It cannot {@code requires} what it isolates: an isolated module is off its path, and javac says
 * so. What crosses is {@code Report}, from the shared API module.
 *
 * @jenesis.release 25
 * @jenesis.pin build.jenesis.launcher 0.4.0 SHA-256/e56603ebcb99e54225d6124b9c7d11d95b842028511dbd0054682ce653fb4a8a
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.4.0 SHA-256/e56603ebcb99e54225d6124b9c7d11d95b842028511dbd0054682ce653fb4a8a
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson.core/jackson-core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 * @jenesis.pin layer:render/maven/build.jenesis/build.jenesis.launcher 0.4.0 SHA-256/e56603ebcb99e54225d6124b9c7d11d95b842028511dbd0054682ce653fb4a8a
 * @jenesis.pin layer:render/maven/com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.layer render api demo.layers.spi
 * @jenesis.layer render provider module/demo.layers.impl
 */
module demo.layers.library {
    requires build.jenesis.launcher;
    requires demo.layers.spi;

    exports demo.layers.library;
}
