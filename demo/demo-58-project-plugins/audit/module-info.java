module demo.audit {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.audit.AuditModule;
}
