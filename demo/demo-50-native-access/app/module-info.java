/**
 * The application. It runs demo.natives.words, which names demo.natives.text as
 * needing native access, and grants it here: a grant is made by the module that
 * runs, never inherited from a dependency.
 *
 * @jenesis.release 25
 * @jenesis.main demo.natives.app.Application
 * @jenesis.native demo.natives.text
 */
module demo.natives.app {
    requires demo.natives.words;
}
