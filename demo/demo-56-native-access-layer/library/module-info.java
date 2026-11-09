/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it: no one outside the library can see that module.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider demo.strings.text
 * @jenesis.layer strings native demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.6.5
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.5
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.5
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
