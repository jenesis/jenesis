/**
 * The provider, isolated in the layer with the jackson-core it pins. It requires the API module,
 * which resolves from the library rather than from the layer, so the {@code Report} it provides is
 * the very class the library looks up.
 *
 * It also declares a layer of its own, to show that nesting is unbounded: a module isolated in one
 * layer may isolate a dependency of its own in another, and reaches it exactly as the library
 * reaches this one.
 *
 * @jenesis.release 25
 * @jenesis.layer inner api demo.layers.spi
 * @jenesis.layer inner module/demo.layers.nested
 * @jenesis.pin build.jenesis.launcher 1-SNAPSHOT SHA-256/27d05acf94d01192094582fe2128484e5245b6f3bc2e5ec933d4c9328f6a1b2e
 * @jenesis.pin build.jenesis/build.jenesis.launcher 1-SNAPSHOT SHA-256/27d05acf94d01192094582fe2128484e5245b6f3bc2e5ec933d4c9328f6a1b2e
 * @jenesis.pin com.fasterxml.jackson.core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson.core/jackson-core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 */
module demo.layers.impl {
    requires build.jenesis.launcher;
    requires com.fasterxml.jackson.core;
    requires demo.layers.spi;

    provides demo.layers.spi.Report with demo.layers.impl.JacksonReport;
}
