package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.step.Jar;
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Versions;

public record JavaToolchainModule(BuildExecutorModule generator,
                                  BuildExecutorModule compiler,
                                  BuildExecutorModule transformer,
                                  BuildExecutorModule validator,
                                  BuildExecutorModule archiver,
                                  BuildExecutorModule classpath) implements BuildExecutorModule {

    public static final String ARTIFACTS = "artifacts",
            CLASSES = "classes",
            TRANSFORM = "transform",
            VALIDATE = "validate",
            CLASSPATH = "classpath";
    private static final String GENERATED = "generated", COMPILED = "compiled";

    public JavaToolchainModule() {
        this(null,
                new Javac(ProcessHandler.Factory.of()).asModule("javac"),
                null,
                null,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.CLASSES).asModule("jar"),
                null);
    }

    public JavaToolchainModule generator(BuildExecutorModule generator) {
        return new JavaToolchainModule(generator, compiler, transformer, validator, archiver, classpath);
    }

    public JavaToolchainModule compiler(BuildExecutorModule compiler) {
        return new JavaToolchainModule(generator, compiler, transformer, validator, archiver, classpath);
    }

    public JavaToolchainModule archiver(BuildExecutorModule archiver) {
        return new JavaToolchainModule(generator, compiler, transformer, validator, archiver, classpath);
    }

    public JavaToolchainModule transformer(BuildExecutorModule transformer) {
        return new JavaToolchainModule(generator, compiler, transformer, validator, archiver, classpath);
    }

    public JavaToolchainModule validator(BuildExecutorModule validator) {
        return new JavaToolchainModule(generator, compiler, transformer, validator, archiver, classpath);
    }

    public JavaToolchainModule classpath(BuildExecutorModule classpath) {
        return new JavaToolchainModule(generator, compiler, transformer, validator, archiver, classpath);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (generator == null) {
            buildExecutor.addModule(COMPILED, compiler, inherited.sequencedKeySet());
        } else {
            buildExecutor.addModule(GENERATED, generator, inherited.sequencedKeySet());
            buildExecutor.addModule(COMPILED,
                    compiler,
                    Stream.concat(inherited.sequencedKeySet().stream(), Stream.of(GENERATED)));
        }
        buildExecutor.addStep(CLASSES, new Versions(), Stream.concat(
                Stream.of(COMPILED),
                inherited.sequencedKeySet().stream()));
        String classes;
        if (transformer == null) {
            classes = CLASSES;
        } else {
            buildExecutor.addModule(TRANSFORM, transformer, Stream.concat(
                    Stream.of(CLASSES),
                    inherited.sequencedKeySet().stream()));
            classes = TRANSFORM;
        }
        if (validator != null) {
            buildExecutor.addModule(VALIDATE, validator, Stream.concat(
                    Stream.of(classes),
                    inherited.sequencedKeySet().stream()));
        }
        if (classpath != null) {
            buildExecutor.addModule(CLASSPATH, classpath, Stream.concat(
                    Stream.of(classes),
                    inherited.sequencedKeySet().stream()));
        }
        if (archiver != null) {
            buildExecutor.addModule(ARTIFACTS, archiver, Stream.of(
                    Stream.of(classes),
                    classpath == null ? Stream.<String>empty() : Stream.of(CLASSPATH),
                    inherited.sequencedKeySet().stream()).flatMap(Function.identity()));
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return switch (path) {
            case COMPILED -> Optional.empty();
            case TRANSFORM -> Optional.of(CLASSES);
            case CLASSES -> transformer == null ? Optional.of(CLASSES) : Optional.empty();
            default -> Optional.of(path);
        };
    }
}
