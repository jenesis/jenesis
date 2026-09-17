Code signing demo
=================

Sign the produced jar with `jarsigner`, the JDK's own signer, so that the jar
carries a signature block a JVM can check before it runs the code. This is the
signature *inside* the archive, not the detached OpenPGP or Sigstore signature
that `openpgp` and `sigstore` verify on the way in - those answer "who published
these bytes", this one answers "who vouches for this jar" to whoever opens it.

Run it
------

From this directory:

    java build/Demo.java

    Generated a throwaway key store in target/keystore/demo.p12
    ...
    Staged target/stage/maven/output/demo/signing/demo.signing/1-SNAPSHOT/demo.signing-1-SNAPSHOT.jar
      carries META-INF/DEMO.SF
      carries META-INF/DEMO.RSA

    jar verified.

`build/Demo.java` first generates a throwaway RSA key with `keytool` under
`target/`, because no repository should carry a signing key. Then it names that key
to the build, builds and stages, and checks that the jar the staging laid out is the
signed one.

How signing is wired
--------------------

Signing is named by properties, never by a file of its own:

    jenesis.jarsigner.keystore     the key store to sign with
    jenesis.jarsigner.alias        the key within that store
    jenesis.jarsigner.storepass    where the store's password is read from
    jenesis.jarsigner.keypass      the same, where the key has a password of its own
    jenesis.jarsigner.storetype    the JDK's own default otherwise, normally PKCS12
    jenesis.jarsigner.tsa          a timestamp authority, so a signature outlives the certificate
    jenesis.jarsigner.arguments    anything else jarsigner accepts

Naming any of them says the project signs its jar. Saying that and then leaving
the key store, the alias or the password location unnamed fails the build - a
release that shipped unsigned because a runner forgot a flag is the one outcome
worth refusing.

They belong to the machine that signs, never to the project: a `jenesis.properties`
that named them would decide which key a build reaches for and which file a password
is read from, so a project file that sets any of them fails the build. The machine
names them on the command line, or your own `~/.jenesis/jenesis.properties` does:

    java -Djenesis.jarsigner.keystore=/etc/jenesis/release.p12 \
         -Djenesis.jarsigner.alias=release \
         -Djenesis.jarsigner.storepass=env JENESIS_KEYSTORE_PASSWORD \
         build/jenesis/Make.java

This demo is that machine. It generates a throwaway key into `target/` and names
all three itself, in `build/Demo.java`, before it builds:

    System.setProperty("jenesis.jarsigner.keystore", KEYSTORE.toString());
    System.setProperty("jenesis.jarsigner.alias", "demo");
    System.setProperty("jenesis.jarsigner.storepass", "file " + PASSWORD);

It names no `storetype` because the JDK's own default is already `pkcs12`; a JKS
store would need the line.

Nothing else changes: the build is the ordinary inferred one, and `build/Demo.java`
only generates the key before handing over to it exactly as `Make.java` would:

    Project.perform(Path.of("."), Make.loadProperties(Path.of(".")), "stage");

A developer's own key belongs in the user-global `~/.jenesis/jenesis.properties`,
which every project on that machine reads and none of them commits:

    # ~/.jenesis/jenesis.properties
    jenesis.jarsigner.keystore=/home/me/.keys/signing.p12
    jenesis.jarsigner.alias=me
    jenesis.jarsigner.storepass=file /home/me/.keys/signing.pass

A release runner names the same three on the command line, where a `-D` wins over
that file. A project's own `jenesis.properties` or profile names none of them: a
file that travels with the sources would decide which key a build reaches for and
which file a password is read from, so setting any `jenesis.jarsigner.*` there
fails the build. A build that names nothing at all does not sign.

Passwords are never values
--------------------------

`storepass` and `keypass` take a location rather than a secret, in `jarsigner`'s
own grammar:

    jenesis.jarsigner.storepass=env JENESIS_KEYSTORE_PASSWORD
    jenesis.jarsigner.storepass=file /run/secrets/keystore.pass

Anything else fails the build naming the two forms. That holds even though these
are properties now: a password passed as `-D` would stand in the process list of
every machine that runs the build. The key store itself is referenced by path and
never copied into `target/`, so no private key reaches the build tree or a shared
build cache.

Where the step sits
-------------------

Signing is part of producing the artifact, not a check after it: the signer
reads the jar the archiver wrote and writes the signed jar in its place, so the
unsigned jar never leaves the toolchain. Everything downstream - the module's
inventory, the staged Maven and modular repositories, an `export`, a
publication - sees only the signed jar.

That also means the signature is applied before a detached OpenPGP or Sigstore
signature would be, which is the order you want: the detached signature then
covers the signed bytes.

A note on caching
-----------------

The step re-runs when the jar changes or when any of the settings above change.
It does not hash the key store's contents, so replacing the key behind an
unchanged path does not by itself invalidate the step - delete `target/` (or pass
`-Djenesis.executor.rebuild=true`) after a key rotation.
