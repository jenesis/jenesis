package demo.natives.app;

import demo.natives.text.NativeText;

public class Application {

    public static void main(String[] args) {
        System.out.println("native access: demo.natives.text=" + NativeText.granted()
                + ", demo.natives.app=" + Application.class.getModule().isNativeAccessEnabled());
        String text = args.length == 0 ? "Hello, native world!" : String.join(" ", args);
        System.out.println("\"" + text + "\" is " + NativeText.length(text) + " bytes long, as C's strlen counts it");
    }
}
