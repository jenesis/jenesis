module demo.plugin {
    requires build.jenesis;
    requires org.json;
    provides build.jenesis.BuildModuleFactory with demo.plugin.GreetingFactory;
}
