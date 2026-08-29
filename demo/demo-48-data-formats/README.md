Generated sources demo (data formats)
=====================================

Three modules, three wire formats, no build script and no plugin: each module
declares its schema and a one-line configuration file, and the classes the
generator writes are compiled into that module like any hand-written package.

Run it
------

    java build/jenesis/Project.java

| Module     | Config file                          | Input                    | Generator | Generated |
| ---------- | ------------------------------------ | ------------------------ | --------- | --------- |
| `xml`      | `xml/build.jenesis/xjc.properties`       | `META-INF/build.jenesis/order.xsd`     | JAXB `xjc` | `demo.order` |
| `protobuf` | `protobuf/build.jenesis/protoc.properties` | `META-INF/build.jenesis/greeting.proto` | `protoc` + the gRPC plugin | `demo.greeting` |
| `avro`     | `avro/build.jenesis/avro.properties`     | `META-INF/build.jenesis/user.avsc`     | `avro-tools` | `demo.user` |

Each module also has one hand-written class that uses the generated types -
`Orders`, `Greetings`, `Users` - so a build that compiles at all proves the
generated sources took part in it.

Presence activates, contents configure - the same grammar as `jacoco.properties`
or `pitest.properties`. `avro.properties` here is **empty**: that is enough to
compile every `.avsc` and `.avpr` the module offers the build. `xjc.properties`
names a target package, and `protoc.properties` names a plugin.

Where the inputs live
---------------------

A generator reads `META-INF/build.jenesis/` under the module's sources. That is the
build's own folder, and the compiler never copies it into the artifact, so a schema
is an input to the build and nothing more - none of these three jars carries the
`.xsd`, the `.proto` or the `.avsc` it was built from, nor an empty directory where
they sat:

    jar tf .../classes.jar
    demo/order/Item.class
    demo/order/ObjectFactory.class
    demo/order/Order.class
    demo/order/package-info.class
    demo/format/xml/Orders.class
    META-INF/MANIFEST.MF

An input that *should* ship - a schema you want to publish, a `.proto` other projects
compile against - lives wherever you want it, and the config file names the folder
instead:

    folders=schema

Folders resolve against the module's source and resource roots, and a generator reads
only the file kinds it compiles out of them, so the folder may hold a `.xsd` for one
generator and a `.proto` for another. `../demo-49-service-contracts` needs exactly
that for its WSDL.

Per-module configuration
------------------------

A Maven module reads `<module>/build.jenesis/` and `<module>/src/main/build.jenesis/`
on top of the project-wide configuration folder, so each module here turns on its
own generator and no other. A generator that finds no input of its kind
contributes no sources rather than failing, so putting one config file at the
project root would simply switch that generator on for every module that has
something for it.

Two dependency closures, kept apart
-----------------------------------

Every generator is a *build* tool: it resolves in a dependency group named after
it (`xjc`, `protoc`, `avro`) and is pinned there. What the generated classes
*import* at run time is a dependency of the artifact you ship, so each module
declares it in its own `pom.xml`:

| Module     | Tool group                             | Declared dependency |
| ---------- | -------------------------------------- | ------------------- |
| `xml`      | `xjc` -> `org.glassfish.jaxb:jaxb-xjc`      | `jakarta.xml.bind:jakarta.xml.bind-api` |
| `protobuf` | `protoc` -> `com.google.protobuf:protoc`, `protoc-grpc-java` -> `io.grpc:protoc-gen-grpc-java` | `com.google.protobuf:protobuf-java`, `io.grpc:grpc-protobuf`, `io.grpc:grpc-stub` |
| `avro`     | `avro` -> `org.apache.avro:avro-tools`      | `org.apache.avro:avro` |

`pin` writes both closures, each under its own group, so the tool and the runtime
API are versioned independently:

    java build/jenesis/Project.java pin

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
    protoc/maven/com.google.protobuf/protoc/exe/linux-x86_64  4.32.1 SHA-256/9a757b... [linux,x86_64]
    protoc/maven/com.google.protobuf/protoc/exe/osx-aarch_64  4.32.1 SHA-256/e3b836... [macos,aarch64]
    protoc-grpc-java/maven/io.grpc/protoc-gen-grpc-java/exe/linux-x86_64 1.83.1 SHA-256/db4044... [linux,x86_64]
    -->

Pin `protoc` rather than floating it: `com.google.protobuf:protoc` still publishes
a `21.0-rc-1` that Maven Central reports as the latest release, and the generated
code has to match the `protobuf-java` the module depends on.

A protoc plugin is a second native executable, named and resolved the same way:

    plugins=grpc-java=io.grpc/protoc-gen-grpc-java

which resolves in its own `protoc-grpc-java` group and reaches protoc as
`--plugin=protoc-gen-grpc-java=<path> --grpc-java_out=<dir>`, so `GreeterGrpc`
lands next to `GreetingProto`.

Where generation runs in the build
----------------------------------

Generation is the `generator` stage of the Java toolchain, immediately before the
compiler chain:

    generated/<tool>/generate -> compiled/javac -> classes -> artifacts/jar

Everything downstream of the compiler sees the generated classes, so tests and
`javadoc` cover them. Everything *upstream* does not: the inferred linters and
formatters (`check`, `format`) read the module's own sources only, so generated
code is never linted or reformatted. The sources jar is upstream too: it carries
the schema, from which the generated files follow, not the generated files.

Layout
------

    demo-48-data-formats
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
        |-- build.jenesis/avro.properties      empty
        |-- pom.xml
        `-- sources
            |-- META-INF/build.jenesis/user.avsc
            `-- demo/format/avro/Users.java
