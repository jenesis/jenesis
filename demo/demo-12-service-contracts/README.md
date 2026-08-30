Generated sources demo (service contracts)
==========================================

The same generation as `../demo-11-data-formats`, one level up: instead of a data
model, the generator writes the *client side of a service* from its published
contract. Two modules, two contract languages.

Run it
------

    java build/jenesis/Project.java

| Module | Config file                            | Input                 | Ships? | Generator | Generated |
| ------ | -------------------------------------- | --------------------- | --- | --------- | --------- |
| `soap` | `soap/build.jenesis/wsimport.properties` | `wsdl/greeter.wsdl`   | yes | `wsimport` | `demo.greeter` |
| `rest` | `rest/build.jenesis/openapi.properties`  | `META-INF/build.jenesis/greeting.yaml` | no | OpenAPI Generator | `demo.greeting` |

The two modules differ on purpose. A generator reads `META-INF/build.jenesis/` under
the module's sources by default, and the compiler never copies that folder into the
artifact - which is what `rest` wants, since nothing reads the specification at run
time. `soap` needs the opposite: a JAX-WS client reads its WSDL when the service class
is constructed, so the description has to be in the jar. Naming the folder in the
config file puts it where the module ships it:

    folders=wsdl

`soap` gets a JAX-WS port interface and a `GreeterService` locator; `rest` gets a
JDK-`HttpClient` API client and a `Greeting` model. Each module has one
hand-written class calling into the generated types, so a build that compiles
proves the generation took part in it.

Nothing the generators write mentions where the build ran. Both of these tools
would otherwise leave a trace of it in the shipped artifact - wsimport bakes the
description's location into `@WebServiceClient` and a static initializer, and the
OpenAPI Generator stamps the wall clock and the local time zone into `@Generated`
on every class. The build states a class-path location for the first and turns the
timestamp off for the second, so the same sources give the same bytes on any
machine.

wsimport
--------

    package=demo.greeter
    folders=wsdl
    location=/wsdl/greeter.wsdl

Every `.wsdl` in the named folders is compiled; `folders` moves them out of the
build's own folder, here to keep the description where it ships. The
build passes `-Xnocompile`, because generating the sources is the whole job -
`javac` compiles them as part of the module, on the same source path as
everything else.

It also states where the description will be at run time. Left alone, wsimport
writes the path it read into the generated service:

    wsdlLocation = "file:/home/you/project/target/build/.../sources/wsdl/greeter.wsdl"

which is an absolute path, to a build directory, compiled into the artifact you
ship. So `location` states the place the description ships under instead -
`sources/wsdl/greeter.wsdl` is copied into the jar like any other resource, so a
class-path lookup finds it:

    GREETERSERVICE_WSDL_LOCATION = GreeterService.class.getResource("/wsdl/greeter.wsdl");

It is required, and states a live endpoint just as readily as a class-path path:
wsimport writes one location for the whole run, and only the project knows where
its description is served. A generator that reads its description out of the build's
own folder has nothing to ship at all, and the build says so rather than writing a
client that fails when its service class is first touched. A `.xjb` in a named folder
is passed as a binding, a `catalog=<file>` names a catalog, and `arguments` are the
remaining keys.

OpenAPI
-------

    package=demo.greeting
    arguments=--library native --additional-properties useJakartaEe=true

No key names the input here. A `.xsd`, a `.proto` and an `.avsc` announce themselves
by extension, and so does a lone OpenAPI document: the generator takes one document
per run, so one `.yaml` or `.json` in the folders is the specification. A module that
offers several says which with `specification=<file>`, and the build fails saying so
rather than guessing.

The generator writes a whole project - a `pom.xml`, a `build.gradle`, a README,
docs, tests - of which only the source folder belongs in your module. The build
runs the generator into a scratch folder and collects `src/main/java` out of it;
`sources=<path>` renames that folder for a generator that writes elsewhere.

The generated client's dependencies are yours
---------------------------------------------

This is where a code generator earns its keep and hands you a bill. The OpenAPI
`java` generator with `--library native` produces a client that imports Jackson
and Jakarta annotations, so `rest/pom.xml` declares them - four dependencies for
one endpoint. That is the generator's choice, not the build's, and stating it in
the `pom.xml` is what keeps it visible and pinned:

    jackson-databind, jackson-datatype-jsr310, jackson-databind-nullable, jakarta.annotation-api

The tools themselves stay out of it: `wsimport` resolves in the `wsimport` group
(`com.sun.xml.ws:jaxws-tools`) and the OpenAPI generator in the `openapi` group
(`org.openapitools:openapi-generator-cli`), each pinned separately from what the
generated code needs at run time.

Layout
------

    demo-12-service-contracts
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- pom.xml                  parent, two modules
    |-- soap
    |   |-- build.jenesis/wsimport.properties
    |   |-- pom.xml              jakarta.xml.ws-api
    |   `-- sources
    |       |-- wsdl/greeter.wsdl                      named, so the client can read it
    |       `-- demo/contract/soap/Greeters.java
    `-- rest
        |-- build.jenesis/openapi.properties
        |-- pom.xml              the generated client's Jackson dependencies
        `-- sources
            |-- META-INF/build.jenesis/greeting.yaml   the input, which does not ship
            `-- demo/contract/rest/Greetings.java
