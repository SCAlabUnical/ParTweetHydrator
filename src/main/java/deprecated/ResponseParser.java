package deprecated;

public class ResponseParser extends Thread {
    /*private GZIPOutputStream compressedOutput;
    private BufferedOutputStream outputStream;
    private Semaphore waitForData, fileIsDone;
    private Stack<Character> parser = new Stack<>();
    private File risultato;
    private Buffer<WrappedCompletableFuture> sentRequests;
    private RejectedRequestsHandler handler = null;
    private PrintWriter logger;
    private final static int PARSER_POOL = 10;
    private int n429 = 0, okResponsesReceived = 0, responseTarget;
    private ResponseParserParallel parserParallel;

    void setOutputAndTarget(File f, int responseTarget) {
        risultato = f;
        this.responseTarget = responseTarget;
    }

    private int received = 0;

    void attachHandler(RejectedRequestsHandler handler) {
        this.handler = handler;
    }

    synchronized void setResponseTarget(int received) {
        responseTarget = received;
    }

    public ResponseParser(Buffer<WrappedCompletableFuture> sentRequests, Semaphore waitForData, Semaphore fileIsDone, PrintWriter logger) {
        this.waitForData = waitForData;
        this.fileIsDone = fileIsDone;
        this.logger = logger;
        this.sentRequests = sentRequests;
    }


    public void run() {
        boolean firstMatch;
        int endIndex, beginIndex = 0;
        char c, peek;
        String x;
        WrappedCompletableFuture futureResponse;
        HttpResponse<String> answer;
        while (!this.isInterrupted()) {
            try {
                waitForData.acquire();
                if (outputStream == null) {
                    outputStream = new BufferedOutputStream(new FileOutputStream(risultato, false));
                    System.out.println("Opening output stream");
                }
                if (compressedOutput == null) {
                    compressedOutput = new GZIPOutputStream(outputStream);
                    System.out.println("Opening GZIP stream");
                }
                while (okResponsesReceived != responseTarget) {
                    futureResponse = sentRequests.get();
                    answer = futureResponse.futureResponse().join();
                    received++;
                    logger.println("[Response n° " + (received) + " + received][Ack :  " + okResponsesReceived + "]" + "[Status : " + answer.statusCode() + "]" +
                            "[Limit :" + utils.checkLimits(answer) + "]");
                    if (answer.statusCode() != 200) {
                        n429++;
                        //TEMP
                        if (n429 == 5)
                            System.exit(-1);
                        if (handler != null)
                            handler.put(futureResponse.request());
                        else System.out.println("Request lost due to missing handler");
                        continue;
                    } else okResponsesReceived++;
                    x = answer.body();
                    firstMatch = true;
                    peek = ' ';
                    for (int i = 0; i < x.length(); i++) {
                        c = x.charAt(i);
                        if (c == '}' && peek == '{') {
                            parser.pop();
                            if (parser.isEmpty()) {
                                endIndex = i + 1;
                                compressedOutput.write((x.substring(beginIndex, endIndex) + "\n").getBytes(StandardCharsets.UTF_8));
                                firstMatch = true;
                            } else peek = parser.peek();
                        } else if (c == '}' || c == '{') {
                            if (firstMatch && c == '{') {
                                beginIndex = i;
                                firstMatch = false;
                            }
                            parser.push(peek = c);
                        }
                    }
                    compressedOutput.flush();
                    parser.clear();
                }
            } catch (InterruptedException e) {
                System.out.println("Parser interrotto");
                break;
            } catch (IOException e) {
                System.out.println(e.getMessage());
            } finally {
                try {
                    System.out.println("Closing streams...");
                    if (compressedOutput != null) {
                        compressedOutput.flush();
                        compressedOutput.close();
                        compressedOutput = null;
                    }
                    if (outputStream != null) {
                        outputStream.flush();
                        outputStream.close();
                        outputStream = null;
                    }
                    System.out.println("Parser closed all streams");
                    fileIsDone.release();
                } catch (IOException e) {
                }
            }

        }
        System.out.println("OUT");
    }*/
}



