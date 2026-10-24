/**
 * A modular Java app with a {@code main} method and one real module dependency
 * ({@code org.slf4j}), shipped as a {@code bundle.zip}: the runtime launch closure
 * (the app jar plus {@code slf4j-api}) split into a {@code modulepath/}, with an
 * {@code application.properties} naming the entry point - a single, self-contained
 * input to drop onto a stock JRE base image. It is the bundle counterpart of the
 * jpackage app-image in {@code ../demo-10-java-modular-executable}.
 *
 * The {@code @jenesis.main} tag names the module's main class, which is what makes
 * the module runnable: the build records {@code main=sample.Sample} in the module's
 * {@code module.properties}, and the {@code bundle} step reads it (from the derived
 * {@code launcher.properties}) to write the bundle's {@code application.properties}.
 *
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin maven/org.slf4j/slf4j-api/pom 2.0.16 SHA-256/b1a00f5b1c4dbe62b805d65d23911a6f77063889d7cb1e86fe8389d6190473f7
 * @jenesis.pin maven/org.slf4j/slf4j-bom/pom 2.0.16 SHA-256/0566048ec825cdf28758620af64d9e14ae38a9cd8748dd6fafa6df4a4194c279
 * @jenesis.pin maven/org.slf4j/slf4j-parent/pom 2.0.16 SHA-256/09a0b4cc814d7274616c9b16d4c0fd9aaf1ecc813334de5131e547271b1982e5
 * @jenesis.pin org.slf4j 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.bundle {
    requires org.slf4j;

    exports sample;
}
