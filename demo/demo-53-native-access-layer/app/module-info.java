/**
 * The application. It grants native access to the library it runs and to
 * nothing else: what the library keeps in its layer stays out of sight.
 *
 * @jenesis.release 25
 * @jenesis.main demo.strings.app.Application
 * @jenesis.native demo.strings.library
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.3 SHA-256/47d0bc19f02c904ee6c573f8aafe69234006dc1357525281f8f36f4d1a96f9bf
 */
module demo.strings.app {
    requires demo.strings.library;
}
