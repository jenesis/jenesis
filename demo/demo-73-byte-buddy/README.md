Byte Buddy demo
===============

Rewrite the classes `javac` compiled with Byte Buddy, as its Maven and Gradle plugins
do, from a plugin of the project. The plugin is compiled from local source, as in the
`internal-module` demo, and joins the build at `binary/transform`, after the compilers:
it runs Byte Buddy's `Plugin.Engine` over the module's classes, with two Byte Buddy
plugins - `ToStringPlugin`, which Byte Buddy ships, and one written for the project -
and the module's class path, and what it writes replaces the classes it was handed.

Run it
------

From this directory:

    java build/jenesis/Execute.java

which builds the project and then launches the built module:

    Hello from a method that Byte Buddy rewrote in a build plugin!
    Greeting{audience=the build}

`sample.Greeting` returns `"Hello from javac, " + audience + "!"` in its source and
declares no `toString()`. The first line is what the rewritten `get()` returns, the
second the `toString()` that `ToStringPlugin` added for the `@ToStringPlugin.Enhance`
annotation on the class.

Layout
------

    demo/demo-73-byte-buddy
    |-- build/jenesis                  symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties     enhance+binary/transform=./plugin
    |-- build.jenesis/
    |   `-- plugin-enhance.properties  type=sample.Greeting
    |                                  greeting=Hello from a method that ...
    |-- plugin/
    |   |-- .jenesis.skip              keeps the project's module discovery out of plugin/
    |   |-- module-info.java           module demo.plugin { requires build.jenesis;
    |   |                                      requires net.bytebuddy;
    |   |                                      provides build.jenesis.BuildExecutorModule
    |   |                                      with demo.plugin.EnhanceModule; }
    |   `-- demo/plugin/EnhanceModule.java
    `-- sources/
        |-- module-info.java           module demo.bytebuddy { requires static net.bytebuddy; ... }
        `-- sample/
            |-- Greeting.java          @ToStringPlugin.Enhance, a get() that is rewritten
            `-- Sample.java            prints what a Greeting supplies, and the Greeting

Naming the plugin
-----------------

`jenesis.plugins.properties` names the plugin, the hook point it joins and the
folder it is compiled from:

    enhance+binary/transform=./plugin

`binary/transform` runs after the compilers. A plugin there is handed the module's
compiled classes as its first argument and the module's inputs after it, among them
its resolved dependencies. What it writes below `classes/` replaces the class of that
name, every class it does not write passes on as it was, and the jar, the tests and
every later step see the result. Several such plugins run in the order the file names
them, each handed the classes the one before it wrote.

Configuring the plugin
----------------------

The plugin runs in a module where `plugin-enhance.properties` is found, here in
`build.jenesis/`:

    type=sample.Greeting
    greeting=Hello from a method that Byte Buddy rewrote in a build plugin!

The file's values reach the provider's constructor as a `SequencedMap`, in the
file's order, and the provider keeps what it needs. `javac` requires a service
provider to keep a public constructor without arguments as well, which the build
uses when the file is empty:

    public EnhanceModule() {
        this(Collections.emptyNavigableMap());
    }

    public EnhanceModule(SequencedMap<String, String> properties) {
        type = properties.getOrDefault("type", "sample.Greeting");
        greeting = properties.getOrDefault("greeting", "Hello from a rewritten method!");
    }

`accept` adds one step, a record of those two values, handed everything the hook
point reads:

    executor.addStep("enhance", new Enhance(type, greeting), inherited.sequencedKeySet());

A step's serialised form is part of its cache key, so changing either value in
`plugin-enhance.properties` runs the step again, and so does a change to the classes
it is handed.

Running Byte Buddy's plugins
----------------------------

The step collects the jars the module compiles against, which
`Dependencies.select(folder, "main", "compile")` lists for each argument, as Byte
Buddy's class path, and runs the engine from the classes it was handed into
`classes/` of its own output:

    try (ClassFileLocator locator = new ClassFileLocator.Compound(classPath)) {
        new Plugin.Engine.Default()
                .with(locator)
                .apply(arguments.firstEntry().getValue().folder().resolve(BuildStep.CLASSES).toFile(),
                        Files.createDirectories(context.next().resolve(BuildStep.CLASSES)).toFile(),
                        new Plugin.Factory.Simple(new ToStringPlugin()),
                        new Plugin.Factory.Simple(new Greeting(type, greeting)));
    }

The class path is what lets Byte Buddy read the types a class refers to beyond the
JDK. `@ToStringPlugin.Enhance` comes from the module's `requires static
net.bytebuddy`: without the module's jars, Byte Buddy cannot resolve the annotation,
`ToStringPlugin` matches nothing, and the application prints `sample.Greeting@...`
instead.

The second plugin is a `net.bytebuddy.build.Plugin` of the project's own, which
matches the type its properties name and rewrites its `get()` to return the
configured text:

    public boolean matches(TypeDescription target) {
        return target.getName().equals(type);
    }

    public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder,
                                        TypeDescription typeDescription,
                                        ClassFileLocator classFileLocator) {
        return builder.method(named("get")).intercept(FixedValue.value(greeting));
    }

Byte Buddy's Maven and Gradle plugins run the Byte Buddy plugins they discover in
`META-INF/net.bytebuddy/build.plugins` or that the build file names in XML or its
DSL. A Jenesis plugin is plain Java instead: its provider constructs the plugins it
runs, with the values of its own properties file, and runs nothing it did not name.

The plugin's dependencies - `build.jenesis` and `net.bytebuddy` - resolve by module
name into the plugin's own module layer, and `pin` records them in
`sources/module-info.java` under the group named after the plugin, beside the
`net.bytebuddy` the module itself compiles against:

    @jenesis.pin plugin-enhance/module/build.jenesis ...
    @jenesis.pin plugin-enhance/module/net.bytebuddy ...

Byte Buddy never reaches the running application: the module requires it only to
compile against its annotation, and what the plugin rewrote refers to nothing but
`java.base`.
