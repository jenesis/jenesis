Toolchain demo
==============

Name the JDK a build runs on, and let Jenesis find it among the JDKs already
installed. The project's `jenesis.properties` asks for JDK 25:

    jenesis.toolchain.version=25

`Make` and `Execute` check the JVM they were started on first. When it matches,
the build runs as always. When it does not, they look for a matching JDK and run
themselves again on it, with the same selectors and the same `-Djenesis.*`
properties. Jenesis never downloads or installs a JDK by itself: a build that finds
no match fails, listing what it found, unless you named an installer as shown below.

The module declares no release, so it compiles for the JDK that runs the build, and
its main class prints that JDK's feature version.

Run it
------

From this directory, on JDK 25:

    java build/jenesis/Execute.java

builds the module and prints

    25

Ask for another JDK on the command line, where a `-D` overrides the project's file:

    java -Djenesis.toolchain.version=26 build/jenesis/Execute.java

On a machine with a JDK 26 in one of the usual places, this prints a line naming
the JDK it selected, builds again, since the classes now target 26, and prints

    26

The version
-----------

A version is `<feature>[.<interim>[.<update>...]][-<word>...]`:

- **The numbers match as a prefix**, with missing ones counting as zero: `25`
  matches every JDK 25, `25.0.3` only that update and its patches.
- **Every word must be one the JDK answers to.** Those are the words of the vendor
  and vendor version its `release` file records, and of the pre-release and
  optional parts of its version: Temurin answers to `eclipse`, `adoptium` and
  `temurin`, Zulu to `azul` and `zulu`, GraalVM Community to `graalvm` and
  `community`, and an LTS build to `lts`. No vendor list is built in, so a JDK
  built in-house answers to its own name.
- **A pre-release matches only when its word is named.** `26-ea` selects an
  early-access build of 26; `26` never does.

Among several matches, the newest version wins. A JDK is identified by reading its
`release` file, never by running it, so no JDK is executed before one is chosen.

Where Jenesis looks
-------------------

`jenesis.toolchain.searchpath` lists the folders, separated by commas. An entry is
an absolute folder or starts with `~`, and `*` stands for any one folder name. The
default, `@`, stands for the usual locations of the operating system: on Linux
`/usr/lib/jvm/*`, SDKMAN, IntelliJ's `~/.jdks` and mise; on macOS
`/Library/Java/JavaVirtualMachines`, Homebrew, SDKMAN and mise; on Windows the
vendors' folders under `C:\Program Files`, `~\.jdks` and Scoop. `@` can be combined
with folders of your own:

    java -Djenesis.toolchain.searchpath=@,/opt/jdks/* build/jenesis/Execute.java

An empty search path searches nothing: the build only checks that the JVM it was
started on matches, which suits a CI job that sets its JDK up itself.

Installing a missing JDK
------------------------

Jenesis installs nothing by itself, but it runs an installer you name when no JDK
matches. The Jenesis command-line install ships one, `jenesis-jdk`, which turns a
version into a request for the tool that installs JDKs on your machine: SDKMAN or
mise, or Scoop on Windows.

Where SDKMAN, mise or Scoop installed Jenesis, the `jenesis` command names its
`jenesis-jdk` as the installer through the `JENESIS_TOOLCHAIN_INSTALLER` environment
variable, for the run it starts and nothing else, and `jenesis-jdk` installs the JDK
with that tool in turn. A run of the installed command asking for a JDK you do not
have then installs it and runs on it:

    jenesis-exec -Djenesis.toolchain.version=25-zulu

A build started from the sources, as `java build/jenesis/Execute.java`, has no
installing tool to call back, and runs an installer only where you name one, with
`-Djenesis.toolchain.installer` or in your own `~/.jenesis/jenesis.properties`.
The installer runs in your home folder, gets the version as its last argument, and
has to install into a folder on the search path; Jenesis searches again afterwards
and checks what it finds like any other JDK.

What a project cannot do
------------------------

The search path and the installer decide what the build executes, so they are
yours to set, never the project's. Both are accepted on the command line and in
your own `~/.jenesis/jenesis.properties`, and refused in the project's
`jenesis.properties`, in its profiles, and in a user-global file whose location
the project chose. A project can name the version it needs, and so choose among
the JDKs you installed or your installer provides, but not point the build at a
program of its own.

Before a JDK it found runs, Jenesis checks on POSIX systems that every file of it
belongs to you or to root and is writable by no other user; a JDK that fails the
check is refused with the file named, rather than exchanged for another match.

Layout
------

    demo/demo-06-toolchain
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- jenesis.properties         jenesis.toolchain.version=25
    `-- sources
        |-- module-info.java       demo.toolchain, no release, @jenesis.main
        `-- demo/toolchain
            `-- Feature.java       prints the feature version of the running JDK
