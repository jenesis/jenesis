/**
 * A consumer that requires the published library by its module name alone.
 *
 * The build wires a {@code MavenModuleRepository} as its module repository,
 * which maps {@code demo.convention.greeter} onto
 * {@code demo.convention:demo.convention.greeter} in the Maven repository the
 * library was published to.
 *
 * @jenesis.release 25
 * @jenesis.main sample.app.App
 * @jenesis.bom pin-greeter.properties
 */
module demo.convention.app {
    requires demo.convention.greeter;
}
