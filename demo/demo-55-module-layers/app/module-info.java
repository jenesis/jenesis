/**
 * The consumer. It declares no layer, names no API module, and requires a different jackson-core
 * from the one the library keeps to itself - which is the whole point: it does not have to know.
 *
 * @jenesis.release 25
 * @jenesis.main demo.layers.app.Main
 * @jenesis.pin com.fasterxml.jackson.core 2.18.2
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.18.2
 */
module demo.layers.app {
    requires com.fasterxml.jackson.core;
    requires demo.layers.library;
}
