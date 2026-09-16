/**
 * The API module: the one module the library and its layer share. It is loaded by the library and
 * read by the layer, so a {@code Report} instance crosses the boundary as an ordinary interface
 * call. Everything it reaches is shared too, which is why it reaches nothing but the JDK here.
 *
 * @jenesis.release 25
 */
module demo.layers.spi {
    exports demo.layers.spi;
}
