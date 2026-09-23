/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it. It names itself, because the layer's grant is made on
 * its behalf, and the module in its layer, which no one outside it can see.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider module/demo.strings.text
 * @jenesis.native demo.strings.library layer:strings/module/demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.5.1 SHA-256/b9db437eccaeaef7ee04a6a00a8e330bc69542614012fa6485079f41e3c9edfb
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.1 SHA-256/b9db437eccaeaef7ee04a6a00a8e330bc69542614012fa6485079f41e3c9edfb
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
