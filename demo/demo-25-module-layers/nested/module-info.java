/**
 * A provider isolated one level deeper: it lives in a layer declared by a module that is itself
 * isolated in a layer. Nesting needs no mechanism of its own - a layer is a child of its caller's,
 * so this one is a child of {@code render} rather than of the application.
 *
 * @jenesis.release 25
 * @jenesis.pin com.fasterxml.jackson.core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 * @jenesis.pin maven/com.fasterxml.jackson.core/jackson-core/pom 2.13.5 SHA-256/ad91ab6025eb48ae3262280c42efebb57b70d25c00ab0a69c5a3c7ca3a8078ee
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-base/pom 2.13.5 SHA-256/f2e41218dd50b92ce4983c5d2c04209dd61cd1e00fc125e3600aa9eaf4501dea
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-bom/pom 2.13.5 SHA-256/0a26b6f34ab93f9cfa80179888c6bf425f1c17dcd9c11db6561393930fdb746a
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-parent/pom 2.13 SHA-256/2bba89978172af1effcb4d143d09921a3f3082ca4dcf122b1ded98bf55b2ad57
 * @jenesis.pin maven/com.fasterxml/oss-parent/pom 43 SHA-256/e5585cc1c37079b2e3817a8997945736f158831844d59d0e4d3a45b27611f926
 */
module demo.layers.nested {
    requires com.fasterxml.jackson.core;
    requires demo.layers.spi;

    provides demo.layers.spi.Nested with demo.layers.nested.NestedReport;
}
