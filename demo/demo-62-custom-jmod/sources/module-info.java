/**
 * A modular sample whose build is customized to package extra, non-class content
 * into a `.jmod`, link it into a runtime image with `jlink`, and wrap that into a
 * self-contained application image with `jpackage`.
 *
 * The module itself is ordinary; everything interesting is in `build/Demo.java`,
 * which wraps the stock assembler to add a config file to the module's `.jmod`.
 * Its `main` reads that config back from the runtime's `conf/` at run time, and
 * logs through its one module dependency, `org.slf4j`. That dependency shows the
 * other half of the picture: `jlink` links it into the runtime as a plain jar,
 * alongside this module's content-bearing `.jmod`, and `jpackage` carries the
 * whole linked runtime into the image - so the launched app finds both.
 *
 * @jenesis.main sample.Sample
 * @jenesis.release 25
 * @jenesis.pin maven/org.slf4j/slf4j-api/pom 2.0.16 SHA-256/b1a00f5b1c4dbe62b805d65d23911a6f77063889d7cb1e86fe8389d6190473f7
 * @jenesis.pin maven/org.slf4j/slf4j-bom/pom 2.0.16 SHA-256/0566048ec825cdf28758620af64d9e14ae38a9cd8748dd6fafa6df4a4194c279
 * @jenesis.pin maven/org.slf4j/slf4j-parent/pom 2.0.16 SHA-256/09a0b4cc814d7274616c9b16d4c0fd9aaf1ecc813334de5131e547271b1982e5
 * @jenesis.pin org.slf4j 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.config {
    requires org.slf4j;

    exports sample;
}
