package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;


public final class RequestExecutor extends Thread {
    private Logger logger = LogManager.getLogger(RequestExecutor.class.getName());
    private Buffer<WrappedHTTPRequest> toSend;
    private Buffer<WrappedCompletableFuture> sent;
    private final int client_pool_size;
    private HttpClient client;
    private ExecutorService services;
    private volatile boolean timeout = false, error = false;
    private int normalSleepTime, requests = 0, numberOfTimeouts;
    private final Map<Integer, Integer> causeAndValue = new HashMap<>(Map.of(4, 900, 5, 30));
    private int timeoutReason = 0;


    int timeout(int errorCode) {
        timeoutReason = errorCode;
        timeout = true;
        return causeAndValue.get(errorCode / 100);
    }

    void signalClientError() {
        error = true;
    }

    public RequestExecutor(Buffer<WrappedHTTPRequest> toSend, Buffer<WrappedCompletableFuture> sent, int clientPool) {
        this.toSend = toSend;
        this.client_pool_size = clientPool;
        this.sent = sent;
        this.normalSleepTime = (int) Math.ceil(0.04 * client_pool_size);
        initClients(true);
    }

    private void handleTimeout() throws InterruptedException {
        int val, howlong = causeAndValue.get(val = timeoutReason / 100);
        if (val == 5) {
            normalSleepTime += 1;
            logger.trace("[Executor - new cooldown time : " + normalSleepTime + " s]");
            causeAndValue.put(val, causeAndValue.get(val) + 3);
        }
        logger.warn("Executor has been timed out for " + howlong);
        logger.warn("Executor has been timed out [Total timeouts : " + (numberOfTimeouts++ + 1) + "]");
        TimeUnit.SECONDS.sleep(howlong);
        timeout = false;
    }

    private void initClients(boolean firstTime) {
        if (firstTime)
            services = Executors.newFixedThreadPool(client_pool_size);
        client = HttpClient.newBuilder().executor(services).build();
        error = false;
    }

    public void run() {
        int executedWithoutResting = 0;
        WrappedHTTPRequest request;
        while (!this.isInterrupted()) {
            try {
                request = toSend.get();
                sent.put(new WrappedCompletableFuture(request, client.sendAsync(request.request(), HttpResponse.BodyHandlers.ofString()), request.fileInput()
                        , request.reqNumber()));
                logger.info("[Request n° " + (requests++) + " sent : " + Instant.now().toEpochMilli() + "]");
                if (executedWithoutResting++ == client_pool_size) {
                    TimeUnit.SECONDS.sleep(normalSleepTime);
                    executedWithoutResting = 0;
                }
                if (timeout)
                    handleTimeout();
                if (error)
                    initClients(false);
            } catch (InterruptedException e) {
                logger.fatal(e.getMessage());
                break;
            }
        }
        logger.info("Executor shutting down...");
    }
}
