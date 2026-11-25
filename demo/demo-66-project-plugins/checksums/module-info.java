module demo.checksums {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.checksums.ChecksumsFactory;
}
