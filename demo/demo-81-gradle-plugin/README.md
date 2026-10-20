Gradle plugin demo
==================

Build a Gradle plugin and publish it so that a Gradle build applies it by its ID.
There is no build script: the `pom.xml` declares the module and the Gradle API it
compiles against, a resource names the plugin as Gradle expects, and a
`gradleplugin.properties` in the `build.jenesis/` configuration folder switches on
the official plugin `build.jenesis.plugin.gradleplugin`, which stages the plugin's
marker beside the jar.

Build it
--------

From this directory:

    java build/jenesis/Make.java stage

The staged Maven repository holds the jar and, for the plugin it declares, the
marker that Gradle's `plugins` block looks the ID up by:

    target/stage/maven/output/demo/greet-gradle-plugin/1.0/greet-gradle-plugin-1.0.jar
    target/stage/maven/output/demo/greet/demo.greet.gradle.plugin/1.0/demo.greet.gradle.plugin-1.0.pom

A Gradle build that names the staged repository among its plugin repositories
applies the plugin by its ID:

    // settings.gradle
    pluginManagement { repositories { maven { url = uri('<this demo>/target/stage/maven/output') } } }

    // build.gradle
    plugins { id 'demo.greet' version '1.0' }

and `gradle hello` prints `Hello from a Gradle plugin that Jenesis built!`.

Layout
------

    demo/demo-81-gradle-plugin
    |-- build/jenesis                       symlink to ../../../sources/build/jenesis
    |-- build.jenesis/
    |   `-- gradleplugin.properties         switches the markers on, and stays empty
    |-- pom.xml                             the module and the Gradle API, provided
    `-- src/main
        |-- java/demo/greet/GreetPlugin.java
        `-- resources/META-INF/gradle-plugins/
            `-- demo.greet.properties       implementation-class=demo.greet.GreetPlugin

What is published
-----------------

A Gradle plugin is an ordinary jar. Gradle does not publish its API to Maven
Central, so the module compiles against the community build of it,
`dev.gradleplugins:gradle-api`, in scope `provided`, as Gradle supplies its own
API when it runs the plugin. A file `META-INF/gradle-plugins/<id>.properties`
among the resources names the class that implements the plugin, and a build that
puts the jar on its `buildscript` class path applies it by that ID with
`apply plugin: 'demo.greet'`.

The `plugins` block resolves an ID through a marker instead: the artifact
`<id>:<id>.gradle.plugin`, a POM that depends on the jar. For every descriptor of a
staged jar, the plugin stages that marker with the name, description, URL,
licences, developers and SCM of the jar's own POM, which a repository such as
Maven Central requires of every artifact, so `export` and `release` ship it with
the jar. A plugin ID that is no ID, one in a namespace Gradle keeps for its own
plugins, a descriptor naming a class its jar does not hold, and an ID two jars
declare each fail the build. Publishing to the Gradle Plugin Portal takes the
portal's own tooling.

`-Djenesis.artifact.gradleplugin=false` switches the markers off.
