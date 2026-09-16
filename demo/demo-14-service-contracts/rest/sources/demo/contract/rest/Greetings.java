package demo.contract.rest;

import demo.greeting.ApiClient;
import demo.greeting.DefaultApi;
import demo.greeting.model.Greeting;

public class Greetings {

    public static DefaultApi api(String host) {
        ApiClient client = new ApiClient();
        client.updateBaseUri(host);
        return new DefaultApi(client);
    }

    public static String summarize(Greeting greeting) {
        return greeting.getText() + " (" + greeting.getCount() + ")";
    }
}
