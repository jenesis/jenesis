/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it. It names itself, because the layer's grant is made on
 * its behalf, and the module in its layer, which no one outside it can see.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider module/demo.strings.text
 * @jenesis.native demo.strings.library layer:strings/module/demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.5.0 SHA-256/a130e105cbb1b04df47249edb65c6b4f03139cae0de174a02b2c218a3b9a3d47
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.0 SHA-256/a130e105cbb1b04df47249edb65c6b4f03139cae0de174a02b2c218a3b9a3d47
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
