/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it: no one outside the library can see that module.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider demo.strings.text
 * @jenesis.layer strings native demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.6.5 SHA-256/f48c2fcc48025673fff614a826155ebb177ef2ca02c327eefef025b39fb0336a
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.5 SHA-256/f48c2fcc48025673fff614a826155ebb177ef2ca02c327eefef025b39fb0336a
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.5 SHA-256/efa781ecc8e769e31ed29039a0ca7a4891a4d8d10321114b086d3afab1183c3d
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
