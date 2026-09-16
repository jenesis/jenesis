/**
 * The API module: the one module the library and its layer share. It reaches nothing but the JDK,
 * so nothing of the legacy tree behind it is exposed by it.
 *
 * @jenesis.release 25
 */
module demo.legacy.spi {
    exports demo.legacy.spi;
}
