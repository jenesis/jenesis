Java (POM model 4.1.0) demo
===========================

The project of `../demo-03-java-pom-multi`, written in POM model 4.1.0, the model
Maven 4 introduces for the `pom.xml` files you keep in source control. Jenesis reads
it as it reads the 4.0.0 model, and the POMs it publishes stay 4.0.0, the model
every consumer of a repository expects.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Both modules build and the `greeter` test runs, exactly as in
`../demo-03-java-pom-multi`.

Layout
------

    demo/demo-68-java-pom-model-4-1-0
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- pom.xml              aggregator (packaging pom); lists the two subprojects
    |-- greeter/             the library, with a JUnit test
    |   |-- pom.xml          <parent/>; its sources and tests as <build><sources>
    |   |-- sources/sample/greeter/Greeter.java
    |   `-- test/sample/greeter/GreeterTest.java
    `-- app/                 the consumer
        |-- pom.xml          <parent/>; requires greeter without a version
        `-- sources/sample/app/App.java

What model 4.1.0 leaves out
---------------------------

The aggregator lists its modules as `<subprojects>`, the name 4.1.0 gives
`<modules>`:

    <project xmlns="http://maven.apache.org/POM/4.1.0">
        <modelVersion>4.1.0</modelVersion>
        ...
        <subprojects>
            <subproject>greeter</subproject>
            <subproject>app</subproject>
        </subprojects>
    </project>

A subproject names no coordinate for its parent: `<parent/>` is the POM in the folder
above, and `<parent><relativePath>../platform</relativePath></parent>` the one in
another folder. Its groupId and version come from that POM:

    <parent/>
    <artifactId>greeter</artifactId>

A dependency on another subproject names no version, because the subproject has one:

    <dependency>
        <groupId>build.jenesis.demo</groupId>
        <artifactId>greeter</artifactId>
    </dependency>

The source folders are `<source>` elements, with `main` the default scope and `java` the
default language; a `<source>` of the language `resources` names a resource folder,
and one that is not `<enabled>` is left out:

    <build>
        <sources>
            <source>
                <directory>sources</directory>
            </source>
            <source>
                <scope>test</scope>
                <directory>test</directory>
            </source>
        </sources>
    </build>

An aggregator that lists no subprojects at all takes every folder beside it that holds
a `pom.xml`, as Maven 4 does. A dependency of the type `processor` is placed on the
annotation processor path, as `<!--jenesis.plugin ...-->` places one in a 4.0.0 POM.

What Jenesis does not read is refused by name rather than ignored: a `<source>` that
builds a Java module of its own (`<module>`) or targets a release (`<targetVersion>`),
more than one `java` source folder for one scope, and the dependency types
`classpath-jar` and `modular-jar`, because Jenesis places a jar by whether it
describes a module.

`pin` rewrites a 4.1.0 POM as it rewrites a 4.0.0 one, and the POM a module is
published with is the 4.0.0 model, with every version the build inferred written out.
