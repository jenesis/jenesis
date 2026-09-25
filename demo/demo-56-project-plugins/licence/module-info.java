module demo.licence {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.licence.LicenceModule;
}
