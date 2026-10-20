/**
 * @jenesis.pin build.jenesis 0.17.0 SHA-256/a875ab473238e7191332698fab64db7b4a4d7a484da6beb313a4a4beb10c60fc
 * @jenesis.pin build.jenesis/build.jenesis 0.17.0 SHA-256/a875ab473238e7191332698fab64db7b4a4d7a484da6beb313a4a4beb10c60fc
 * @jenesis.pin org.json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 * @jenesis.pin org.json/json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 */
module demo.plugin {
    requires build.jenesis;
    requires org.json;
    provides build.jenesis.BuildModuleFactory with demo.plugin.GreetingFactory;
}
