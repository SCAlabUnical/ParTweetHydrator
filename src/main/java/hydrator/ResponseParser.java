package hydrator;


import dataStructures.Buffer;
import dataStructures.ByteAndDestination;
import dataStructures.WrappedCompletableFuture;
import dataStructures.WrappedHTTPRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.ParsingStrategy;


import java.io.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ResponseParser {
    private static final Logger logger = LogManager.getLogger(ResponseParser.class.getName());
    private static final Logger errorLogger = LogManager.getLogger("ERROR_LOGGER");
    private RequestsSupplier handler = null;
    private final static int PARSER_POOL = 5;
    private List<File> workset = new ArrayList<>(100);
    private PrintWriter[] loggers = new PrintWriter[PARSER_POOL];
    private Buffer<WrappedCompletableFuture> workSet;
    private Buffer<ByteAndDestination> queueToFile;
    private final String logPath;
    private final RequestExecutor executor;
    private static long rehydratedTweets = 0;
    private long notificationExpireTimestap = Long.MAX_VALUE;
    private final ParsingStrategy parsingStrategy;

    public ResponseParser(RequestExecutor executor, Buffer<WrappedCompletableFuture> outerWorkset, Buffer<ByteAndDestination> outerOutput, String logPath, ParsingStrategy strategy) {
        this.workSet = outerWorkset;
        this.parsingStrategy = strategy;
        this.executor = executor;
        this.queueToFile = outerOutput;
        this.logPath = logPath;
    }

    public static long getRehydratedTweets() {
        return rehydratedTweets;
    }

    public static synchronized void updateRehydrateTweets(int add) {
        rehydratedTweets += add;
    }

    public void startWorkers() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < PARSER_POOL; i++)
            executorService.execute(new ParserWorker(i));

    }

    public void attachHandler(RequestsSupplier handler) {
        this.handler = handler;
    }


    class ParserWorker implements Runnable {
        private final int index;

        public ParserWorker(int index) {
            this.index = index;
        }


        private void repeat(WrappedHTTPRequest request) {
            if (handler != null)
                handler.addRequestToRepeat(request);
            else {
                logger.fatal("Missing handler, request lost");
                logger.fatal("The file will be missing tweets");
            }
        }

        public void run() {
            List<byte[]> tweets;
            WrappedCompletableFuture currentItem;
            HttpResponse<String> response;
            CompletableFuture<HttpResponse<String>> futureResponse;
            final Throwable[] e = new Exception[1];
            final boolean[] err = new boolean[1];
            while (true)
                try {
                    currentItem = workSet.get();
                    futureResponse = currentItem.futureResponse().handle(
                            (resp, exception) -> {
                                if (exception != null) {
                                    e[0] = exception;
                                    err[0] = true;
                                    return null;
                                }
                                return resp;
                            }
                    );

                    response = futureResponse.join();

                    if (!err[0]) {
                        if (response.statusCode() == 200) {
                            queueToFile.put(new ByteAndDestination(tweets = parsingStrategy.parse(response.body()), currentItem.fileIndex(), currentItem.packetNumber()));
                            updateRehydrateTweets(tweets.size());
                            logger.info("[Response n° " + currentItem.packetNumber() + " received]  [Status : " + response.statusCode() + "]");
                        } else {
                            logger.warn("Timing out executor,worker n° " + this.index);
                            executor.timeout(response.statusCode(), false);
                            errorLogger.trace(response.statusCode() + " [STATUS CODE]");
                            errorLogger.trace(response.body());
                            errorLogger.trace(response.headers());
                            errorLogger.trace(response.request());
                            repeat(currentItem.request());
                        }
                    } else {
                        executor.timeout(-1, true);
                        errorLogger.error(e[0].getMessage());
                        errorLogger.error(e[0].getCause());
                        errorLogger.error(e[0]);
                        errorLogger.error(e[0].getLocalizedMessage());
                        err[0] = false;
                        repeat(currentItem.request());
                    }
                } catch (InterruptedException interr) {
                    errorLogger.error(interr.getMessage());
                    break;
                }

        }

    }


}



