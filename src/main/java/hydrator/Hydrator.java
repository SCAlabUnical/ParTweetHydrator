package hydrator;


import dataStructures.*;
import key.AbstractKey;
import key.BearerToken;
import key.OAuth1Token;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import utils.orgJsonParsingStrategy;


import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
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
    private ResponseParser parser;
    RequestExecutor executor;
    RequestsSupplier supplier;
    IOHandler ioHandler;
    private Buffer<WrappedCompletableFuture> risposteHTTP;
    private Buffer<WrappedHTTPRequest> richiesteHTTP;
    private Buffer<ByteAndDestination> codaOutput;
    private exec_setting rate;
    private int currentWorkRate;
    private int completedFiles = 0;
    private boolean isRunning = false;

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

    final static float version = 2.2f;

    public int getCurrentWorkRate() {
        return currentWorkRate;
    }

    public void setCurrentWorkRate(int r) {
        currentWorkRate = r;
    }

    public void setLogPath(String logPath) {
        this.LOG_PATH = logPath;
    }

    public void setSavePath(String savePath) {
        if (savePath.charAt(savePath.length() - 1) != System.getProperty("file.separator").charAt(0))
            savePath += System.getProperty("file.separator");
        this.pathSalvataggio = savePath;

    }

    public void setTokens(String pathToAccountsXML) {
        this.tokens = loadAllTokens(pathToAccountsXML);
    }

    public static AbstractKey[] loadAllTokens(String XMLPATH) {
        ArrayList<AbstractKey> tokens = new ArrayList<>(100);
        try {
            int bearer = 0, oauth1 = 0;
            AbstractKey currToken;
            Document xmlDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(XMLPATH));
            String expr = "//BearerToken/text()";
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate(expr, xmlDocument, XPathConstants.NODESET);
            System.out.println("Found " + (bearer = nodeList.getLength()) + " bearer tokes in " + XMLPATH);
            for (int i = 0; i < nodeList.getLength(); i++)
                try {
                    currToken = new BearerToken("Bearer " + nodeList.item(i).getNodeValue().trim());
                    tokens.add(currToken);
                } catch (AbstractKey.UnusableKeyException e) {
                    logger.warn(nodeList.item(i).getNodeValue().trim() + "Is not a valid Bearer token");
                }
            expr = "//Progetto";
            xPath = XPathFactory.newInstance().newXPath();
            nodeList = (NodeList) xPath.evaluate(expr, xmlDocument, XPathConstants.NODESET);
            System.out.println("Found " + (oauth1 = nodeList.getLength()) + " sets of  oauth1 tokes in " + XMLPATH);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node project = nodeList.item(i);
                String[] oauthValues = new String[oauth_1_fields.length];
                for (int j = 0; j < oauthValues.length; j++) {
                    xPath = XPathFactory.newInstance().newXPath();
                    oauthValues[j] = ((String) xPath.evaluate(("./" + oauth_1_fields[j] + "/text()"), project, XPathConstants.STRING)).trim();
                }
                try {
                    currToken = new OAuth1Token(Arrays.copyOf(oauthValues, oauthValues.length));
                    tokens.add(currToken);
                } catch (AbstractKey.UnusableKeyException e) {
                    logger.warn(Arrays.toString(oauthValues) + "Is not a valid set of oauth1 tokens");
                }
            }
            Hydrator.INSTANCE.setCurrentWorkRate((bearer * 300 + oauth1 * 900) * 100);
        } catch (SAXException | ParserConfigurationException | XPathExpressionException | IOException e) {
            throw new RuntimeException("File structure invalid,check github for a fac-simile");
        }
        Collections.shuffle(tokens, new Random(System.currentTimeMillis()));
        return tokens.toArray(new AbstractKey[0]);
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
                logger.info("[Removed " + file.getName() + " from the work queue][Reason : already processed]");
            });
        }
    }


    private void init() {
        ClassLoader classLoader = Hydrator.INSTANCE.getClass().getClassLoader();
        if (LOG_PATH == null)
            LOG_PATH = System.getProperty("java.io.tmpdir");
        if (LOG_PATH.charAt(LOG_PATH.length() - 1) != System.getProperty("file.separator").charAt(0))
            LOG_PATH += System.getProperty("file.separator");
        System.setProperty("log4j_logPath", LOG_PATH);
        org.apache.logging.log4j.core.LoggerContext ctx =
                (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
        try {
            ctx.setConfigLocation(classLoader.getResource("logger_conf.xml").toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        ctx.reconfigure();
        int buffer_sizes = (int) Math.pow(10, (rate.ordinal() + 2));
        risposteHTTP = new Buffer<>(buffer_sizes, "risposteHTTP");
        // the buffer can't bee too large or requests will be created and the timestamp will be outdated by the time they are actually sent
        richiesteHTTP = new Buffer<>(100, "richiesteHTTP");
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
        for (int i = 0; i < tweetIdFiles.size(); i++)
            timeElapsed.put(i, System.currentTimeMillis());
        tweetIdFiles.forEach(file -> rehydratedFiles.add(new File(pathSalvataggio + file.getName().replaceAll("\\.txt", ".json.gz"))));
        rehydratedFiles = Collections.unmodifiableList(rehydratedFiles);
        executor = new RequestExecutor(risposteHTTP, rate);
        supplier = new RequestsSupplier(tokens, richiesteHTTP, executor);
        ioHandler = new IOHandler(codaOutput, tweetIdFiles.size());
        parser = new ResponseParser(executor, risposteHTTP, codaOutput, LOG_PATH, new orgJsonParsingStrategy());
        GraphicModule.INSTANCE.waitForSetup.release();
    }

    void setStartTime(int fileIndex) {
        timeElapsed.put(fileIndex, System.currentTimeMillis());
    }

    long setTime(int fileIndex) {
        long old = timeElapsed.get(fileIndex), ret;
        timeElapsed.put(fileIndex, ret = System.currentTimeMillis() - old);
        return ret;
    }

    boolean areThereMore(int fileIndex) {
        return rehydratedFiles.size() > fileIndex;
    }

    File getOutputFile(int index) {
        return rehydratedFiles.get(index);
    }


    public void hydrate() {
        isRunning = true;
        GraphicModule.INSTANCE.statusPanel.start();
        this.init();
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        parser.attachHandler(supplier);
        executorService.execute(supplier);
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
        isRunning = false;
        GraphicModule.INSTANCE.statusPanel.stop();
    }

    boolean isSetup() {
        return tweetIdFiles != null && pathSalvataggio != null && tokens != null && rate != null;
    }

    public static void main(String... args) {
        //reference per effeture il loading delle enum
        System.out.println("Hydrator starting");
        Object x = GraphicModule.INSTANCE;
        Object y = Hydrator.INSTANCE;
    }
}