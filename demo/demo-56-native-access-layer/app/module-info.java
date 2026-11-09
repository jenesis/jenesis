/**
 * The application. It grants native access to the library it runs and to
 * nothing else: what the library keeps in its layer stays out of sight.
 *
 * @jenesis.release 25
 * @jenesis.main demo.strings.app.Application
 * @jenesis.native demo.strings.library
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.5
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.5
 */
module demo.strings.app {
    requires demo.strings.library;
}
