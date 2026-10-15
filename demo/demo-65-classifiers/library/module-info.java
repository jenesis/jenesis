module demo.library {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.library.LibraryFactory;
}
