/**
 * A library that offers C's strlen through the foreign function API. Linking a
 * native function is a restricted method, but whether anything calls it is not
 * this library's decision, so it declares nothing.
 *
 * @jenesis.release 25
 */
module demo.natives.text {
    exports demo.natives.text;
}
