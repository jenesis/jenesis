/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it: no one outside the library can see that module.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider demo.strings.text
 * @jenesis.layer strings native demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.5.3 SHA-256/47d0bc19f02c904ee6c573f8aafe69234006dc1357525281f8f36f4d1a96f9bf
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.3 SHA-256/47d0bc19f02c904ee6c573f8aafe69234006dc1357525281f8f36f4d1a96f9bf
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
