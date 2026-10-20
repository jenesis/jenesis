/**
 * @jenesis.pin build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin build.jenesis/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin org.json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 * @jenesis.pin org.json/json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 */
module demo.plugin {
    requires build.jenesis;
    requires org.json;
    provides build.jenesis.BuildModuleFactory with demo.plugin.GreetingFactory;
}
