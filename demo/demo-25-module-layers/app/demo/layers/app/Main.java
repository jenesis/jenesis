package demo.layers.app;

import com.fasterxml.jackson.core.json.PackageVersion;
import demo.layers.library.Library;

public class Main {

    public static void main(String[] args) {
        System.out.println("the application's jackson-core " + PackageVersion.VERSION
                + ", loaded by " + PackageVersion.class.getClassLoader());
        System.out.println(new Library().report());
    }
}
