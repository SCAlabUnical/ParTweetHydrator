package lightweightVersion;


import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public  record WrappedCompletableFuture(WrappedHTTPRequest request,CompletableFuture<HttpResponse<String>> future,int fileIndex,int packetNumber){

}
