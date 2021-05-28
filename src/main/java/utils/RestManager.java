package utils;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;

public class RestManager {
    public static HttpClient client = HttpClient.newBuilder().executor(Executors.newCachedThreadPool()).build();


    private RestManager() {

    }

    public static void resetClient() {
        client = HttpClient.newBuilder().executor(Executors.newCachedThreadPool()).build();
    }
}
