package hydrator;

import dataStructures.Buffer;
import dataStructures.WorkKit;
import dataStructures.WrappedHTTPRequest;
import key.AbstractKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class RequestsSupplier implements Runnable {
    private static final Logger logger = LogManager.getLogger(RequestsSupplier.class.getName());
    private int shift = 0, key = 0, requestsPerSingleFile = 0;
    private final int numberOfKeys;
    private long ids = 0;
    private Long[] timers;
    private final AbstractKey[] keys;
    private final Buffer<WorkKit> worksetQueue = new Buffer<>(10, "idFiles");
    private final Stack<WrappedHTTPRequest> requestsToRepeat = new Stack<>();
    private final RequestExecutor executor;
    private int filesToWorkOn;

    public RequestsSupplier(AbstractKey[] keys, RequestExecutor executor) {
        this.executor = executor;
        if (keys.length == 0) throw new IllegalArgumentException("0 keys supplied");
        this.keys = keys;
        numberOfKeys = keys.length;
        timers = new Long[numberOfKeys];
    }


    public long getTotalTweets() {
        return ids;
    }

    public void addRequestToRepeat(WrappedHTTPRequest request) {
        requestsToRepeat.push(request);
    }

    public void workSetRefresh(WorkKit kit) throws InterruptedException {
        this.worksetQueue.put(kit);
    }

    private boolean nextKey() {
        key = (key + 1) % numberOfKeys;
        shift++;
        return shift < numberOfKeys;
    }

    private void waitForFirstAvailableKey() throws InterruptedException, AbstractKey.UnusableKeyException, IOException {
        Arrays.sort(keys, AbstractKey::compareTo);
        long timeToWait = keys[0].getResetTime();
        if (timeToWait > 0) {
            logger.warn("Waiting for a key to reset... ETA : " + (timeToWait) + " seconds till " + new Date(System.currentTimeMillis() + timeToWait * 1000));
            Arrays.stream(keys).forEach(key -> logger.trace(key.toString()));
            GraphicModule.INSTANCE.statusPanel.waitingForKey();
            TimeUnit.SECONDS.sleep(timeToWait + 2);
        }
        key = shift = 0;
        GraphicModule.INSTANCE.statusPanel.start();
        logger.info("Done waiting");
    }

    public void setFilesToWorkOn(int f) {
        filesToWorkOn = f;
    }

    public void run() {
        int i = 0, up, reqTarget = 0, currentFile = 0,file=-1;
        long total = 0;
        URI requestURI = null;
        WorkKit kit;
        List<String> idWorkset;
        WrappedHTTPRequest old,request;
        while (currentFile != filesToWorkOn) {
            try {
                kit = worksetQueue.get();
                if (file != kit.fileIndex()) {
                    Hydrator.INSTANCE.setStartTime(kit.fileIndex());
                    ids += kit.ids().size();
                }
                file = kit.fileIndex();
                idWorkset = kit.ids();
                reqTarget = (int) Math.ceil((double) idWorkset.size() / 100);
                while (i < idWorkset.size()) {
                    if (!keys[key].isUsable()) {
                        if (!nextKey())
                            waitForFirstAvailableKey();
                        continue;
                    }
                    up = Math.min(i + 100, idWorkset.size());
                    HttpRequest base = HttpRequest.newBuilder().uri((requestURI = utils.generateQuery(idWorkset.subList(i, up), 1))).POST(HttpRequest.BodyPublishers.noBody()).build();
                    if (requestsToRepeat.empty()) {
                        request = new WrappedHTTPRequest(keys[key].signRequest(base), reqTarget, requestsPerSingleFile++, file);
                        i += (up - i);
                    } else {
                        old = requestsToRepeat.pop();
                        request = new WrappedHTTPRequest(keys[key].signRequest(old.request()), old.reqTarget(), old.reqNumber(), old.fileInput());
                        logger.info("[Received request to repeat][File " + request.fileInput() + " ][Request n° " + old.reqNumber() + "/" + request.reqTarget() + "]");
                    }
                    logger.info("[Request n° " + (requestsPerSingleFile) + " created ][Total : " + (total++) + "]" + "[Valid for " + keys[key].getUsesLeft() + "] [Key : " + keys[key].getId() + "]\n[Key " + keys[key].toString() + "]");
                    if (!executor.executeRequest(request)) keys[key].setUsableHint(false);
                }
            } catch (InterruptedException e) {
                logger.fatal(e.getMessage());
                break;
            } catch (MalformedURLException e) {
                logger.fatal(requestURI.toString());
            } catch (IOException | AbstractKey.UnusableKeyException e) {
                logger.fatal(e.getMessage());
            }
            logger.trace("Created all the requests for file : " + file);
            requestsPerSingleFile = i = 0;
            currentFile++;
        }
       // if (waitForMe != null) waitForMe.release();
        logger.trace("Hydrator terminating - Exit code 0");
    }
}
