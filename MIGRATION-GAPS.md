# Migration gaps

What 25 open-source builds needed from Jenesis that it does not offer yet, collected while moving them off Maven and
Gradle. Each item names the projects that ran into it and what they did instead: a plugin of their own, a test run or
artifact they dropped, or the old build they kept beside Jenesis.

The projects: caffeine, commons-lang, gson, guava, hibernate-validator, jackson-core, JavaHamcrest, javapoet, jhipster
(sample app), jjwt, jline3, JsonPath, JSqlParser, jsoup, junit-framework, junit-pioneer, mapstruct, micrometer,
mockito, moshi, netty, picocli, quarkus (Panache sample), spring-boot-realworld-example-app and spring-petclinic.

Sizes are a rough estimate of the change to the tool: **S** is a setting or a step's option, **M** a new capability
of an existing module, **L** a new mechanism.

## Status

About 111 gaps found along the way are fixed in the tool, among them forking `javac`, `--source`/`--target`, modular
Javadoc, resources in the sources jar, a check for a module declared twice, `kotlinc` arguments, scope and exclusions
on a pin, the operating system family as a platform token, bytecode transforms at `binary/transform` (bnd, AspectJ),
`tar.gz` dependencies, Docker arguments, reusing a dependency resolution whose inputs did not change, generated
sources in the `ide` project files, relocation POMs, `Implementation-*` manifest entries, JaCoCo coverage limits, and
classifier variants (`classifiers.properties`, `@jenesis.pin <module> :<classifier>`).

30 more are answered by the migration briefing (`skill/migrate`, `skill/registry`) rather than by the tool, because a
plugin or a small change to the project covers them: OSGi manifests from a `binary/transform` plugin, resource
filtering and extra source roots from a plugin at `binary/compiled` or `binary/generated`, a tool whose packages split
across a plugin layer forked instead, `os-maven-plugin`, the git revision, pinning every profile, `test-jpms`
suites that run on the class path, and where each jar embeds its SBOM
(`META-INF/sbom/<groupId>/<artifactId>.cdx.json`) compared to the CycloneDX plugins.

What follows is what remains.

## Hit in four projects or more

| Gap | Size | Projects |
|---|---|---|
| **No compile avoidance.** A dependency's jar is compared by its bytes, so any change to it recompiles every module that depends on it, even when its API is unchanged. | L | gson, caffeine, micrometer, moshi |
| **Formatters take no options.** A line of `process-*.properties` becomes a JVM option, not a formatter option: no AOSP style, no reflow switch, no Eclipse formatter, no ktfmt, no licence headers. | M | moshi, mockito, junit-pioneer, hibernate-validator, jline3, JSqlParser |
| **Tests always run on the build's JDK.** A test run on another Java version, as a CI matrix does, cannot be declared. | M | JavaHamcrest, JsonPath, junit-pioneer, jsoup, commons-lang, jjwt |
| **One test run per module.** No second run of the same tests with other JVM options or another selection, as a second Surefire execution does. | M | micrometer, junit-framework, mockito (sibling modules instead), netty |

Compile avoidance matters most: it is the largest share of the difference to Gradle on a build after an edit
(caffeine 16 min against 13, micrometer 11 min against 7:40, moshi 238 s against 67 s).

## Testing

| Gap | Size | Projects |
|---|---|---|
| A process file's `@<key>` reads only the global `jenesis.variable.*`; there is no variable for the module's own coordinates. | S | netty |
| Tests shipped in a dependency's jar do not run; only the module's own compiled tests are selected. | S | caffeine (JCache conformance suite) |
| No location for configuration that only the tests, or only the main code, of every module should see. | S | jline3 (copied into 16 test modules), hibernate-validator |
| A plugin cannot hand a file it produced to the test JVM; the path of its step has to be written into `process-test.properties`. | S | quarkus, mockito (OSGi tests dropped) |
| A test module cannot keep a jar without a module identity on the class path. | M | JSqlParser (JavaCC tests stay on Maven) |

## Packaging and publishing

| Gap | Size | Projects |
|---|---|---|
| Documentation is switched on or off for the whole project; `packaging.properties` has no key for one module. | S | netty |
| No development-only scope, for a dependency the application runs with locally but never ships. | S | spring-petclinic (DevTools kept in a Maven profile) |

## Compilers

| Gap | Size | Projects |
|---|---|---|
| Groovy is compiled after `javac`, against its classes; Java and Groovy sources that refer to each other cannot be compiled jointly. | M | jline3 (stub plugin) |
| Only Groovy 4 or newer and Scala 3 are supported. | M | junit-framework (Spock 1.3 fixture filtered out), picocli (68 Groovy tests and the Scala examples dropped) |
| Kotlin: no `-Xfriend-paths` for tests (S), no KSP (M), and `.kt` sources below `META-INF/versions` are compiled into the base release (S). | S/M | moshi (version-coupled workaround, KSP and multi-release plugins) |
| An annotation processor falls back to the class path in a module build only when module resolution fails outright. | S | jhipster (hibernate-processor and the JPA metamodel lost) |
| No native compilation (C, Rust, JNI). Prebuilt libraries can be published as a classifier variant, but not built. | L | netty (natives stay on Maven) |

## Code quality

| Gap | Size | Projects |
|---|---|---|
| Dokka takes no options. | S | moshi |
| No built-in module for forbiddenapis, Apache RAT, revapi or Android's animal-sniffer signatures; each needs a plugin. | M | several |

## Dependency resolution

| Gap | Size | Projects |
|---|---|---|
| A Kotlin Multiplatform artifact resolves to its root jar, which holds no classes, and nothing reports it. Without Gradle module metadata the platform variant is not found; at the least, the build should fail and name the variant to depend on. | M | spring-boot-realworld-example-app (clikt), micrometer, moshi |

## Frameworks without a built-in module

| Gap | Size | Projects |
|---|---|---|
| GWT | L | guava |
| Android | L | mockito |
| Angular and Node front ends | L | jhipster |
| Asciidoctor | L | picocli, hibernate-validator |
| `quarkus:dev`, and `@QuarkusTest` on the module path | L | quarkus |

## Partial or unconfirmed

- When a test JVM writes outside its own folder, the build names the folder it wrote to but does not prevent the
  write (quarkus).
- Whether a plugin's dependency group can resolve a sibling module of the project is not settled (moshi).

## Deliberately unsupported

These are decisions rather than gaps. The briefing says so and names the change to the project that replaces each:

- shading and relocation;
- more than one release per module, including compiled stubs excluded from the jar - the stubs become a module of
  their own, required as `static`, and a newer call becomes a multi-release class;
- a Java 8 or older base built from `module-info.java`;
- white-box tests through `--patch-module`;
- Gradle's highest-version conflict resolution;
- retrying a failed test, which belongs to the test framework (JUnit Pioneer's `@RetryingTest`, TestNG's
  `IRetryAnalyzer`), and a JVM per test class - each test module runs in a JVM of its own, so a test that needs one
  becomes a module (micrometer, mockito, caffeine);
- overriding a `pom.xml` `${...}` property from the command line;
- Gradle module metadata, war and ear archives, and running Docker containers as part of a build.

## Suggested order

1. **The small items, in one change.** A per-module coordinate variable, a per-module documentation switch, and
   failing on a class-less Kotlin Multiplatform root.
2. **Test JDK and test runs.** Choosing the JDK a test run uses, and declaring more than one run per module, belong
   together: a second run on another Java version is the most common reason for a second run. About eight
   projects.
3. **Formatter options.** Six projects.
4. **Compile avoidance.** Compare a dependency by a fingerprint of its API rather than its bytes. The largest change,
   and the one a build after an edit shows most.
