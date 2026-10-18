module demo.audit {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.audit.AuditFactory;
}
