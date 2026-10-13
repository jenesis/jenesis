module demo.zip {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.zip.ZipModule;
}
