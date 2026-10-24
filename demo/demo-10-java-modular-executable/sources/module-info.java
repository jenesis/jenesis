/**
 * A modular Java sample with an entry point and one real module dependency. As
 * with {@code ../java-modular}, its only build descriptor is this
 * {@code module-info.java} (no {@code pom.xml}), so Jenesis auto-detects the
 * MODULAR_TO_MAVEN layout and resolves the declared module through the Jenesis
 * module repository.
 *
 * The {@code @jenesis.main} tag names the module's main class. It is what lets
 * the {@code package} step (and the {@code Execute} launcher) treat this module
 * as runnable: the build records {@code main=sample.Sample} in the module's
 * {@code module.properties}, and when packaging is requested jpackage turns the
 * produced jar plus its runtime dependencies (here {@code org.slf4j}) into a
 * self-contained application image.
 *
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin launcher/maven/build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 * @jenesis.pin launcher/maven/build.jenesis/build.jenesis.launcher/pom 0.6.0 SHA-256/62237345fd053bdd3b0c4681f6b65d79d856eb07bb761b9cd9520626f6ba12ce
 * @jenesis.pin maven/org.slf4j/slf4j-api/pom 2.0.16 SHA-256/b1a00f5b1c4dbe62b805d65d23911a6f77063889d7cb1e86fe8389d6190473f7
 * @jenesis.pin maven/org.slf4j/slf4j-bom/pom 2.0.16 SHA-256/0566048ec825cdf28758620af64d9e14ae38a9cd8748dd6fafa6df4a4194c279
 * @jenesis.pin maven/org.slf4j/slf4j-parent/pom 2.0.16 SHA-256/09a0b4cc814d7274616c9b16d4c0fd9aaf1ecc813334de5131e547271b1982e5
 * @jenesis.pin org.slf4j 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.modular.executable {
    requires org.slf4j;

    exports sample;
}
