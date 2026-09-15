package demo.layers.app;

import com.fasterxml.jackson.core.json.PackageVersion;
import demo.layers.api.Report;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        System.out.println("the application sees jackson-core "
                + PackageVersion.VERSION
                + ", loaded by "
                + PackageVersion.class.getClassLoader());

        ModuleLayer parent = Main.class.getModule().getLayer();
        ModuleFinder finder = ModuleFinder.of(Path.of(System.getProperty("jenesis.layer.render")));
        Set<String> roots = finder.findAll().stream()
                .map(reference -> reference.descriptor().name())
                .collect(Collectors.toUnmodifiableSet());
        Configuration configuration = parent.configuration()
                .resolveAndBind(finder, ModuleFinder.of(), roots);
        ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(
                configuration, List.of(parent), Main.class.getClassLoader()).layer();

        for (Report report : ServiceLoader.load(layer, Report.class)) {
            System.out.println(report.render());
        }
        System.out.println("the seam module is the parent's: "
                + (layer.findModule("demo.layers.api").orElseThrow()
                        == parent.findModule("demo.layers.api").orElseThrow()));
    }
}
