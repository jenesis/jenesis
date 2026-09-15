/**
 * The application. It requires jackson-core at one version and isolates the
 * renderer, which requires jackson-core at another, in the {@code render} layer.
 * Nothing is relocated: both copies keep the package
 * {@code com.fasterxml.jackson.core}, and the layer's own class loader is what
 * keeps them apart.
 *
 * The application never {@code requires} the renderer - it could not, because an
 * isolated module is off its module path - so it reaches it as a service. The seam
 * module is declared shared, which keeps the layer from carrying a second copy of
 * {@code Report}; without that line the build fails and names the module.
 *
 * @jenesis.release 25
 * @jenesis.main demo.layers.app.Main
 * @jenesis.layer render module/demo.layers.renderer
 * @jenesis.layer render shared demo.layers.api
 * @jenesis.pin com.fasterxml.jackson.core 2.18.2 SHA-256/d8054ae7c0d1c2d2f55d28e46026ebe5892881f3fab5f439233184381c3b4a1f
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.18.2 SHA-256/d8054ae7c0d1c2d2f55d28e46026ebe5892881f3fab5f439233184381c3b4a1f
 * @jenesis.pin render/maven/com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.pin render/module/com.fasterxml.jackson.core 2.15.4
 */
module demo.layers.app {
    requires com.fasterxml.jackson.core;
    requires demo.layers.api;

    uses demo.layers.api.Report;
}
