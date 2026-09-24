/**
 * An ordinary consumer. It requires the library and nothing else: the legacy tree the library needs
 * is not on its path, not in its pins, and not in its vocabulary. That is the whole point - a
 * consumer of a library that isolates its dependencies declares nothing about them.
 *
 * @jenesis.release 25
 * @jenesis.main demo.legacy.app.Main
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.5.1 SHA-256/b9db437eccaeaef7ee04a6a00a8e330bc69542614012fa6485079f41e3c9edfb
 * @jenesis.pin layer:beans/maven/commons-beanutils/commons-beanutils 1.9.4 SHA-256/7d938c81789028045c08c065e94be75fc280527620d5bd62b519d5838532368a
 * @jenesis.pin layer:beans/maven/commons-collections/commons-collections 3.2.2 SHA-256/eeeae917917144a68a741d4c0dff66aa5c5c5fd85593ff217bced3fc8ca783b8
 * @jenesis.pin layer:beans/maven/commons-logging/commons-logging 1.2 SHA-256/daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636
 */
module demo.legacy.app {
    requires demo.legacy.library;
}
