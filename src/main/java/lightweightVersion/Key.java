package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import twitter4j.HttpParameter;
import twitter4j.RequestMethod;
import twitter4j.auth.Authorization;
import twitter4j.auth.OAuthAuthorization;
import twitter4j.conf.ConfigurationBuilder;
import utils.utils;
import utils.RestManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;


public final class Key implements Comparable<Key> {
    private static Logger logger = LogManager.getLogger(Key.class.getName());

    public final static class UnusableKeyException extends Exception {
        public UnusableKeyException(String msg) {
            super(msg);
        }
    }

    private enum KeyType {BEARER, OAUTH1}

    ;

    private final static AtomicInteger instanceTracker = new AtomicInteger(0);
    private final int id = instanceTracker.incrementAndGet();
    private final KeyType type;
    private final int requestLimitPerWindow;
    private int usesLeft = 0;
    private final String bearer;
    private final String[] oauthParams;
    private final Authorization authConf;
    private static final Map<String, RequestMethod> map = Map.of("GET", RequestMethod.GET, "PUT", RequestMethod.PUT,
            "POST", RequestMethod.POST, "DELETE", RequestMethod.DELETE, "HEAD", RequestMethod.HEAD);
    private long epochResetTime = Instant.now().getEpochSecond() + 15 * 60;
    private final int WINDOW_RESET_TIME = 15;
    private long firstUseTimestamp = 0L;


    public int getId() {
        return id;
    }


    public boolean isUsable() {
        if (epochResetTime < Instant.now().getEpochSecond()) {
            usesLeft = requestLimitPerWindow;
            firstUseTimestamp = 0L;
        }
        return usesLeft > 0;
    }

    public long getResetTime() {
        return epochResetTime - Instant.now().getEpochSecond();
    }

    private Key() {
        type = null;
        bearer = null;
        authConf = null;
        oauthParams = null;
        requestLimitPerWindow = -1;
    }

    public Key(String bearerToken) throws UnusableKeyException {
        this.oauthParams = null;
        this.authConf = null;
        this.type = KeyType.BEARER;
        requestLimitPerWindow = 300;
        this.bearer = bearerToken;
        this.validateKey();
    }

    public Key(String[] oauth1Params) throws UnusableKeyException {
        bearer = null;
        if (oauth1Params.length != 4) throw new IllegalArgumentException();
        this.type = KeyType.OAUTH1;
        requestLimitPerWindow = 900;
        this.oauthParams = oauth1Params;
        this.authConf = new OAuthAuthorization(new ConfigurationBuilder().setOAuthConsumerKey(
                this.oauthParams[0]
        ).setOAuthConsumerSecret(this.oauthParams[1]).setOAuthAccessToken(this.oauthParams[2])
                .setOAuthAccessTokenSecret(this.oauthParams[3]).build());
        this.validateKey();
    }


    @Override
    public String toString() {
        return "Key{" +
                "id=" + id +
                ", type=" + type +
                ", usesLeft=" + usesLeft +
                ", epochResetTime=" + (epochResetTime - Instant.now().getEpochSecond()) +
                '}';

    }

    public HttpRequest signRequest(HttpRequest request) throws UnusableKeyException, MalformedURLException {
        if (this.usesLeft <= 0)
            throw new IllegalStateException("Key has no uses left,wait for the reset " + usesLeft + " " + (epochResetTime - Instant.now().getEpochSecond()));
        HttpRequest req = signRequestPrivate(request);
        if (firstUseTimestamp == 0) {
            firstUseTimestamp = Instant.now().getEpochSecond();
            epochResetTime = firstUseTimestamp + WINDOW_RESET_TIME * 60;
        }
        usesLeft--;
        return req;
    }

    /* proxied for integrity checks*/
    private HttpRequest signRequestPrivate(HttpRequest request) throws MalformedURLException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri());
        builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        if (this.type == KeyType.BEARER)
            return builder.setHeader("Authorization", this.bearer).build();
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


    public int getUsesLeft() {
        return usesLeft;
    }


    private void validateKey() throws UnusableKeyException {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(utils.API_ENDPOINTS[0] + utils.KEY_VALIDATION[0] + "?resources=statuses")).build();
            logger.info("Validating key");
            HttpResponse<String> response = RestManager.client.send(this.signRequestPrivate(request), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject jsonObject = new JSONObject(response.body());
                JSONObject statusEndpoint = (JSONObject) ((JSONObject) ((JSONObject) jsonObject.get("resources")).get("statuses")).get("/statuses/lookup");
                usesLeft = Integer.parseInt(statusEndpoint.get("remaining").toString());
                epochResetTime = Long.parseLong(statusEndpoint.get("reset").toString());
            } else {
                throw new RuntimeException("Bad http response,check if the tokens supplied are valid " + response.statusCode());
            }
        } catch (IOException | InterruptedException  e) {
            logger.fatal(e.getMessage());;
            throw new RuntimeException("Unable to validate key,check your connection");
        } catch (RuntimeException e) {
            throw new UnusableKeyException(e.getMessage());
        }
    }

    @Override
    public int compareTo(Key that) {
        return Long.compare(this.epochResetTime, that.epochResetTime);
    }

}
