package key;


import java.net.http.HttpRequest;

public final class BearerToken extends AbstractKey {
    private final String bearer;

    public BearerToken(String b) throws AbstractKey.UnusableKeyException {
        bearer = b;
        validateKey();
    }

    @Override
    protected HttpRequest effectiveSigning(HttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri());
        builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        return builder.setHeader("Authorization", this.bearer).build();
    }

    @Override
    public String toString() {
        return bearer;
    }

}
