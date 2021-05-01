package lightweightVersion;


import java.net.http.HttpRequest;

public record WrappedHTTPRequest(HttpRequest request, int reqTarget, int reqNumber, int fileInput){}

