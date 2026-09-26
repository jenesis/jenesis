module demo.checksums {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.checksums.ChecksumsModule;
}
