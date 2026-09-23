/**
 * The provider, isolated in the library's layer. It measures a string with C's
 * strlen through the foreign function API, which is a restricted method.
 *
 * @jenesis.release 25
 */
module demo.strings.text {
    requires demo.strings.spi;

    provides demo.strings.spi.Length with demo.strings.text.NativeLength;
}
