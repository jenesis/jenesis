/**
 * The adapter that lives inside the layer. It requires the old jackson-core and
 * provides the seam's service, so it is the only code in the layer the application
 * ever reaches - and it reaches it as a {@code Report}, a plain interface call,
 * because the seam module is shared rather than duplicated.
 *
 * It is compiled against the jackson-core it pins here. The application pins a
 * different one, which is the whole point: {@code pin/divergence} reports the two
 * versions, and the layer is what lets them coexist.
 *
 * @jenesis.release 25
 * @jenesis.pin com.fasterxml.jackson.core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 */
module demo.layers.renderer {
    requires demo.layers.api;
    requires com.fasterxml.jackson.core;

    provides demo.layers.api.Report with demo.layers.renderer.JacksonReport;
}
