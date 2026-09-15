/**
 * The seam. This module is the only thing that crosses the boundary between the
 * application and the isolated layer, so it is declared shared: the layer resolves
 * it from its parent rather than carrying a second copy, and both sides therefore
 * work with the very same {@code Report} class.
 *
 * @jenesis.release 25
 */
module demo.layers.api {
    exports demo.layers.api;
}
