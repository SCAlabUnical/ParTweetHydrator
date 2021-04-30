package lightweightVersion;



import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;


import java.io.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class ResponseParserParallel {
    private Logger logger = LogManager.getLogger(ResponseParserParallel.class.getName());
    private RequestsSupplier handler = null;
    private final static int PARSER_POOL = 5;
    private List<File> workset = new ArrayList<>(100);
    private PrintWriter[] loggers = new PrintWriter[PARSER_POOL];
    private Buffer<WrappedCompletableFuture> workSet;
    private Buffer<ByteAndDestination> outerOutput;
    private final String logPath;
    private final RequestExecutor executor;
    private final AtomicInteger tweets = new AtomicInteger(0);
    private boolean hasTheExecutorBeenTimedOut = false, clientErrors = false;
    private long notificationExpireTimestap = Long.MAX_VALUE;
    private final Object executorInteractions = new Object();

    public ResponseParserParallel(RequestExecutor executor, Buffer<WrappedCompletableFuture> outerWorkset, Buffer<ByteAndDestination> outerOutput, String logPath) {
        this.workSet = outerWorkset;
        this.executor = executor;
        this.outerOutput = outerOutput;
        this.logPath = logPath;
    }


    public void startWorkers() {
        ExecutorService executorService = Executors.newFixedThreadPool(PARSER_POOL);
        for (int i = 0; i < PARSER_POOL; i++)
            try {
                executorService.execute(new parser(i));
            } catch (Exception e) {
                System.out.println("ERR");
            }
    }

    public void attachHandler(RequestsSupplier handler) {
        this.handler = handler;
    }


    class parser implements Runnable {

        private final int index;
        private final Stack<Character> parser = new Stack<>();

        public parser(int index) throws IOException {
            this.index = index;

        }

        List<byte[]> parse(String answer) {
            int beginIndex = 0;
            ArrayList<byte[]> arrayList = new ArrayList<>(100);
            boolean firstMatch = true;
            char peek = ' ', c;
            for (int i = 0; i < answer.length(); i++) {
                c = answer.charAt(i);
                try {
                    if (c == '}' && peek == '{') {
                        parser.pop();
                        if (parser.isEmpty()) {
                            tweets.incrementAndGet();
                            arrayList.add((answer.substring(beginIndex, i + 1) + "\n").getBytes(StandardCharsets.UTF_8));
                            firstMatch = true;
                        } else peek = parser.peek();
                    } else if (c == '}' || c == '{') {
                        if (firstMatch && c == '{') {
                            beginIndex = i;
                            firstMatch = false;
                        }
                        parser.push(peek = c);
                    }
                } catch (Exception e) {
                    logger.fatal(answer.substring(beginIndex, i + 1));
                    System.exit(0);
                }
            }
            parser.clear();
            return arrayList;
        }

        List<byte[]> parse2(String answer) {
            ArrayList<byte[]> arrayList = new ArrayList<>(100);
            JSONArray array = new JSONArray(answer);
            for (int i = 0; i < array.length(); i++)
                arrayList.add(((array.get(i)).toString() + "\n").getBytes(StandardCharsets.UTF_8));
            return arrayList;
        }


        public void run() {
            WrappedCompletableFuture workingOn;
            HttpResponse<String> response = null;
            CompletableFuture<HttpResponse<String>> res;
            final Throwable[] e = new Exception[1];
            final boolean[] err = new boolean[1];
            while (true)
                try {
                    workingOn = workSet.get();
                    res = workingOn.future().handle(
                            (resp, exception) -> {
                                if (exception != null) {
                                    e[0] = exception;
                                    err[0] = true;
                                }
                                return resp;
                            }
                    );
                    if (!err[0])
                        response = res.join();
                    if (!err[0] && response.statusCode() == 200) {
                        outerOutput.put(new ByteAndDestination(parse2(response.body()), workingOn.output(), workingOn.packetNumber()));
                        logger.info("[Response n° " + workingOn.packetNumber() + " received] + [Status : " + response.statusCode() + "]");
                    } else {
                        if (!err[0]) {
                            //bad status code
                            synchronized (executorInteractions) {
                                if (!hasTheExecutorBeenTimedOut) {
                                    logger.warn("Timing out executor " + this.index);
                                    notificationExpireTimestap = Instant.now().getEpochSecond() + executor.timeout(response.statusCode());
                                    hasTheExecutorBeenTimedOut = true;
                                }
                            }
                            logger.trace(response.statusCode());
                            logger.trace(response.headers());
                            logger.trace(response.body());
                        } else {
                            synchronized (executorInteractions) {
                                if (!clientErrors) {
                                    clientErrors = true;
                                    executor.signalClientError();
                                    notificationExpireTimestap = Instant.now().getEpochSecond() + 2 * 60;
                                    logger.error("[An exception occurred : " + e[0].getCause() + " ]");
                                    logger.error("[Repeating requests...]");
                                }
                            }
                            logger.trace(Arrays.toString(e[0].getStackTrace()));
                            err[0] = false;
                        }
                        if (handler != null)
                            handler.addRequestToRepeat(workingOn.request());
                        else {
                            logger.fatal("Missing handler, request lost");
                            logger.fatal("The file will be corrupted");
                        }
                    }
                    /* sincronizzazione non necessaria,il risultato non cambia*/
                    if (hasTheExecutorBeenTimedOut && (Instant.now().getEpochSecond() > notificationExpireTimestap))
                        hasTheExecutorBeenTimedOut = false;
                    if (clientErrors && (Instant.now().getEpochSecond() > notificationExpireTimestap))
                        clientErrors = false;
                } catch (InterruptedException interr) {
                    logger.error(interr.getMessage());
                    break;
                }

        }

    }


}



