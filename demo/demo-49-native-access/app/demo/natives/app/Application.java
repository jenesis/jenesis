package demo.natives.app;

import demo.natives.text.NativeText;
import demo.natives.words.Words;

public class Application {

    public static void main(String[] args) {
        System.out.println("native access: demo.natives.text=" + NativeText.granted()
                + ", demo.natives.app=" + Application.class.getModule().isNativeAccessEnabled());
        System.out.println(Words.describe(args.length == 0 ? "Hello, native world!" : String.join(" ", args)));
    }
}
