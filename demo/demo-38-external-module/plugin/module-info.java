/**
 * @jenesis.pin build.jenesis 0.9.4
 * @jenesis.pin build.jenesis/build.jenesis 0.9.4 SHA-256/42cfb3f4f4f05d0f7362cf97dadbad7b94cfa356df57c5602c0c7d01bf0f5d78
 * @jenesis.pin org.json 20260719
 * @jenesis.pin org.json/json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 */
module demo.plugin {
    requires build.jenesis;
    requires org.json;
    provides build.jenesis.BuildExecutorModule with demo.plugin.SubstitutionModule;
}
