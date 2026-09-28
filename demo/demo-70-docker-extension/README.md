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

`build.jenesis/packaging.properties` names the image to build on and what that image holds:

    docker=sample
    docker.diff=org.slf4j

`docker` is the image the generated `Dockerfile` starts `FROM`, as in any other project. With
`docker.diff`, the image extends it rather than launching an application of its own.

`docker.diff` names what the base image holds, as a comma-separated list of module names or
`<groupId>:<artifactId>[:<version>]` coordinates. The build resolves them, with everything they
require, in a dependency group of their own, `docker`. Whatever module of this project's closure is
among them is left out of the image, in whatever version this project would have resolved. For an
image generated from an application, naming the application is enough; `sample` is not published,
so this demo names `org.slf4j`, which is the part of it that `org.slf4j.simple` requires.

`pin` records the `docker` group like any other, so the modules that are left out are checked by
checksum too:

    @jenesis.pin docker/module/org.slf4j 2.0.19 SHA-256/...

Run it
------

    java build/jenesis/Make.java stage

    target/stage/docker/output/module-sources/
    |-- Dockerfile
    `-- extensions/                    this module and org.slf4j.simple, without org.slf4j

The `Dockerfile` adds the jars to the folder `sample` keeps for extensions, and nothing else:

    FROM sample
    COPY extensions/ /app/extensions/

Build and run it:

    docker build -t sample-logging target/stage/docker/output/module-sources
    docker run --rm sample-logging Ada

    [main] INFO sample.Sample - greeting Ada
    Hello, Ada, from a packaged Java module built by Jenesis!

The backend is bound because `org.slf4j` uses the service it provides, and the `slf4j-api` it runs
against is the one `sample` shipped.

Options for java
----------------

The image changes nothing about how `sample` is launched. An option for `java` - a system property,
a module to resolve that provides no service, an agent - goes into the `JDK_JAVA_OPTIONS` variable,
which `java` reads ahead of the `ENTRYPOINT`'s command line. Set it when the container starts:

    docker run --rm -e JDK_JAVA_OPTIONS=-Dorg.slf4j.simpleLogger.showThreadName=false sample-logging Ada

    NOTE: Picked up JDK_JAVA_OPTIONS: -Dorg.slf4j.simpleLogger.showThreadName=false
    INFO sample.Sample - greeting Ada
    ...

or in an image of your own, extending the variable rather than replacing it, so that the options of
every image below it remain:

    FROM sample-logging
    ENV JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS} -Dorg.slf4j.simpleLogger.showThreadName=false"

Stacking images
---------------

An image built on `sample-logging` names that image and everything below it:

    docker=sample-logging
    docker.diff=org.slf4j,org.slf4j.simple

In a real project, `docker.diff` names the published application and every extension below, and
each extension's own dependencies are filtered out with it.

What the build refuses
----------------------

- `docker.diff` without `docker`, which names the image to extend;
- a `docker.diff` entry that is neither a module name nor a Maven coordinate;
- an extending image that adds nothing, because every module is already in the base image;
- an extending image with a module layer, an agent or a jar granted native access, since each is an
  option of the launch - name the agent or `--enable-native-access` in `JDK_JAVA_OPTIONS` instead.
