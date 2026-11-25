/**
 * The application. It grants native access to the library it runs and to
 * nothing else: what the library keeps in its layer stays out of sight.
 *
 * @jenesis.release 25
 * @jenesis.main demo.strings.app.Application
 * @jenesis.native demo.strings.library
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.5 SHA-256/f48c2fcc48025673fff614a826155ebb177ef2ca02c327eefef025b39fb0336a
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.5 SHA-256/efa781ecc8e769e31ed29039a0ca7a4891a4d8d10321114b086d3afab1183c3d
 */
module demo.strings.app {
    requires demo.strings.library;
}
