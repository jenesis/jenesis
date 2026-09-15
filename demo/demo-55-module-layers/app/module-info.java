/**
 * The consumer. It declares no layer, names no API module, and requires a different jackson-core
 * from the one the library keeps to itself - which is the whole point: it does not have to know.
 *
 * @jenesis.release 25
 * @jenesis.main demo.layers.app.Main
 * @jenesis.pin build.jenesis/build.jenesis.launcher 1-SNAPSHOT SHA-256/23735dd00c6898715a3090749c0e1a5f94bfa85b6936bc86389eee6d5b194efa
 * @jenesis.pin com.fasterxml.jackson.core 2.18.2 SHA-256/d8054ae7c0d1c2d2f55d28e46026ebe5892881f3fab5f439233184381c3b4a1f
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.18.2 SHA-256/d8054ae7c0d1c2d2f55d28e46026ebe5892881f3fab5f439233184381c3b4a1f
 * @jenesis.pin layer:render/maven/com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 */
module demo.layers.app {
    requires com.fasterxml.jackson.core;
    requires demo.layers.library;
}
