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
 * @jenesis.pin maven/commons-beanutils/commons-beanutils/pom 1.9.4 SHA-256/c35cca7b61d4678d9578cbc0b901b8717b539abf9254441da78b8fe60de064d0
 * @jenesis.pin maven/commons-collections/commons-collections/pom 3.2.2 SHA-256/d5d81fcc288c0d8c711c302007cada4aa9a226ed1a112d4baa64cb1d6322170b
 * @jenesis.pin maven/commons-logging/commons-logging/pom 1.2 SHA-256/c91ab5aa570d86f6fd07cc158ec6bc2c50080402972ee9179fe24100739fbb20
 * @jenesis.pin maven/org.apache.commons/commons-parent/pom 34 SHA-256/3a2e69d06d641d1f3b293126dc9e2e4ea6563bf8c36c87e0ab6fa4292d04b79c
 * @jenesis.pin maven/org.apache.commons/commons-parent/pom 39 SHA-256/87cd27e1a02a5c3eb6d85059ce98696bb1b44c2b8b650f0567c86df60fa61da7
 * @jenesis.pin maven/org.apache.commons/commons-parent/pom 47 SHA-256/8a8ecb570553bf9f1ffae211a8d4ca9ee630c17afe59293368fba7bd9b42fcb7
 * @jenesis.pin maven/org.apache/apache/pom 13 SHA-256/ff513db0361fd41237bef4784968bc15aae478d4ec0a9496f811072ccaf3841d
 * @jenesis.pin maven/org.apache/apache/pom 16 SHA-256/9f85ff2fd7d6cb3097aa47fb419ee7f0ebe869109f98aba9f4eca3f49e74a40e
 * @jenesis.pin maven/org.apache/apache/pom 19 SHA-256/91f7a33096ea69bac2cbaf6d01feb934cac002c48d8c8cfa9c240b40f1ec21df
 */
module demo.legacy.impl {
    requires commons.beanutils;
    requires demo.legacy.spi;

    provides demo.legacy.spi.Beans with demo.legacy.impl.ConvertingBeans;
}
