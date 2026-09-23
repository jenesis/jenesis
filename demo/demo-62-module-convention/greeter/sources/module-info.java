/**
 * A library published to a Maven repository by Jenesis.
 *
 * Its module name decides its Maven coordinate: the group is the first two
 * dotted segments, the artifact is the full module name, so this module is
 * published as {@code demo.convention:demo.convention.greeter}.
 *
 * @jenesis.release 25
 */
module demo.convention.greeter {
    exports sample.greeter;
}
