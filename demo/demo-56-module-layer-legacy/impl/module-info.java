/**
 * The provider, isolated in the layer together with the legacy tree it needs. It requires
 * commons-beanutils under a name this project gives it, because the jar names itself nowhere: an
 * {@code @jenesis.alias} is all it takes to make it an automatic module the layer can resolve.
 *
 * What the alias does not have to cover is the rest of the tree. commons-logging and
 * commons-collections come along as commons-beanutils' own dependencies, name themselves nowhere
 * either, and are read through the layer's class path - which is what an automatic module reads,
 * exactly as it would on a plain {@code -cp}. Naming every jar of a legacy tree is the work a layer
 * exists to avoid.
 *
 * @jenesis.release 25
 * @jenesis.alias commons.beanutils commons-beanutils/commons-beanutils
 * @jenesis.pin commons-beanutils/commons-beanutils 1.9.4 SHA-256/7d938c81789028045c08c065e94be75fc280527620d5bd62b519d5838532368a
 * @jenesis.pin commons-collections/commons-collections 3.2.2 SHA-256/eeeae917917144a68a741d4c0dff66aa5c5c5fd85593ff217bced3fc8ca783b8
 * @jenesis.pin commons-logging/commons-logging 1.2 SHA-256/daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636
 */
module demo.legacy.impl {
    requires commons.beanutils;
    requires demo.legacy.spi;

    provides demo.legacy.spi.Beans with demo.legacy.impl.ConvertingBeans;
}
