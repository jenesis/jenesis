/**
 * The application. It grants native access to the library it runs and to
 * nothing else: what the library keeps in its layer stays out of sight.
 *
 * @jenesis.release 25
 * @jenesis.main demo.strings.app.Application
 * @jenesis.native demo.strings.library
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.2 SHA-256/f88e6840cdfd142876b6bbc36743597755e66430780ba97348a4a47a32e30794
 */
module demo.strings.app {
    requires demo.strings.library;
}
