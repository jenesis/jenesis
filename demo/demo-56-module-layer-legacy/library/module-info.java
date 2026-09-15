/**
 * The library that keeps a legacy dependency private. What it isolates is not one jar but a tree:
 * commons-beanutils, which names itself nowhere, and the commons-logging and commons-collections it
 * drags behind it, which name themselves nowhere either.
 *
 * @jenesis.release 25
 * @jenesis.layer beans api demo.legacy.spi
 * @jenesis.layer beans module/demo.legacy.impl
 * @jenesis.pin build.jenesis.launcher 1-SNAPSHOT SHA-256/8dd18ab78e7d95cd01a9b6e8fa8a3b67938af8ff8ca65174fbdb124999231f0d
 * @jenesis.pin build.jenesis/build.jenesis.launcher 1-SNAPSHOT SHA-256/8dd18ab78e7d95cd01a9b6e8fa8a3b67938af8ff8ca65174fbdb124999231f0d
 * @jenesis.pin layer:beans/maven/commons-beanutils/commons-beanutils 1.9.4 SHA-256/7d938c81789028045c08c065e94be75fc280527620d5bd62b519d5838532368a
 * @jenesis.pin layer:beans/maven/commons-collections/commons-collections 3.2.2 SHA-256/eeeae917917144a68a741d4c0dff66aa5c5c5fd85593ff217bced3fc8ca783b8
 * @jenesis.pin layer:beans/maven/commons-logging/commons-logging 1.2 SHA-256/daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636
 */
module demo.legacy.library {
    requires build.jenesis.launcher;
    requires demo.legacy.spi;

    exports demo.legacy.library;
}
