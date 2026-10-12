package sample.app;

import sample.natives.Natives;

public class App {

    public static void main(String[] args) throws Exception {
        System.out.println("Running on " + Natives.variant());
    }
}
