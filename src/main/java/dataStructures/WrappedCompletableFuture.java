package dataStructures;


import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public  record WrappedCompletableFuture(WrappedHTTPRequest request, CompletableFuture<HttpResponse<String>> futureResponse, int fileIndex, int packetNumber){

}
