module demo.jni {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.jni.JniModule;
}
