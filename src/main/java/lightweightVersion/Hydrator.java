package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.UnusableHydratorException;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static utils.utils.*;


public class Hydrator {
    private Logger logger = LogManager.getLogger(Hydrator.class.getName());
    private Key[] tokens;
    private List<File> tweetIdFiles, rehydratedFiles;
    private final String LOG_PATH, pathSalvataggio;
    private ResponseParserParallel parser;
    private RequestExecutor executor;
    private RequestsSupplier supplier;
    private IOHandler ioHandler;
    private final Buffer<WrappedCompletableFuture> risposteHTTP;
    private final Buffer<WrappedHTTPRequest> richiesteHTTP;
    private final Buffer<ByteAndDestination> codaOutput;

    public enum exec_setting {SLOW, FAST, VERY_FAST, MAX}

    private static Hydrator instance;

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
            toRemove.forEach(file -> logger.warn("Removed " + file.getName() + " from the work queue \t Reason : already processed"));
        } finally {
            br.close();
        }
    }


    private Hydrator(String tweetFiles, String tokenFile, String LOG_PATH, String save, String configPath, exec_setting value) throws IOException {
        System.setProperty("log4j.configurationFile", configPath);
        System.setProperty("log4j_logPath", LOG_PATH);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        ctx.reconfigure();
        this.LOG_PATH = LOG_PATH;
        this.pathSalvataggio = save;
        int buffer_sizes = (int) Math.pow(10, (value.ordinal() + 2));
        risposteHTTP = new Buffer<>(buffer_sizes, "risposteHTTP");
        // the buffer can't bee too large or requests will be created and the timestamp will be outdated by the time they are actually sent
        richiesteHTTP = new Buffer<>(500, "richiesteHTTP");
        codaOutput = new Buffer<>(buffer_sizes, "queueToDisk");
        logger.info("Buffer sizes : " + buffer_sizes);
        try {
            tweetIdFiles = loadFiles(new File(tweetFiles));
            File mainLog;
            if ((mainLog = new File(LOG_PATH + "completion.txt")).exists())
                tryToRestoreFromLog(mainLog);
            tweetIdFiles = Collections.unmodifiableList(tweetIdFiles);
            rehydratedFiles = new ArrayList<>(tweetIdFiles.size());
            tweetIdFiles.forEach(file -> rehydratedFiles.add(new File(pathSalvataggio + file.getName().replaceAll("\\.txt", ".json.gz"))));
            rehydratedFiles = Collections.unmodifiableList(rehydratedFiles);
            tokens = loadAllTokens(tokenFile);
        } catch (IOException | Key.UnusableKeyException e) {
            logger.fatal(e.getMessage());
            logger.fatal("Errore sull'inizializzazione dei file,esecuzione abortita");
            throw new UnusableHydratorException();
        }
        executor = new RequestExecutor(richiesteHTTP, risposteHTTP, (value.ordinal() + 1) * 15);
        supplier = new RequestsSupplier(tokens, richiesteHTTP);
        ioHandler = new IOHandler(codaOutput, tweetIdFiles.size(), this);
        parser = new ResponseParserParallel(executor, risposteHTTP, codaOutput, LOG_PATH);
    }

    File getOutputFile(int index) {
        return rehydratedFiles.get(index);
    }

    private void hydrate() {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        parser.attachHandler(supplier);
        parser.startWorkers();
        executorService.execute(supplier);
        executorService.execute(executor);
        executorService.execute(ioHandler);
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
        new Hydrator(args[0], args[1], args[2], args[3], args[4], exec_setting.valueOf(args[5])).hydrate();
    }
}