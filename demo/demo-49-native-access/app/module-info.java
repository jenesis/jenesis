/**
 * The application. It runs demo.natives.text and therefore decides whether that
 * library may use native code: @jenesis.native demo.natives.text grants it, and
 * nothing else in this module's run is granted anything.
 *
 * @jenesis.release 25
 * @jenesis.main demo.natives.app.Application
 * @jenesis.native demo.natives.text
 */
module demo.natives.app {
    requires demo.natives.text;
}
