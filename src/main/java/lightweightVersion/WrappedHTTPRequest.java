package lightweightVersion;

import java.io.File;
import java.net.http.HttpRequest;

public record WrappedHTTPRequest(HttpRequest request, int reqTarget, int reqNumber, File input){}

