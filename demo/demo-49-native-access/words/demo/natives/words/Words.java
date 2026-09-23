package demo.natives.words;

import demo.natives.text.NativeText;

public final class Words {

    private Words() {
    }

    public static String describe(String text) {
        return "\"" + text + "\" is " + NativeText.length(text) + " bytes long, as C's strlen counts it";
    }
}
