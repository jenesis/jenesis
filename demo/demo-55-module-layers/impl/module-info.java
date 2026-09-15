/**
 * The provider, isolated in the layer with the jackson-core it pins. It requires the API module,
 * which resolves from the library rather than from the layer, so the {@code Report} it provides is
 * the very class the library looks up.
 *
 * @jenesis.release 25
 * @jenesis.pin com.fasterxml.jackson.core 2.15.4
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.15.4
 */
module demo.layers.impl {
    requires com.fasterxml.jackson.core;
    requires demo.layers.spi;

    provides demo.layers.spi.Report with demo.layers.impl.JacksonReport;
}
