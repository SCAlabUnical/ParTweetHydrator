package hydrator;


import dataStructures.Buffer;
import dataStructures.WrappedCompletableFuture;
import dataStructures.WrappedHTTPRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;


public final class RequestExecutor {
    private Logger logger = LogManager.getLogger(RequestExecutor.class.getName());
    private Buffer<WrappedCompletableFuture> sent;
    private volatile boolean timeout = false, error = false;
    private int requests = 0, numberOfTimeouts;
    private long normalSleepTime;
    public HttpClient client;

    private final static Map<Hydrator.exec_setting, Integer>
            sleepRates = Map.of(Hydrator.exec_setting.VERY_FAST, 15, Hydrator.exec_setting.FAST, 10, Hydrator.exec_setting.SLOW, 10),


    sleepTimes = Map.of(Hydrator.exec_setting.VERY_FAST, 1000, Hydrator.exec_setting.FAST, 1000, Hydrator.exec_setting.SLOW, 1000);
    private int timeoutReason = 0;
    private final int SLEEP_AFTER_N_REQUESTS;
    private boolean everythingOkay = true;
    private int executedWithoutResting = 0;
    private long[] pastTimeouts = new long[10];
    private int currentIndexTimeout = 0;

    synchronized void timeout(int errorCode, boolean clientError) {
        if (clientError) {
            if (error) return;
            error = true;
            return;
        }
        if (timeout) return;
        timeoutReason = errorCode;
        timeout = true;
    }

    public int getRequests() {
        return requests;
    }

    public void resetClient() {
        client = HttpClient.newBuilder().executor(Executors.newFixedThreadPool(50)).build();
    }

    public RequestExecutor(Buffer<WrappedCompletableFuture> sent, Hydrator.exec_setting selectedRate) {
        this.sent = sent;
        resetClient();
        SLEEP_AFTER_N_REQUESTS = sleepRates.get(selectedRate);
        this.normalSleepTime = sleepTimes.get(selectedRate);
        logger.info("[Sleep time every " + SLEEP_AFTER_N_REQUESTS + "  requests : " + normalSleepTime + " ms]");
        logger.trace("[Sleep time :  " + normalSleepTime + " ms]");
        logger.trace("[Sleep time every " + SLEEP_AFTER_N_REQUESTS + "  requests : " + normalSleepTime + " ms]");
    }

    private void handleTimeout() throws InterruptedException {
        int val = timeoutReason / 100;
        if (val == 5) {
            normalSleepTime = Math.min(normalSleepTime + 50, 2300);
            logger.warn("[Executor - new cooldown time : " + normalSleepTime + " s]");
        }
        //SOLO SE l'ultimo timeout è recente viene tenuti in considerazione nel backoff esponenziale
        if (Math.abs(pastTimeouts[(Math.abs(currentIndexTimeout - 1)) % pastTimeouts.length] - Instant.now().getEpochSecond()) > 10 * 60)
            currentIndexTimeout = 0;
        pastTimeouts[currentIndexTimeout] = Instant.now().getEpochSecond();
        currentIndexTimeout = (currentIndexTimeout + 1) % pastTimeouts.length;
        int timeoutWaitTime = (int) Math.min(Math.pow(2, currentIndexTimeout + 2), 900);
        GraphicModule.INSTANCE.statusPanel.timeOut();
        logger.warn("[Executor has been timed out for " + timeoutWaitTime + " seconds][Total timeouts : " + (++numberOfTimeouts) + "]");
        TimeUnit.SECONDS.sleep(timeoutWaitTime);
        synchronized (this) { //non necessario
            timeout = false;
        }
        everythingOkay = false;
        GraphicModule.INSTANCE.statusPanel.start();
    }

    private void reInitClients() throws InterruptedException {
        GraphicModule.INSTANCE.statusPanel.timeOut();
        resetClient();
        logger.warn("[Resetting client due to network errors][30s timeout]");
        TimeUnit.SECONDS.sleep(30);
        logger.warn("[Resuming work]");
        normalSleepTime += 150;
        synchronized (this) { //non necessario
            error = false;
        }
        GraphicModule.INSTANCE.statusPanel.start();
        everythingOkay = false;
    }

    public boolean executeRequest(WrappedHTTPRequest request) {
        everythingOkay = true;
        try {
            if (timeout)
                handleTimeout();
            if (error)
                resetClient();
            sent.put(new WrappedCompletableFuture(request, client.sendAsync(request.request(), HttpResponse.BodyHandlers.ofString()), request.fileInput()
                    , request.reqNumber()));
            logger.info("[Request n° " + (requests++) + " sent : " + Instant.now().toEpochMilli() + "]");
            if (executedWithoutResting++ == SLEEP_AFTER_N_REQUESTS) {
                TimeUnit.MILLISECONDS.sleep(normalSleepTime);
                executedWithoutResting = 0;
            }

        } catch (InterruptedException e) {
            logger.fatal(e.getMessage());
            everythingOkay = false;
        }
        logger.info("Executor shutting down...");
        return everythingOkay;
    }
}
