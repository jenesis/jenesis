/**
 * The application. It grants native access to the library it runs and to
 * nothing else: what the library keeps in its layer stays out of sight.
 *
 * @jenesis.release 25
 * @jenesis.main demo.strings.app.Application
 * @jenesis.native demo.strings.library
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.0 SHA-256/a130e105cbb1b04df47249edb65c6b4f03139cae0de174a02b2c218a3b9a3d47
 */
module demo.strings.app {
    requires demo.strings.library;
}
