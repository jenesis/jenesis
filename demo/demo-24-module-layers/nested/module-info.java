/**
 * A provider isolated one level deeper: it lives in a layer declared by a module that is itself
 * isolated in a layer. Nesting needs no mechanism of its own - a layer is a child of its caller's,
 * so this one is a child of {@code render} rather than of the application.
 *
 * @jenesis.release 25
 * @jenesis.pin com.fasterxml.jackson.core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 */
module demo.layers.nested {
    requires com.fasterxml.jackson.core;
    requires demo.layers.spi;

    provides demo.layers.spi.Nested with demo.layers.nested.NestedReport;
}
