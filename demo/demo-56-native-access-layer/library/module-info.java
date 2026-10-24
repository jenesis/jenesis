/**
 * The library that keeps demo.strings.text in a layer of its own and passes its
 * native access on to it: no one outside the library can see that module.
 *
 * @jenesis.release 25
 * @jenesis.layer strings api demo.strings.spi
 * @jenesis.layer strings provider demo.strings.text
 * @jenesis.layer strings native demo.strings.text
 * @jenesis.pin build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.0 SHA-256/62237345fd053bdd3b0c4681f6b65d79d856eb07bb761b9cd9520626f6ba12ce
 */
module demo.strings.library {
    requires build.jenesis.launcher;
    requires demo.strings.spi;

    exports demo.strings.library;
}
