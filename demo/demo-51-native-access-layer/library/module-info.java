/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it: no one outside the library can see that module.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider demo.strings.text
 * @jenesis.layer strings native demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.5.2 SHA-256/f88e6840cdfd142876b6bbc36743597755e66430780ba97348a4a47a32e30794
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.2 SHA-256/f88e6840cdfd142876b6bbc36743597755e66430780ba97348a4a47a32e30794
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
