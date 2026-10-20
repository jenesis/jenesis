Generated sources demo (data formats)
=====================================

Three modules, three wire formats, no build script: each module switches on the
generator of its format with that generator's configuration file, binds its schema
there, and the classes the generator writes are compiled into the module like any
hand-written package.

Run it
------

    java build/jenesis/Make.java

| Module     | Config file                          | Input                    | Generator | Generated |
| ---------- | ------------------------------------ | ------------------------ | --------- | --------- |
| `xml`      | `xml/build.jenesis/xjc.properties`       | `META-INF/build.jenesis/order.xsd`     | JAXB `xjc` | `demo.order` |
| `protobuf` | `protobuf/build.jenesis/protoc.properties` | `META-INF/build.jenesis/greeting.proto` | `protoc` + the gRPC plugin | `demo.greeting` |
| `avro`     | `avro/build.jenesis/avro.properties`     | `META-INF/build.jenesis/user.avsc`     | `avro-tools` | `demo.user` |

Each module also has one hand-written class that uses the generated types -
`Orders`, `Greetings`, `Users` - so a build that compiles at all proves the
generated sources took part in it.

Each generator is a plugin at the hook point `binary/generated`, whose sources the
module compiles with its own:

    xjc+binary/generated=build.jenesis.plugin.xjc
    protoc+binary/generated=build.jenesis.plugin.protoc
    avro+binary/generated=build.jenesis.plugin.avro

A plugin runs in a module where its `<name>.properties` is found, so each
module turns on its own generator and no other. `avro.properties` here only
binds the schemas: that is enough to compile every `.avsc` and `.avpr` they hold.
`xjc.properties` also names a target package, and `protoc.properties`
a protoc plugin.

Where the inputs live
---------------------

Each module keeps its schema in `META-INF/build.jenesis/` under its sources and binds
that folder into the folder the generator reads, relative to the module:

    @schemas/avro=sources/META-INF/build.jenesis

That is the build's own folder, and the compiler never copies it into the artifact, so a schema
is an input to the build and nothing more - none of these three jars carries the
`.xsd`, the `.proto` or the `.avsc` it was built from, nor an empty directory where
they sat:

    jar tf .../build.jenesis.demo~data-formats-xml~1.0.0.jar
    demo/order/Item.class
    demo/order/ObjectFactory.class
    demo/order/Order.class
    demo/order/package-info.class
    demo/format/xml/Orders.class
    META-INF/MANIFEST.MF

An input that *should* ship - a schema you want to publish, a `.proto` other projects
compile against - lives wherever you want it, and the binding names that folder
instead.

Per-module configuration
------------------------

A Maven module reads `<module>/build.jenesis/` and `<module>/src/main/build.jenesis/`
on top of the project-wide configuration folder, so each module here turns on its
own generator and no other.

Two dependency closures, kept apart
-----------------------------------

Every generator is a *build* tool: it resolves in a dependency group named after
it (`xjc`, `protoc`, `avro`) and is pinned there. What the generated classes
*import* at run time is a dependency of the artifact you ship, so each module
declares it in its own `pom.xml`:

| Module     | Tool group                             | Declared dependency |
| ---------- | -------------------------------------- | ------------------- |
| `xml`      | `xjc` -> `org.glassfish.jaxb:jaxb-xjc`      | `jakarta.xml.bind:jakarta.xml.bind-api` |
| `protobuf` | `protoc` -> `com.google.protobuf:protoc`, `protoc:grpc-java` -> `io.grpc:protoc-gen-grpc-java` | `com.google.protobuf:protobuf-java`, `io.grpc:grpc-protobuf`, `io.grpc:grpc-stub` |
| `avro`     | `avro` -> `org.apache.avro:avro-tools`      | `org.apache.avro:avro` |

`pin` writes both closures, each under its own group, so the tool and the runtime
API are versioned independently:

    java build/jenesis/Make.java pin

A native tool, resolved per platform
------------------------------------

`protoc` is not a jar. Google publishes it as a native executable per operating
system and chipset under a Maven classifier, and so is the gRPC plugin. The build
derives that classifier from the host it runs on, copies the resolved binary into
the step's scratch folder, marks it executable and forks it. The coordinate that
resolves therefore differs per machine, and each machine needs its own checksum
pin - `protobuf/pom.xml` carries one line per platform, each guarded with the
platform it is for so `pin` keeps the ones it cannot resolve itself:

    <!--jenesis.pin
    protoc:tool/maven/com.google.protobuf/protoc/exe/linux-x86_64  4.32.1 SHA-256/9a757b... (linux,x86_64)
    protoc:tool/maven/com.google.protobuf/protoc/exe/osx-aarch_64  4.32.1 SHA-256/e3b836... (macos,aarch64)
    protoc:grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/linux-x86_64 1.83.1 SHA-256/db4044... (linux,x86_64)
    -->

`pin` refreshes only the lines of the platform it runs on. To add another platform
from one machine, write its line with the SHA-256 of the executable Maven Central
publishes for that classifier:

    curl -s https://repo1.maven.org/maven2/com/google/protobuf/protoc/4.32.1/protoc-4.32.1-osx-aarch_64.exe | sha256sum

Pin `protoc` rather than floating it: the generated code has to match the
`protobuf-java` the module depends on. An unpinned `protoc` takes the newest release
without a pre-release qualifier, passing over the `21.0-rc-1` that Maven Central
reports as the latest release.

A protoc plugin is a second native executable, named and resolved the same way:

    plugins=grpc-java=io.grpc/protoc-gen-grpc-java

which resolves in its own `protoc:grpc-java` group and reaches protoc as
`--plugin=protoc-gen-grpc-java=<path> --grpc-java_out=<dir>`, so `GreeterGrpc`
lands next to `GreetingProto`.

Importing a well-known type
---------------------------

`greeting.proto` imports `google/protobuf/timestamp.proto`. The `protoc` executable
from Maven Central ships without the well-known types, but `protobuf-java`, which the
module depends on for the generated code anyway, carries them, and every `.proto` in a
compile dependency is on protoc's include path. An import of a well-known type, or of
a `.proto` another library publishes, resolves without copying the file into the
project.

Where generation runs in the build
----------------------------------

Generation is the `generator` stage of the Java toolchain, immediately before the
compiler chain:

    generated/<tool>/generate -> compiled/javac -> classes -> artifacts/jar

Everything downstream of the generators sees the generated sources, so tests and
`javadoc` cover them, and the sources jar carries them beside the schema they
follow from, as Maven's does. Everything *upstream* does not: the inferred linters
and formatters (`check`, `format`) read the module's own sources only, so
generated code is never linted or reformatted.

Layout
------

    demo-17-data-formats
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- pom.xml                  parent, three modules
    |-- xml
    |   |-- build.jenesis/xjc.properties
    |   |-- pom.xml
    |   `-- sources
    |       |-- META-INF/build.jenesis/order.xsd     the input, which does not ship
    |       `-- demo/format/xml/Orders.java
    |-- protobuf
    |   |-- build.jenesis/protoc.properties
    |   |-- pom.xml              plus a protoc and gRPC plugin pin per platform
    |   `-- sources
    |       |-- META-INF/build.jenesis/greeting.proto
    |       `-- demo/format/protobuf/Greetings.java
    `-- avro
        |-- build.jenesis/avro.properties  binds the schemas alone
        |-- pom.xml
        `-- sources
            |-- META-INF/build.jenesis/user.avsc
            `-- demo/format/avro/Users.java
