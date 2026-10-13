/**
 * The application. It grants native access to the library it runs and to
 * nothing else: what the library keeps in its layer stays out of sight.
 *
 * @jenesis.release 25
 * @jenesis.main demo.strings.app.Application
 * @jenesis.native demo.strings.library
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 */
module demo.strings.app {
    requires demo.strings.library;
}
