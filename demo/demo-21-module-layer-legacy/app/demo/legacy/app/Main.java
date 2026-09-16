package demo.legacy.app;

import demo.legacy.library.Library;

public class Main {

    public static void main(String[] args) {
        System.out.println("the application requires one module, and knows no commons-anything");
        System.out.println("the library's private tree: " + new Library().describe());
    }
}
