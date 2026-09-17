/**
 * A module with nothing to resolve, built to show that its jar comes out byte for
 * byte the same wherever it is built. It declares no release, so it compiles for the
 * release of the JDK running the build, which keeps the update of that JDK out of
 * the compiled module declaration.
 */
module demo.reproducible {
    exports demo.reproducible;
}
