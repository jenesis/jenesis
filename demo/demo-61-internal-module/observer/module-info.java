module demo.observer {
    requires build.jenesis;
    provides build.jenesis.ObservabilityFactory with demo.observer.LoadedFactory;
}
