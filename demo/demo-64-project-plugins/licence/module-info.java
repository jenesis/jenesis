module demo.licence {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.licence.LicenceFactory;
}
