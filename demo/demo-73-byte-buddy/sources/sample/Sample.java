package sample;

import java.util.function.Supplier;

public class Sample {

    public static void main(String[] args) throws ReflectiveOperationException {
        Supplier<?> greeting = (Supplier<?>) Class.forName("sample.Greeting").getConstructor().newInstance();
        System.out.println(greeting.get());
    }
}
