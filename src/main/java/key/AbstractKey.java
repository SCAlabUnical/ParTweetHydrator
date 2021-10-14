package key;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import utils.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractKey implements Comparable<AbstractKey> {
    static Logger logger = LogManager.getLogger(AbstractKey.class);
    private final static AtomicInteger instanceTracker = new AtomicInteger(0);
    private final static List<AbstractKey> instancesSet = Collections.synchronizedList(new ArrayList<>());
    private final int id = instanceTracker.incrementAndGet(), TOLERANCE = 30;
    private long epochResetTime = -1L, firstUseTimestamp = 0L;

    protected int requestsLimitPerWindow, usesLeft = 0;
    final int WINDOW_RESET_TIME = 15;
    private final static HttpClient keyChecker = HttpClient.newHttpClient();
    private final static String KEY_ENDPOINT_CHECK = "?resources=statuses";
    private int signedRequests = 0;
    //needed to make up for Twitter's unreliable API during the key setup....
    protected boolean isUsableHint = true;
    private long isUsableTimestamp = Instant.now().getEpochSecond(), tryAgain;


    static {
        Runnable backGroundLogger = () -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (Exception e) {

                }
                synchronized (instancesSet) {
                    for (AbstractKey abstractKey : instancesSet)
                        if (abstractKey.signedRequests > 0)
                            logger.trace("[Key °" + abstractKey.id + " " + abstractKey + "\n" + abstractKey.signedRequests + ", reset in :  " + abstractKey.getResetTime() + "]");

                }
            }
        };
        Executors.newSingleThreadExecutor().execute(backGroundLogger);
    }

    public boolean getUsableHint() {
        return isUsableHint;
    }

    public long getResetTime() {
        return Math.max(epochResetTime, tryAgain) - Instant.now().getEpochSecond();
    }

    public final static class UnusableKeyException extends Exception {
        public UnusableKeyException(String msg) {
            super(msg);
        }
    }

    public int compareTo(AbstractKey otherKey) {
        return Long.compare(this.epochResetTime, otherKey.epochResetTime);
    }

    public int getId() {
        return id;
    }

    protected abstract HttpRequest effectiveSigning(HttpRequest request) throws MalformedURLException;

    public int getUsesLeft() {
        return usesLeft;
    }

    public boolean isUsable() {
        if (epochResetTime > 0 && epochResetTime < Instant.now().getEpochSecond()) {
            if (!isUsableHint && (Instant.now().getEpochSecond() - isUsableTimestamp) < 2 * 60)
                isUsableHint = true;
            usesLeft = requestsLimitPerWindow;
            firstUseTimestamp = 0L;
        }
        return isUsableHint && usesLeft > 0;
    }


    public void setUsableHint(boolean usableHint) {
        this.isUsableHint = false;
        isUsableTimestamp = Instant.now().getEpochSecond();
        tryAgain = Instant.now().getEpochSecond() + 2 * 60;
    }

    protected void validateKey() throws AbstractKey.UnusableKeyException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(utils.API_ENDPOINTS[0] + utils.KEY_VALIDATION[0] + KEY_ENDPOINT_CHECK));
            HttpResponse<String> response = keyChecker.send(this.effectiveSigning(requestBuilder.build()), HttpResponse.BodyHandlers.ofString());
            System.out.println(response);
            if (response.statusCode() == 200) {
                JSONObject jsonObject = new JSONObject(response.body());
                JSONObject statusEndpoint = (JSONObject) ((JSONObject) ((JSONObject) jsonObject.get("resources")).get("statuses")).get("/statuses/lookup");
                requestsLimitPerWindow = Integer.parseInt(statusEndpoint.get("limit").toString());
                usesLeft = Integer.parseInt(statusEndpoint.get("remaining").toString());
                epochResetTime = Long.parseLong(statusEndpoint.get("reset").toString());
                System.out.println("Key n° " + id + " uses left : " + usesLeft);
            } else {
                System.out.println("Key : " + this + " invalid");
                throw new AbstractKey.UnusableKeyException("Bad http response,check if the tokens supplied are valid " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Unable to validate key,check your connection");
        }
        instancesSet.add(this);
    }

    public HttpRequest signRequest(HttpRequest request) throws AbstractKey.UnusableKeyException, MalformedURLException {
        if (usesLeft <= 0)
            throw new IllegalStateException("Key has no uses left,wait for the reset " + usesLeft + " " + (epochResetTime - Instant.now().getEpochSecond()));
        HttpRequest signedRequest = effectiveSigning(request);
        signedRequests++;
        if (firstUseTimestamp == 0L) {
            firstUseTimestamp = Instant.now().getEpochSecond();
            epochResetTime = firstUseTimestamp + WINDOW_RESET_TIME * 60 + TOLERANCE;
        }
        usesLeft--;
        return signedRequest;
    }
}
