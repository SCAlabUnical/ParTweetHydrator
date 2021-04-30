package lightweightVersion;


import deprecated.RejectedRequestsHandler;
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
    private List<File> tweetIdFiles;
    private final String LOG_PATH, pathSalvataggio;
    private File risultato;
    private ResponseParserParallel parser;
    private RequestExecutor executor;
    private RequestsSupplier supplier;
    private IOHandler ioHandler;
    private Buffer<List<Long>> worksetQueue = new Buffer<>(1000);
    private Buffer<WrappedCompletableFuture> risposteHTTP = new Buffer<>(1000);
    private Buffer<WrappedHTTPRequest> richiesteHTTP = new Buffer<>(1000);
    private Buffer<ByteAndDestination> codaOutput = new Buffer<>(1000);

    public enum exec_setting {SLOW, FAST, VERY_FAST, MAX}


    private void tryToRestoreFromLog(File restoreFrom) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(restoreFrom));
        try {
            String line = "";
            StringTokenizer st;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                st = new StringTokenizer(line, "$");
                if (st.countTokens() < 2) logger.warn("Restore from log aborted,file structure unknown");
                st.nextToken();
                tweetIdFiles.remove(new File(line = st.nextToken().trim().replaceAll("\\.json\\.gz", ".txt")));
                logger.warn("Removed " + line + " from the work queue");
            }
            tweetIdFiles = Collections.unmodifiableList(tweetIdFiles);
        } finally {
            br.close();
        }
    }


    public Hydrator(String tweetFiles, String tokenFile, String LOG_PATH, String save, String configPath, exec_setting value) throws IOException {
        this.LOG_PATH = LOG_PATH;
        this.pathSalvataggio = save;
        try {
            tweetIdFiles = loadFiles(new File(tweetFiles));
            File mainLog;
            if ((mainLog = new File(LOG_PATH + "completion.txt")).exists())
                tryToRestoreFromLog(mainLog);
            tokens = loadAllTokens(tokenFile);
        } catch (IOException | Key.UnusableKeyException e) {
            logger.fatal(e.getMessage());
            logger.fatal("Errore sull'inizializzazione dei file,esecuzione abortita");
            throw new UnusableHydratorException();
        }
        executor = new RequestExecutor(richiesteHTTP, risposteHTTP, (value.ordinal() + 1) * 15);
        supplier = new RequestsSupplier(tokens, richiesteHTTP);
        ioHandler = new IOHandler(codaOutput,tweetIdFiles.size());
        parser = new ResponseParserParallel(executor, risposteHTTP, codaOutput, LOG_PATH);
        System.setProperty("log4j.configurationFile", configPath);
        System.setProperty("log4j_logPath", LOG_PATH);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        ctx.reconfigure();
    }


    private void hydrate() {
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        parser.attachHandler(supplier);
        parser.startWorkers();
        executorService.execute(supplier);
        executorService.execute(executor);
        executorService.execute(ioHandler);
        List<Long> ids;
        for (File tweetIds : tweetIdFiles) {
            try {
                risultato = new File(pathSalvataggio + tweetIds.getName().replaceAll("\\.txt", ".json.gz"));
                ids = loadTweetIds(tweetIds.getAbsolutePath());
                int target = (int) Math.ceil((double) ids.size() / 100);
                ioHandler.setTargetPerFile(risultato, target);
                supplier.workSetRefresh(new WorkKit(ids, risultato));
                logger.info("Added " + tweetIds.getName() + " to the work queue");
            } catch (InterruptedException | IOException e) {
                logger.fatal(e.getMessage());
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
        new Hydrator(args[0], args[1], args[2],args[3],args[4],exec_setting.valueOf(args[5])).hydrate();
    }
}