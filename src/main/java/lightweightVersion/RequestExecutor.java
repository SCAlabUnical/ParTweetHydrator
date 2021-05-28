package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.RestManager;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;


public final class RequestExecutor extends Thread {
    private Logger logger = LogManager.getLogger(RequestExecutor.class.getName());
    private Buffer<WrappedHTTPRequest> toSend;
    private Buffer<WrappedCompletableFuture> sent;
    private volatile boolean timeout = false, error = false;
    private int requests = 0, numberOfTimeouts;
    private long normalSleepTime;
    private final Map<Integer, Integer> causeAndValue = new HashMap<>(Map.of(4, 900, 5, 30));
    private final Map<Hydrator.exec_setting, Integer>
            sleepRates = Map.of(Hydrator.exec_setting.MAX, 15, Hydrator.exec_setting.VERY_FAST, 15, Hydrator.exec_setting.FAST, 10, Hydrator.exec_setting.SLOW, 10), sleepTimes =
            Map.of(Hydrator.exec_setting.MAX, 1250, Hydrator.exec_setting.VERY_FAST, 1100, Hydrator.exec_setting.FAST, 1000, Hydrator.exec_setting.SLOW, 1000);
    private int timeoutReason = 0;
    private final int SLEEP_AFTER_N_REQUESTS;

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


    public RequestExecutor(Buffer<WrappedHTTPRequest> toSend, Buffer<WrappedCompletableFuture> sent, Hydrator.exec_setting selectedRate) {
        this.toSend = toSend;
        this.sent = sent;
        SLEEP_AFTER_N_REQUESTS = sleepRates.get(selectedRate);
        this.normalSleepTime = sleepTimes.get(selectedRate);
        logger.info("[Sleep time every " + SLEEP_AFTER_N_REQUESTS + "  requests : " + normalSleepTime + " ms]");
        logger.trace("[Sleep time :  " + normalSleepTime + " ms]");
        logger.trace("[Sleep time every " + SLEEP_AFTER_N_REQUESTS + "  requests : " + normalSleepTime + " ms]");
    }

    private void handleTimeout() throws InterruptedException {
        int val, sleepLength = causeAndValue.get(val = timeoutReason / 100);
        if (val == 5) {
            normalSleepTime += 450;
            logger.warn("[Executor - new cooldown time : " + normalSleepTime + " s]");
            causeAndValue.put(val, causeAndValue.get(val) + 3);
        }
        logger.warn("Executor has been timed out for " + sleepLength);
        logger.warn("Executor has been timed out [Total timeouts : " + (numberOfTimeouts++ + 1) + "]");
        TimeUnit.SECONDS.sleep(sleepLength);
        synchronized (this) { //non necessario
            timeout = false;
        }
    }

    private void reInitClients() throws InterruptedException {
        RestManager.resetClient();
        logger.warn("[Resetting client due to network errors]");
        TimeUnit.SECONDS.sleep(30);
        logger.warn("[Resuming work]");
        normalSleepTime+=150;
        synchronized (this) { //non necessario
            error = false;
        }

    }

    public void run() {
        int executedWithoutResting = 0;
        WrappedHTTPRequest request;
        while (!this.isInterrupted()) {
            try {
                request = toSend.get();
                sent.put(new WrappedCompletableFuture(request, RestManager.client.sendAsync(request.request(), HttpResponse.BodyHandlers.ofString()), request.fileInput()
                        , request.reqNumber()));
                logger.info("[Request n° " + (requests++) + " sent : " + Instant.now().toEpochMilli() + "]");
                if (executedWithoutResting++ == SLEEP_AFTER_N_REQUESTS) {
                    TimeUnit.MILLISECONDS.sleep(normalSleepTime);
                    executedWithoutResting = 0;
                }
                if (timeout)
                    handleTimeout();
                if (error)
                    reInitClients();
            } catch (InterruptedException e) {
                logger.fatal(e.getMessage());
                break;
            }
        }
        logger.info("Executor shutting down...");
    }
}
