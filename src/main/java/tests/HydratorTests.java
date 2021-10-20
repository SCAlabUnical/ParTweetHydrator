package tests;

import dataStructures.Buffer;
import dataStructures.ByteAndDestination;
import dataStructures.WorkKit;
import dataStructures.WrappedCompletableFuture;
import hydrator.*;
import key.AbstractKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import strategy.ParsingStrategy;
import strategy.VerboseParsing;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static utils.utils.loadTweetIds;

class HydratorTests {
    private static String tokensPath = "C:\\Users\\39351\\Desktop\\tokens_tests.xml", inputPath = "E:\\Tweet\\us-pres-elections-2020\\Comparison_Folder\\generated_range_1000.txt",
            savePath = "E:\\Tweet\\Reidratati\\tst\\";
    private static AbstractKey[] keys;
    private static RequestExecutor executor;
    private static RequestsSupplier supplier;
    private static IOHandler ioHandler;
    private static ResponseParser parser;
    private static Buffer<WrappedCompletableFuture> completableFutureBuffer;
    private static Buffer<ByteAndDestination> queueToDisk;
    private static int files, target = 0;
    private static List<File> inputFiles;
    private static ExecutorService executorService;
    private static ParsingStrategy strategy;
    private static Semaphore supplierSemaphore, parserSemaphore, handlerSemaphore;
    private static long idsTotal;
    private static Thread supplierThread, handlerThread;

    @BeforeAll
    static void setupClass() throws IOException {
        System.out.println("setting up....");
        keys = utils.utils.loadAllTokens(tokensPath);
        inputFiles = utils.utils.loadFiles(new File(inputPath));
        files = inputFiles.size();
    }

    @BeforeEach
    void beforeEach() throws InterruptedException, IOException {
        completableFutureBuffer = new Buffer<>();
        queueToDisk = new Buffer<>(files);
        executor = new RequestExecutor(completableFutureBuffer, Hydrator.exec_setting.FAST);
        supplier = new RequestsSupplier(keys, executor);
        ioHandler = new IOHandler(queueToDisk, files);
        ioHandler.setOriginalFileList(inputFiles);
        ioHandler.setSavePath(savePath);
        parser = new ResponseParser(executor, completableFutureBuffer, queueToDisk, strategy = new VerboseParsing());
        parser.attachHandler(supplier);
        executorService = Executors.newCachedThreadPool();
        supplier.setFilesToWorkOn(files);
          supplierThread = new Thread(supplier);
          handlerThread = new Thread(ioHandler);
        supplierThread.start();
        handlerThread.start();
        parser.startWorkers();
        target = 0;
        List<String> ids;
        int index = 0;
        idsTotal = 0;
        for (File inputFile : inputFiles) {
            ids = loadTweetIds(inputFile.getAbsolutePath());
            idsTotal += ids.size();
            target += (int) Math.ceil((double) ids.size() / 100);
            ioHandler.setTargetPerFile(index, target);
            supplier.workSetRefresh(new WorkKit(ids, index++));
        }
    }

    @Test
    public void testSupplierandExecutor() throws InterruptedException {
        supplierThread.join();
        assert supplier.getTotalTweets() == idsTotal && executor.getRequests() == target;
    }


    @Test
    public void testParser() throws InterruptedException {
        //test numero risposte
        handlerThread.join();
        assert VerboseParsing.getUniqueIds().size() == idsTotal;
    }

    @Test
    public void testIOHandler() throws InterruptedException, IOException {
        handlerThread.join();
        long tweets = 0;
        GZIPInputStream inputStream;
        FileOutputStream outputStream;
        BufferedReader br = null;
        File f;
        for (File inputFile : inputFiles) {
            outputStream = new FileOutputStream(f = new File(savePath + inputFile.getName() + " dec"));
            inputStream = new GZIPInputStream(new FileInputStream(savePath + inputFile.getName().replaceAll("\\.txt", ".json.gz")));
            outputStream.write(inputStream.readAllBytes());
            outputStream.flush();
            outputStream.close();
            br = new BufferedReader(new FileReader(f));
            while (br.readLine() != null) tweets++;
        }
        br.close();
        System.out.println(tweets + " " + parser.getRehydratedTweets());
        assert tweets == parser.getRehydratedTweets();
    }

}
