/**
 * @jenesis.release 25
 * @jenesis.pin groovy/module/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin groovy/module/build.jenesis.plugin.groovy 0.1.0 SHA-256/a2ca0fb58b289787e65a20ed7d0491e3405cfa74d12ea7ecd214eb07d5ffc877
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin maven/org.apache.groovy/groovy-bom/pom 5.0.6 SHA-256/ad5d4b53e161e6537afe57dc96a6608cb36896cfb733caa997e9be28d59ab54e
 * @jenesis.pin maven/org.apache.groovy/groovy/pom 5.0.6 SHA-256/17eed34dc0dc57b902b38e19136394e72431c515d0aaed0e3fabd95df77b36a8
 * @jenesis.pin org.apache.groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 * @jenesis.pin org.apache.groovy/groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 */
module sample {
    requires org.apache.groovy;
    exports sample;
}
