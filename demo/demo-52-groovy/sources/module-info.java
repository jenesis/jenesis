/**
 * @jenesis.release 25
 * @jenesis.pin groovy/module/build.jenesis 0.18.2 SHA-256/18494e5406c1a6da0718318cc4037b98ba2c28ce1b1b250580e1b33afd6db7a9
 * @jenesis.pin groovy/module/build.jenesis.plugin.groovy 0.2.0 SHA-256/25dd8f40a87d19e274e223d93b3c46b4752c7b009ff570fa63a02cb0dea2b00d
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-bom/pom 6.0.0-alpha-1 SHA-256/d8fad415eeaf4377a6098506d29b345134f117a7d12187e2d6b1278ea423b04d
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy/pom 6.0.0-alpha-1 SHA-256/bc2f56284d37d6d5e634522dcc6d1431a5e1a73377fb70109a093533c5b20986
 * @jenesis.pin maven/org.apache.groovy/groovy-bom/pom 5.0.6 SHA-256/ad5d4b53e161e6537afe57dc96a6608cb36896cfb733caa997e9be28d59ab54e
 * @jenesis.pin maven/org.apache.groovy/groovy/pom 5.0.6 SHA-256/17eed34dc0dc57b902b38e19136394e72431c515d0aaed0e3fabd95df77b36a8
 * @jenesis.pin org.apache.groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 * @jenesis.pin org.apache.groovy/groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 */
module sample {
    requires org.apache.groovy;
    exports sample;
}
