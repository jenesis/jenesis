/**
 * @jenesis.release 25
 * @jenesis.pin groovy/module/build.jenesis 0.18.0 SHA-256/35a94fc0761110ebcd2fa51b2226a3e7e0a942b14cd69d67cc584bf5f3519f70
 * @jenesis.pin groovy/module/build.jenesis.plugin.groovy 0.2.0 SHA-256/25dd8f40a87d19e274e223d93b3c46b4752c7b009ff570fa63a02cb0dea2b00d
 * @jenesis.pin groovy:tool/maven/com.github.javaparser/javaparser-core 3.28.2 SHA-256/b5499a3b1c40b16c0671fabe478c9aafeab38160c6fde74a6c13f42d86716ecd
 * @jenesis.pin groovy:tool/maven/com.github.javaparser/javaparser-core/pom 3.28.2 SHA-256/87d2b333dfec96896d5a49e80a95b92ef572f610e848276b45eaa061e05b4132
 * @jenesis.pin groovy:tool/maven/com.github.javaparser/javaparser-parent/pom 3.28.2 SHA-256/0358ce75bca1a54c141e16b7f1621e973fa177ddf64fe02b0f38df23ad400543
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-bom/pom 6.0.0 SHA-256/b87b5316806decf3aa00d1f80dc38180f72487066b8ea198bbf6c80727d48245
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-bom/pom 6.0.0-alpha-1 SHA-256/d8fad415eeaf4377a6098506d29b345134f117a7d12187e2d6b1278ea423b04d
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-docgenerator 6.0.0 SHA-256/38e82ca37456f5eaad576ff9d12272eafa780d65a7f9b6d07d3883461509e838
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-docgenerator/pom 6.0.0 SHA-256/86ea5ddd6c0060e03ad95f0d154ce0b7628eeb83cf48737c875658c01ad70c82
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-groovydoc 6.0.0 SHA-256/ed8639a52b400c45ce67918721dc7b2bd5e080819e14f304b99ef191c1759c32
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-groovydoc/pom 6.0.0 SHA-256/853fd46a0a1d6f480b1f410b26fec104b4052923645ee522d5759a1bf301b334
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-templates 6.0.0 SHA-256/c570490d7a8b96d5bf97a384a3ab71583c030102c938258d8785955662fa452d
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-templates/pom 6.0.0 SHA-256/0f0357a53cadb6cb45f7f6927e90b4ee97c4533d1bb3f8a41c7cfe3f4a20c3d8
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-xml 6.0.0 SHA-256/64b8e4788280ed88be1f1e439a71100e3d391ed446ced5b20b8730a3618c8c57
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-xml/pom 6.0.0 SHA-256/6d2390072647f1a6f87cbe9dad198c69ba8ce072b3c8807791e18e2e3415d3ff
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy/pom 6.0.0-alpha-1 SHA-256/bc2f56284d37d6d5e634522dcc6d1431a5e1a73377fb70109a093533c5b20986
 * @jenesis.pin groovy:tool/maven/org.commonmark/commonmark 0.30.0 SHA-256/090f0a758ac2f4f51aa86dff9dff0fdf7403666d0efd29f59886dc8e8b1eac53
 * @jenesis.pin groovy:tool/maven/org.commonmark/commonmark-parent/pom 0.30.0 SHA-256/284486c3a3eb0f42e03526f3f8aceff006afc84c90d76a2c21d1e2bf6962f070
 * @jenesis.pin groovy:tool/maven/org.commonmark/commonmark/pom 0.30.0 SHA-256/cf7554de7f0dec39cf9d7b3642ddefff6a75e087b0d0818515c4a635ccf2c05a
 * @jenesis.pin groovy:tool/maven/org.junit/junit-bom/pom 5.14.4 SHA-256/5706e8f29a0a07f56efbbea4a0670793414194bb8d24d8143ba1e787a2f32856
 * @jenesis.pin maven/org.apache.groovy/groovy-bom/pom 5.0.6 SHA-256/ad5d4b53e161e6537afe57dc96a6608cb36896cfb733caa997e9be28d59ab54e
 * @jenesis.pin maven/org.apache.groovy/groovy/pom 5.0.6 SHA-256/17eed34dc0dc57b902b38e19136394e72431c515d0aaed0e3fabd95df77b36a8
 * @jenesis.pin org.apache.groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 * @jenesis.pin org.apache.groovy/groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 */
module sample {
    requires org.apache.groovy;
    exports sample;
}
