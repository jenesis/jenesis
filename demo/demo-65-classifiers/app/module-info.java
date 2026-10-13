/**
 * An application that runs on the variant of its library for one platform.
 *
 * @jenesis.release 25
 * @jenesis.main sample.app.App
 * @jenesis.pin demo.natives :platform-a
 */
module demo.app {
    requires demo.natives;
}
