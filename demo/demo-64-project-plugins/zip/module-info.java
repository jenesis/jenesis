module demo.zip {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.zip.ZipFactory;
}
