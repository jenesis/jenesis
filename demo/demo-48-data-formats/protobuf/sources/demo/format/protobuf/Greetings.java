package demo.format.protobuf;

import demo.greeting.GreeterGrpc;
import demo.greeting.GreetingProto.Greeting;
import io.grpc.stub.StreamObserver;

public class Greetings {

    public static Greeting greeting(String name, int count) {
        return Greeting.newBuilder().setText("Hello, " + name).setCount(count).build();
    }

    public static GreeterGrpc.GreeterImplBase service() {
        return new GreeterGrpc.GreeterImplBase() {
            @Override
            public void greet(Greeting request, StreamObserver<Greeting> observer) {
                observer.onNext(greeting(request.getText(), request.getCount()));
                observer.onCompleted();
            }
        };
    }
}
