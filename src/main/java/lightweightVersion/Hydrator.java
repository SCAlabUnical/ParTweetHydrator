package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static utils.utils.*;


public class Hydrator {
    private Logger logger = LogManager.getLogger(Hydrator.class.getName());
    private Key[] tokens;
    private List<File> tweetIdFiles, rehydratedFiles;
    private String LOG_PATH, pathSalvataggio, logConfLocation;
    private ResponseParserParallel parser;
    private RequestExecutor executor;
    private RequestsSupplier supplier;
    private IOHandler ioHandler;
    private Buffer<WrappedCompletableFuture> risposteHTTP;
    private Buffer<WrappedHTTPRequest> richiesteHTTP;
    private Buffer<ByteAndDestination> codaOutput;
    private exec_setting rate;
    private int allComponentsHaveBeenSetup = 0;

    public enum exec_setting {SLOW, FAST, VERY_FAST, MAX}


    private static Hydrator instance;

    public static synchronized Hydrator getInstance() {
        if (instance == null)
            instance = new Hydrator();
        return instance;
    }

    private Hydrator() {

    }


    public Hydrator setLogPath(String logPath) {
        this.LOG_PATH = logPath;
        allComponentsHaveBeenSetup++;
        return instance;
    }

    public Hydrator setSavePath(String savePath) {
        this.pathSalvataggio = savePath;
        allComponentsHaveBeenSetup++;
        return instance;
    }

    public Hydrator setTokens(String pathToAccountsXML) throws IOException, Key.UnusableKeyException {
        this.tokens = loadAllTokens(pathToAccountsXML);
        allComponentsHaveBeenSetup++;
        return instance;
    }

    public Hydrator setFileList(String pathToFileList) throws IOException {
        this.tweetIdFiles = loadFiles(new File(pathToFileList));
        allComponentsHaveBeenSetup++;
        return instance;
    }

    public Hydrator setRate(exec_setting value) {
        this.rate = value;
        allComponentsHaveBeenSetup++;
        return instance;
    }

    public Hydrator setLogConfigFile(String logToConfigFile) {
        this.logConfLocation = logToConfigFile;
        allComponentsHaveBeenSetup++;
        return instance;
    }

    private void tryToRestoreFromLog(File restoreFrom) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(restoreFrom));
        ArrayList<File> toRemove = new ArrayList<>();
        try {
            String line = "";
            StringTokenizer st;
            while ((line = br.readLine()) != null) {
                st = new StringTokenizer(line, "$");
                if (st.countTokens() < 2) logger.warn("Restore from log aborted,file structure unknown");
                st.nextToken();
                line = (st.nextToken().trim().replaceAll("\\.json\\.gz", ".txt"));
                String finalLine = line;
                tweetIdFiles.stream().filter(file -> file.getName().equals(finalLine)).forEach(toRemove::add);
            }
            toRemove.forEach(file -> tweetIdFiles.remove(file));
            toRemove.forEach(file -> logger.warn("[Removed " + file.getName() + " from the work queue][Reason : already processed]"));
        } finally {
            br.close();
        }
    }


    private void init() {
        if (allComponentsHaveBeenSetup != 6)
            throw new IllegalStateException("Finish configuring the hydrator");
        System.setProperty("log4j.configurationFile", logConfLocation);
        System.setProperty("log4j_logPath", LOG_PATH);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        ctx.reconfigure();
        int buffer_sizes = (int) Math.pow(10, (rate.ordinal() + 2));
        risposteHTTP = new Buffer<>(buffer_sizes, "risposteHTTP");
        // the buffer can't bee too large or requests will be created and the timestamp will be outdated by the time they are actually sent
        richiesteHTTP = new Buffer<>(500, "richiesteHTTP");
        codaOutput = new Buffer<>(buffer_sizes, "queueToDisk");
        File mainLog;
        try {
            if ((mainLog = new File(LOG_PATH + "completion.txt")).exists())
                tryToRestoreFromLog(mainLog);
        } catch (IOException e) {
            logger.warn("Unable to restore from log,starting from scratch");
        }
        tweetIdFiles = Collections.unmodifiableList(tweetIdFiles);
        rehydratedFiles = new ArrayList<>(tweetIdFiles.size());
        tweetIdFiles.forEach(file -> rehydratedFiles.add(new File(pathSalvataggio + file.getName().replaceAll("\\.txt", ".json.gz"))));
        rehydratedFiles = Collections.unmodifiableList(rehydratedFiles);
        executor = new RequestExecutor(richiesteHTTP, risposteHTTP, (rate.ordinal() + 1) * 15);
        supplier = new RequestsSupplier(tokens, richiesteHTTP);
        ioHandler = new IOHandler(codaOutput, tweetIdFiles.size(), instance);
        parser = new ResponseParserParallel(executor, risposteHTTP, codaOutput, LOG_PATH);
    }


    File getOutputFile(int index) {
        return rehydratedFiles.get(index);
    }

    public void hydrate() {
        this.init();
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        parser.attachHandler(supplier);
        executorService.execute(supplier);
        executorService.execute(executor);
        executorService.execute(ioHandler);
        parser.startWorkers();
        int target;
        List<Long> ids;
        int index = 0;
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
        allComponentsHaveBeenSetup = 0;
    }


    public static void main(String... args) throws Exception {
        if (args.length != 6) {
            System.out.println("Not enough arguments:" + Arrays.toString(args));
            System.out.println("Use : fileId tokensFile logFolder saveFolder config.xmlPath parsingVelocity");
            System.out.println("Values for velocity : " + Arrays.toString(exec_setting.values()));
            System.out.println("[Use slow if you have less than 10 keys]");
            System.out.println("[Disclaimer -> fast will probably result in a lot of timeouts at first]");
            return;
        }
        getInstance().setFileList(args[0]).setTokens(args[1]).setLogPath(args[2]).setSavePath(args[3])
                .setLogConfigFile(args[4]).setRate(exec_setting.valueOf(args[5])).hydrate();
    }
}