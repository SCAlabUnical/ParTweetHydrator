package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import utils.ParsingStrategy;


import java.io.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


public class ResponseParserParallel {
    private Logger logger = LogManager.getLogger(ResponseParserParallel.class.getName());
    private RequestsSupplier handler = null;
    private final static int PARSER_POOL = 5;
    private List<File> workset = new ArrayList<>(100);
    private PrintWriter[] loggers = new PrintWriter[PARSER_POOL];
    private Buffer<WrappedCompletableFuture> workSet;
    private Buffer<ByteAndDestination> queueToFile;
    private final String logPath;
    private final RequestExecutor executor;
    private final AtomicInteger tweets = new AtomicInteger(0);
    private long notificationExpireTimestap = Long.MAX_VALUE;
    private final ParsingStrategy parsingStrategy;

    public ResponseParserParallel(RequestExecutor executor, Buffer<WrappedCompletableFuture> outerWorkset, Buffer<ByteAndDestination> outerOutput, String logPath, ParsingStrategy strategy) {
        this.workSet = outerWorkset;
        this.parsingStrategy = strategy;
        this.executor = executor;
        this.queueToFile = outerOutput;
        this.logPath = logPath;
    }


    public void startWorkers() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int i = 0; i < PARSER_POOL; i++)
            executorService.execute(new parser(i));

    }

    public void attachHandler(RequestsSupplier handler) {
        this.handler = handler;
    }


    class parser implements Runnable {
        private final int index;

        public parser(int index) {
            this.index = index;
        }


        private void repeat(WrappedHTTPRequest request) {
            if (handler != null)
                handler.addRequestToRepeat(request);
            else {
                logger.fatal("Missing handler, request lost");
                logger.fatal("The file will be corrupted");
            }
        }

        public void run() {
            WrappedCompletableFuture currentItem;
            HttpResponse<String> response;
            CompletableFuture<HttpResponse<String>> futureResponse;
            final Throwable[] e = new Exception[1];
            final boolean[] err = new boolean[1];
            while (true)
                try {
                    currentItem = workSet.get();
                    futureResponse = currentItem.future().handle(
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
                            queueToFile.put(new ByteAndDestination(parsingStrategy.parse(response.body()), currentItem.fileIndex(), currentItem.packetNumber()));
                            logger.info("[Response n° " + currentItem.packetNumber() + " received] + [Status : " + response.statusCode() + "]");
                        } else {
                            logger.warn("Timing out executor " + this.index);
                            executor.timeout(response.statusCode(),false);
                            logger.error(response.statusCode() + " [STATUS CODE]");
                            logger.trace(response.body());
                            logger.trace(currentItem.request().request().uri());
                            logger.trace(currentItem.request().request().method());
                            repeat(currentItem.request());
                        }
                    } else {
                        executor.timeout(-1,true);
                        logger.error(e[0].getMessage());
                        logger.error(e[0].getCause());
                        logger.error(e[0]);
                        err[0] = false;
                        repeat(currentItem.request());
                    }
                } catch (InterruptedException interr) {
                    logger.error(interr.getMessage());
                    break;
                }

        }

    }


}



