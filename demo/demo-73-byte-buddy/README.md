Byte Buddy demo
===============

Write a plugin against a library's own Java API - here Byte Buddy's - and add
the classes it generates to the module's jar. The plugin is compiled from local
source, as in the `internal-module` demo, and joins the build at
`binary/compiled`, beside `javac`: it writes `sample.Greeting`, an implementation
of `java.util.function.Supplier` that returns the text the plugin's properties
file configures, and the application loads it and prints what it supplies.

Run it
------

From this directory:

    java build/jenesis/Execute.java

which builds the project and then launches the built module, printing the text
of the class the plugin generated:

    Hello from a class that Byte Buddy generated in a build plugin!

`sample.Greeting` has no source anywhere in the project: `javac` never sees it,
and the module's jar carries `sample/Greeting.class` beside the classes `javac`
compiled.

Layout
------

    demo/demo-73-byte-buddy
    |-- build/jenesis                 symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties    greeter+binary/compiled=./plugin
    |-- build.jenesis/
    |   `-- plugin-greeter.properties  implementation=sample.Greeting
    |                                  greeting=Hello from a class that ...
    |-- plugin/
    |   |-- .jenesis.skip       keeps the project's module discovery out of plugin/
    |   |-- module-info.java     module demo.plugin { requires build.jenesis;
    |   |                                requires net.bytebuddy;
    |   |                                provides build.jenesis.BuildExecutorModule
    |   |                                with demo.plugin.GreeterModule; }
    |   `-- demo/plugin/GreeterModule.java
    `-- sources/
        |-- module-info.java     module demo.bytebuddy { exports sample; }
        `-- sample/Sample.java    loads sample.Greeting and prints what it supplies

Naming the plugin
-----------------

`jenesis.plugins.properties` names the plugin, the hook point it joins and the
folder it is compiled from:

    greeter+binary/compiled=./plugin

`binary/compiled` is where a compiler of the module's own runs, beside `javac`.
What a step there writes below `classes/` is merged with what `javac` compiled
into the module's jar, and the tests and every later step see it as one of the
module's classes. A plugin adds classes and never replaces one: it is not handed
what `javac` compiled, and a class that both write fails the build, naming the
two steps. Rewriting a compiled class in place, as Byte Buddy's `Plugin.Engine`
does, is therefore not something a plugin can do.

Configuring the plugin
----------------------

The plugin runs in a module where `plugin-greeter.properties` is found, here in
`build.jenesis/`:

    implementation=sample.Greeting
    greeting=Hello from a class that Byte Buddy generated in a build plugin!

The file's values reach the provider's constructor as a `SequencedMap`, in the
file's order, and the provider keeps what it needs. `javac` requires a service
provider to keep a public constructor without arguments as well, which the build
uses when the file is empty:

    public GreeterModule() {
        this(Collections.emptyNavigableMap());
    }

    public GreeterModule(SequencedMap<String, String> properties) {
        implementation = properties.getOrDefault("implementation", "sample.Greeting");
        greeting = properties.getOrDefault("greeting", "Hello from a generated class!");
    }

`accept` adds one step, a record of those two values:

    executor.addStep("generate", new Generate(implementation, greeting));

A step's serialised form is part of its cache key, so changing either value in
`plugin-greeter.properties` runs the step again, and nothing else does. The step
describes the class with Byte Buddy and saves it below `classes/` in its own
output folder:

    new ByteBuddy()
            .subclass(Object.class)
            .name(implementation)
            .implement(Supplier.class)
            .method(named("get"))
            .intercept(FixedValue.value(greeting))
            .make()
            .saveIn(Files.createDirectories(context.next().resolve(BuildStep.CLASSES)).toFile());

Byte Buddy's Maven and Gradle plugins run the Byte Buddy plugins they discover in
`META-INF/net.bytebuddy/build.plugins` or that the build file names in XML or its
DSL. A Jenesis plugin is plain Java instead: its provider builds and configures
what Byte Buddy does in code, with the values of its own properties file, and
runs nothing it did not name.

The plugin's dependencies - `build.jenesis` and `net.bytebuddy` - resolve by
module name into the plugin's own module layer, and `pin` records them in
`sources/module-info.java` under the group named after the plugin:

    @jenesis.pin plugin-greeter/module/build.jenesis 0.13.1 SHA-256/...
    @jenesis.pin plugin-greeter/module/net.bytebuddy 1.18.14 SHA-256/...

Byte Buddy never reaches the application: it is a dependency of the plugin
alone, and the generated class refers to nothing but `java.base`.
