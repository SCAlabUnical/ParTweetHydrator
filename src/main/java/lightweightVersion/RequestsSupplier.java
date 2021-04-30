package lightweightVersion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.utils;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RequestsSupplier extends Thread {
    private static final Logger logger = LogManager.getLogger(RequestsSupplier.class.getName());
    private static final Logger tracker = LogManager.getLogger("hydrator_tracker");
    private final Buffer<WrappedHTTPRequest> buffer;
    private int shift = 0;
    private int key = 0;
    private int requestsPerSingleFile = 0;
    private final int numberOfKeys;
    private Long[] timers;
    private final Key[] keys;
    private final Buffer<WorkKit> worksetQueue = new Buffer<>(5);
    private final Stack<WrappedHTTPRequest> requestsToRepeat = new Stack<>();

    public RequestsSupplier(Key[] keys, Buffer<WrappedHTTPRequest> buffer) {
        if (keys.length == 0) throw new IllegalArgumentException("0 keys supplied");
        this.buffer = buffer;
        this.keys = keys;
        numberOfKeys = keys.length;
        timers = new Long[numberOfKeys];
    }

    public void addRequestToRepeat(WrappedHTTPRequest request) {
        requestsToRepeat.push(request);
    }

    void workSetRefresh(WorkKit kit) throws InterruptedException {
        this.worksetQueue.put(kit);
    }

    private boolean nextKey() {
        key = (key + 1) % numberOfKeys;
        shift++;
        return shift < numberOfKeys;
    }


    private void waitForFirstAvailableKey() throws InterruptedException, Key.UnusableKeyException, IOException {
        Arrays.sort(keys, Key::compareTo);
        long timeToWait = keys[0].getResetTime();
        if (timeToWait > 0) {
            logger.warn("Waiting for a key to reset... ETA : " + (timeToWait) + " seconds till " + new Date(System.currentTimeMillis() + timeToWait * 1000));
            for (Key x : keys)
                logger.trace(x.toString());
            TimeUnit.SECONDS.sleep(timeToWait + 2);
        }
        key = shift = 0;
        logger.info("Done waiting");
    }

    public void run() {
        int i = 0, up, reqTarget = 0, total = 0;
        URI requestURI = null;
        WrappedHTTPRequest request;
        WorkKit kit;
        List<Long> idWorkset;
        File curr = null;
        WrappedHTTPRequest old;
        while (!this.isInterrupted()) {
            try {
                kit = worksetQueue.get();
                idWorkset = kit.ids();
                curr = kit.destination();
                reqTarget = (int) Math.ceil((double) idWorkset.size() / 100);
                while (i < idWorkset.size()) {
                    if (!keys[key].isUsable()) {
                        if (!nextKey())
                            waitForFirstAvailableKey();
                        continue;
                    }
                    up = Math.min(i + 100, idWorkset.size());
                    HttpRequest.Builder base = HttpRequest.newBuilder().uri((requestURI = utils.generateQuery(idWorkset.subList(i, up), 1)));
                    if (requestsToRepeat.empty()) {
                        request = new WrappedHTTPRequest(keys[key].signRequest(base, requestURI, "GET"), reqTarget, requestsPerSingleFile++, curr);
                        i += (up - i);
                    } else {
                        old = requestsToRepeat.pop();
                        URI oldUri = old.request().uri();
                        request = new WrappedHTTPRequest(keys[key].signRequest(HttpRequest.newBuilder(oldUri), oldUri, "GET"), old.reqTarget(), old.reqNumber(),old.input());
                        logger.info("[Received request to repeat][File " + request.input().getName() + " ][Request n° " + old.reqNumber() + "/" + request.reqTarget() + "]");
                    }
                    logger.info("[Request n° " + (requestsPerSingleFile) + " created ][Total : " + (total++) + "]" + "[Bounds " + i + " " + up + "]" + "[Valid for " + keys[key].getUsesLeft() + "] [Key : " + keys[key].getId() + "] [Shift " + shift + "]");
                    buffer.put(request);
                }
            } catch (InterruptedException e) {
                logger.fatal(e.getMessage());
                break;
            } catch (MalformedURLException e) {
                logger.fatal(requestURI.toString());
            } catch (IOException | Key.UnusableKeyException e) {
                logger.fatal(e.getMessage());
            }
            logger.trace("Created all the requests for file : " + curr.getName());
            requestsPerSingleFile = i = 0;
        }
        logger.trace("Hydrator terminating - Exit code 0");
    }
}
