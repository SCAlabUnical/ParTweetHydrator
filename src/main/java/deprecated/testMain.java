package deprecated;


import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class testMain {
    public static void main(String... args) throws Exception {
        HttpClient client = HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("111.90.179.74",8080))).build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://stackoverflow.com/")) .build();
        HttpResponse response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
    }
}
