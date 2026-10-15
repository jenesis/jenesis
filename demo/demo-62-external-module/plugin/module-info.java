/**
 * @jenesis.pin build.jenesis 0.16.0 SHA-256/bf8d90a8e3251fcb2519e11793ab87a68f37419f12a6b3275f9dd1d263149c98
 * @jenesis.pin build.jenesis/build.jenesis 0.16.0 SHA-256/bf8d90a8e3251fcb2519e11793ab87a68f37419f12a6b3275f9dd1d263149c98
 * @jenesis.pin org.json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 * @jenesis.pin org.json/json 20260719 SHA-256/c243f45f9590c12694a4142ed3f07fc70dfb71e4daebd05ae234bf92a2da92a6
 */
module demo.plugin {
    requires build.jenesis;
    requires org.json;
    provides build.jenesis.BuildModuleFactory with demo.plugin.GreetingFactory;
}
