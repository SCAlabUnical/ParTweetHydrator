package hydrator;


import dataStructures.*;
import flyweight.FlyweightFactory;
import key.AbstractKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import strategy.VerboseParsing;


import java.io.*;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static utils.utils.*;


public enum Hydrator {
    INSTANCE;
    private static final Logger logger = LogManager.getLogger(Hydrator.class.getName());
    private AbstractKey[] tokens;


    private List<File> tweetIdFiles, rehydratedFiles;
    private HashMap<Integer, Long> timeElapsed = new HashMap<>();
    private String LOG_PATH, pathSalvataggio;
    ResponseParser parser;
    RequestExecutor executor;
    RequestsSupplier supplier;
    IOHandler ioHandler;
    private Buffer<WrappedCompletableFuture> risposteHTTP;
    private Buffer<ByteAndDestination> codaOutput;
    private exec_setting rate;
    private int currentWorkRate;
    private int completedFiles = 0;
    private boolean isRunning = false;

    public List<File> getTweetIdFiles() {
        return tweetIdFiles;
    }
    boolean isRunning() {
        return isRunning;
    }

    synchronized void fileCompleted() {
        completedFiles++;
    }

    int getCompletedFiles() {
        return completedFiles;
    }

    public enum exec_setting {SLOW, FAST, VERY_FAST}

    final static float version = 2.21f;

    public int getCurrentWorkRate() {
        return currentWorkRate;
    }

    public void setCurrentWorkRate(int r) {
        currentWorkRate = r;
    }

    public void setLogPath(String logPath) {
        this.LOG_PATH = logPath;
        if (LOG_PATH.charAt(LOG_PATH.length() - 1) != System.getProperty("file.separator").charAt(0))
            LOG_PATH += System.getProperty("file.separator");
        File mainLog;
        try {
            if ((mainLog = new File(LOG_PATH + "completion.txt")).exists())
                tryToRestoreFromLog(mainLog);
        } catch (IOException e) {
            logger.warn("Unable to restore from log,starting from scratch");
        }
    }

    public void setSavePath(String savePath) {
        if (savePath.charAt(savePath.length() - 1) != System.getProperty("file.separator").charAt(0))
            savePath += System.getProperty("file.separator");
        this.pathSalvataggio = savePath;

    }

    public void setTokens(String pathToAccountsXML) {
        this.tokens = loadAllTokens(pathToAccountsXML);
    }


    int getFileListSize() {
        return tweetIdFiles.size();
    }

    public void setFileList(String pathToFileListOrDirectory) throws IOException {
        this.tweetIdFiles = loadFiles(new File(pathToFileListOrDirectory));
    }

    public void setRate(exec_setting value) {
        this.rate = value;
    }


    private void tryToRestoreFromLog(File restoreFrom) throws IOException {

        try (BufferedReader br = new BufferedReader(new FileReader(restoreFrom))) {
            ArrayList<File> toRemove = new ArrayList<>();
            String line = "";
            StringTokenizer st;
            while ((line = br.readLine()) != null) {
                st = new StringTokenizer(line, "$");
                if (st.countTokens() < 2)
                    logger.warn("Attempt to restore from logs aborted,file structure unknown");
                st.nextToken();
                line = (st.nextToken().trim().replaceAll("\\.json\\.gz", ".txt"));
                String finalLine = line;
                tweetIdFiles.stream().filter(file -> file.getName().equals(finalLine)).forEach(toRemove::add);
            }
            toRemove.forEach(file -> {
                completedFiles++;
                tweetIdFiles.remove(file);
                logger.warn("[Removed " + file.getName() + " from the work queue][Reason : already processed]");
            });
        }
    }


    private void init() {
        ClassLoader classLoader = Hydrator.INSTANCE.getClass().getClassLoader();
        if (LOG_PATH == null)
            LOG_PATH = System.getProperty("java.io.tmpdir");
        System.setProperty("log4j_logPath", LOG_PATH);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        boolean error = false;
        try {
            ctx.setConfigLocation(classLoader.getResource("logger_conf.xml").toURI());
        } catch (URISyntaxException e) {
            error = true;
            e.printStackTrace();
        }
        if (error)
            System.out.println("Logging is disabled due to the missing config file");
        else
            ctx.reconfigure();
        int buffer_sizes = (int) Math.pow(10, (rate.ordinal()));
        risposteHTTP = new Buffer<>(buffer_sizes, "risposteHTTP");
        codaOutput = new Buffer<>(buffer_sizes, "queueToDisk");
        tweetIdFiles = Collections.unmodifiableList(tweetIdFiles);
        rehydratedFiles = new ArrayList<>(tweetIdFiles.size());
        for (int i = 0; i < tweetIdFiles.size(); i++)
            timeElapsed.put(i, System.currentTimeMillis());
        tweetIdFiles.forEach(file -> rehydratedFiles.add(new File(pathSalvataggio + file.getName().replaceAll("\\.txt", ".json.gz"))));
        rehydratedFiles = Collections.unmodifiableList(rehydratedFiles);
        executor = new RequestExecutor(risposteHTTP, rate);
        supplier = new RequestsSupplier(tokens, executor);
        FlyweightFactory flyweightFactory = new FlyweightFactory(100);
        ioHandler = new IOHandler(codaOutput, tweetIdFiles.size(), flyweightFactory);
        ioHandler.setSavePath(pathSalvataggio);
        ioHandler.setOriginalFileList(tweetIdFiles);
        parser = new ResponseParser(executor, risposteHTTP, codaOutput, new VerboseParsing(flyweightFactory));
    }

    void setStartTime(int fileIndex) {
        timeElapsed.put(fileIndex, System.currentTimeMillis());
    }

    long setTime(int fileIndex) {
        long old = timeElapsed.get(fileIndex), ret;
        timeElapsed.put(fileIndex, ret = System.currentTimeMillis() - old);
        return ret;
    }


    File getOutputFile(int index) {
        return rehydratedFiles.get(index);
    }


    public void hydrate() {
        isRunning = true;
        init();
        GraphicModule.INSTANCE.statusPanel.start();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        parser.attachHandler(supplier);
        supplier.setFilesToWorkOn(tweetIdFiles.size());
        executorService.execute(supplier);
        executorService.execute(ioHandler);
        parser.startWorkers();
        int target;
        List<String> ids;
        int index = 0;
        GraphicModule.INSTANCE.waitForSetup.release();
        for (File tweetIds : tweetIdFiles) {
            try {
                ids = loadTweetIds(tweetIds.getAbsolutePath());
                target = (int) Math.ceil((double) ids.size() / 100);
                ioHandler.setTargetPerFile(index, target);
                supplier.workSetRefresh(new WorkKit(ids, index++));
                logger.info("[Added " + tweetIds.getName() + " to the work queue][Request target : " + target + "]");
            } catch (InterruptedException | IOException e) {
                logger.fatal("Failed to load " + tweetIds.getAbsolutePath());
                logger.fatal(e.getMessage());
                logger.trace(Arrays.toString(e.getStackTrace()));
            }
        }
    }

    public void stop() {
        isRunning = false;
        GraphicModule.INSTANCE.statusPanel.stop();
    }

    boolean isSetup() {
        return tweetIdFiles != null && pathSalvataggio != null && tokens != null && rate != null;
    }

    public static void main(String... args) {
        //reference per effettuare il loading delle enum
        System.out.println("Hydrator starting");
        Object x = GraphicModule.INSTANCE;
        Object y = Hydrator.INSTANCE;
    }
}