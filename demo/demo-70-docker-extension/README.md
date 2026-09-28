Docker extension demo
=====================

An image that another image is built on. `../demo-09-java-modular-executable` generates an image
for an application, `sample`, which logs through slf4j but ships no logging backend. This project
builds the image that adds one: `FROM sample`, with nothing in it that `sample` holds already.

Build the base image first, from `../demo-09-java-modular-executable`:

    java -Djenesis.make.profiles=docker build/jenesis/Make.java stage
    docker build -t sample target/stage/docker/output/module-sources

What to declare
---------------

The project is one module that requires the backend, and nothing else:

    module demo.docker.extension {
        requires org.slf4j.simple;
    }

`build.jenesis/packaging.properties` names the image to build on, what that image holds, and an
option for `java`:

    docker=sample
    docker.diff=org.slf4j
    docker.options=-Dorg.slf4j.simpleLogger.showThreadName=false

`docker` is the image the generated `Dockerfile` starts `FROM`, as in any other project. With
`docker.diff`, the image extends it rather than launching an application of its own:

- `docker.diff` names what the base image holds, as a comma-separated list of module names or
  `<groupId>:<artifactId>[:<version>]` coordinates. The build resolves them, with everything they
  require, in a dependency group of their own, `docker`. Whatever module of this project's closure is
  among them is left out of the image, in whatever version this project would have resolved. For an
  image generated from an application, naming the application is enough; `sample` is not published,
  so this demo names `org.slf4j`, which is the part of it that `org.slf4j.simple` requires.
- `docker.options` is options for `java`, separated by whitespace. They are written to an argument
  file of the image's own.

Both keys work without `docker.diff` as well: there, `docker.options` is written into the launch of
the application itself.

`pin` records the `docker` group like any other, so the modules that are left out are checked by
checksum too:

    @jenesis.pin docker/module/org.slf4j 2.0.19 SHA-256/...

Run it
------

    java build/jenesis/Make.java stage

    target/stage/docker/output/module-sources/
    |-- Dockerfile
    |-- arguments/<digest>.args        the options, as a Java argument file
    `-- extensions/                    this module and org.slf4j.simple, without org.slf4j

The `Dockerfile` adds the jars to the folder `sample` keeps for extensions, and the argument file to
the `java` command line:

    FROM sample
    COPY extensions/ /app/extensions/
    COPY arguments/ /app/arguments/
    ENV JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS} @/app/arguments/<digest>.args"

Build and run it:

    docker build -t sample-logging target/stage/docker/output/module-sources
    docker run --rm sample-logging Ada

    NOTE: Picked up JDK_JAVA_OPTIONS:  @/app/arguments/<digest>.args
    INFO sample.Sample - greeting Ada
    Hello, Ada, from a packaged Java module built by Jenesis!

The backend is bound because `org.slf4j` uses the service it provides, the `slf4j-api` it runs
against is the one `sample` shipped, and the option removed the thread name from the log line.
`java` prints the `NOTE` line whenever `JDK_JAVA_OPTIONS` is set.

Stacking images
---------------

An argument file cannot name another argument file, and an `ENTRYPOINT` can only be replaced, so an
image that named its argument file there would drop those of every image below it. `java` reads
`JDK_JAVA_OPTIONS` before its command line and expands argument files named in it, so each image
appends its own file to what its base set, and the `ENTRYPOINT` of the application stays as it was.
The file is named after the digest of its contents, so two images never overwrite each other's
options.

An image built on `sample-logging` names that image and everything below it:

    docker=sample-logging
    docker.diff=org.slf4j,org.slf4j.simple

In a real project, `docker.diff` names the published application and every extension below, and
each extension's own dependencies are filtered out with it.

What the build refuses
----------------------

- `docker.diff` or `docker.options` without `docker`, which names the image to build from;
- a `docker.diff` entry that is neither a module name nor a Maven coordinate;
- an extending image that adds nothing: every module is already in the base image, and there are no
  options;
- an extending image with a module layer, or with a jar granted native access, since both are
  options of the application's own launch - name `--enable-native-access` in `docker.options`
  instead.
