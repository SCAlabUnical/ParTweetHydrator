package lightweightVersion;


import java.io.File;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public  record WrappedCompletableFuture(WrappedHTTPRequest request,CompletableFuture<HttpResponse<String>> future,File output,int packetNumber){

}
