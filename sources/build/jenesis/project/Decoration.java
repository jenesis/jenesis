package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;

public record Decoration(String name,
                         UnaryOperator<ProjectModuleDescriptor> descriptor,
                         Function<ProjectModuleDescriptor, BuildExecutorModule> before,
                         Function<ProjectModuleDescriptor, BuildExecutorModule> after) {

    public Decoration {
        if (name == null || name.isBlank() || name.contains("/")) {
            throw new IllegalArgumentException("A decoration nests the decorated build under a name of its own,"
                    + " a single segment without a slash, not: " + name);
        }
        if (descriptor == null) {
            throw new IllegalArgumentException("A decoration needs a descriptor operator,"
                    + " UnaryOperator.identity() to hand the decorated build the descriptor unchanged");
        }
    }

    public Decoration(String name) {
        this(name, UnaryOperator.identity(), null, null);
    }

    public Decoration name(String name) {
        return new Decoration(name, descriptor, before, after);
    }

    public Decoration descriptor(UnaryOperator<ProjectModuleDescriptor> descriptor) {
        return new Decoration(name, descriptor, before, after);
    }

    public Decoration before(Function<ProjectModuleDescriptor, BuildExecutorModule> before) {
        return new Decoration(name, descriptor, before, after);
    }

    public Decoration after(Function<ProjectModuleDescriptor, BuildExecutorModule> after) {
        return new Decoration(name, descriptor, before, after);
    }

    public MultiProjectAssembler<ProjectModuleDescriptor> around(MultiProjectAssembler<? super ProjectModuleDescriptor> base) {
        return (original, repositories, resolvers) -> {
            ProjectModuleDescriptor decorated = descriptor.apply(original);
            SequencedSet<String> siblings = Stream.of(decorated.sources(),
                            decorated.resources(),
                            decorated.manifests(),
                            decorated.coordinates(),
                            decorated.artifacts(),
                            decorated.spdx(),
                            decorated.content())
                    .flatMap(SequencedSet::stream)
                    .filter(reference -> !reference.startsWith(BuildExecutorModule.PREVIOUS))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            siblings.removeIf(reference -> siblings.stream().anyMatch(root -> reference.startsWith(root + "/")));
            return base.apply(decorated.toInherited(), repositories, resolvers).mapBuild(build -> (sub, inherited) -> {
                if (before != null) {
                    BuildExecutorModule module = before.apply(original);
                    if (module != null) {
                        module.accept(sub, inherited);
                    }
                }
                sub.addModule(name, build, Stream.concat(inherited.sequencedKeySet().stream(), siblings.stream()));
                if (after != null) {
                    BuildExecutorModule module = after.apply(original);
                    if (module != null) {
                        module.accept(sub, inherited);
                    }
                }
            });
        };
    }
}
