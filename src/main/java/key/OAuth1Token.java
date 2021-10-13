package key;


import twitter4j.HttpParameter;
import twitter4j.RequestMethod;
import twitter4j.auth.Authorization;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.conf.ConfigurationBuilder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.StringTokenizer;

public final class OAuth1Token extends AbstractKey{

    private static final Map<String, RequestMethod> map = Map.of("GET", RequestMethod.GET, "PUT", RequestMethod.PUT,
            "POST", RequestMethod.POST, "DELETE", RequestMethod.DELETE, "HEAD", RequestMethod.HEAD);
    private final String[] oauthParams;
    private final Authorization authConf;

    public OAuth1Token(String[] oauth1Params ) throws AbstractKey.UnusableKeyException {
        if (oauth1Params.length != 4) throw new IllegalArgumentException();
        this.oauthParams = oauth1Params;
        this.authConf = new OAuthAuthorization(new ConfigurationBuilder().setOAuthConsumerKey(
                        this.oauthParams[0]
                ).setOAuthConsumerSecret(this.oauthParams[1]).setOAuthAccessToken(this.oauthParams[2])
                .setOAuthAccessTokenSecret(this.oauthParams[3]).build());
       validateKey();
    }

    protected HttpRequest effectiveSigning(HttpRequest request) throws MalformedURLException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri());
        builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        URI uri = request.uri();
        String query = uri.getQuery() == null ? "" : (uri.getQuery() + "&");
        StringTokenizer st2, st = new StringTokenizer(query, "&");
        HttpParameter[] params = new HttpParameter[st.countTokens()];
        int index = 0;
        String param, first;
        while (st.hasMoreTokens()) {
            first = st.nextToken();
            st2 = new StringTokenizer(first, "=");
            param = st2.nextToken();
            query = first.substring(first.indexOf("=") + 1);
            params[index++] = new HttpParameter(param, query);
        }
        URL urlT = uri.toURL();
        String url = urlT.getProtocol() + "://" + urlT.getHost() + urlT.getPath();
        twitter4j.HttpRequest tw4jreq = new twitter4j.HttpRequest(map.get(request.method().toUpperCase()),
                url, params.length == 0 ? null : params, this.authConf, null);
        String authHeader = this.authConf.getAuthorizationHeader(tw4jreq);
        return builder.setHeader("Authorization", authHeader).build();
    }

    public String toString(){
        return Arrays.toString(oauthParams);
    }

}
