module demo.jni {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.jni.JniFactory;
}
