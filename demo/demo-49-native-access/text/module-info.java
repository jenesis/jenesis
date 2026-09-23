/**
 * A library that calls C's strlen through the foreign function API. Linking a
 * native function is a restricted method, so this module needs native access;
 * the bare @jenesis.native tag records that need in its jar's manifest. It
 * grants nothing: only the module that runs this library can.
 *
 * @jenesis.release 25
 * @jenesis.native
 */
module demo.natives.text {
    exports demo.natives.text;
}
