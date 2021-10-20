package hydrator;


import dataStructures.Buffer;
import dataStructures.ByteAndDestination;
import flyweight.FlyweightFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.zip.GZIPOutputStream;

public class IOHandler implements Runnable {
    private Logger logger = LogManager.getLogger(IOHandler.class.getName());
    private Logger completionLogger = LogManager.getLogger("completionTracker");
    private GZIPOutputStream currentCompressedStream;
    private BufferedOutputStream currentOutput;
    private int risposte = 0, fileCounter = 0, fileIndex = -1;
    private long[] acks, targetPerFile;
    private final Buffer<ByteAndDestination> buffer;
    private OutputStream[][] fileStreams;
    private FlyweightFactory flyweightFactory;
    private String savePath;
    private List<File> originals;
    private ResponseParser parser;

    public void setParser(ResponseParser parser) {
        this.parser = parser;
    }

    public IOHandler(Buffer<ByteAndDestination> buffer, int files) {
        this.buffer = buffer;
        this.acks = new long[files];
        this.targetPerFile = new long[files];
        this.fileStreams = new OutputStream[files][];
        Arrays.fill(targetPerFile, -1L);
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public IOHandler(Buffer<ByteAndDestination> buffer, int files, FlyweightFactory flyweightFactory) {
        this.flyweightFactory = flyweightFactory;
        this.buffer = buffer;
        this.acks = new long[files];
        this.targetPerFile = new long[files];
        this.fileStreams = new OutputStream[files][];
        Arrays.fill(targetPerFile, -1L);
    }


    public boolean isDone() {
        for (int i = 0; i < targetPerFile.length; i++)
            if (acks[i] != targetPerFile[i]) return false;
        return true;
    }

    public void setTargetPerFile(int file, int target) {
        targetPerFile[file] = (((long) target * (target - 1)) / 2);
    }

    long getCurrentAcks() {
        return fileIndex >= 0 ? acks[fileIndex] : 0L;
    }

    public void setOriginalFileList(List<File> ogs) {
        originals = ogs;
    }

    public int[] getCurrentFile() {
        if (fileIndex < 0) return null;
        return new int[]{fileIndex, (int) acks[fileIndex], (int) targetPerFile[fileIndex]};
    }

    public void run() {
        ByteAndDestination curr;
        OutputStream[] streams;
        long start, currentTarget = -1L;
        boolean keepGoing = true;
        while (keepGoing) {
            try {
                curr = buffer.get();
                start = System.currentTimeMillis();
                if (curr.fileIndex() != fileIndex) {
                    fileIndex = curr.fileIndex();
                    //       GraphicModule.INSTANCE.updateCurrentFile(fileIndex, (int) acks[fileIndex], (int) targetPerFile[fileIndex]);
                    currentTarget = targetPerFile[fileIndex];
                    if (fileStreams[fileIndex] == null) {
                        streams = new OutputStream[2];
                        streams[0] = new BufferedOutputStream(new FileOutputStream((savePath + originals.get(fileIndex).getName().replaceAll("\\.txt", ".json.gz")), false));
                        streams[1] = new GZIPOutputStream(streams[0]);
                        fileStreams[fileIndex] = streams;
                    }
                    streams = fileStreams[fileIndex];
                    currentOutput = (BufferedOutputStream) streams[0];
                    currentCompressedStream = (GZIPOutputStream) streams[1];
                }
                acks[fileIndex] += curr.packetN();
                for (byte[] array : curr.array())
                    currentCompressedStream.write(array);
                if (curr.array() instanceof FlyweightFactory.Flyweight flyweight)
                    flyweightFactory.usedFlyweight(flyweight);
                logger.info("[Ack file : " + fileIndex + " n° " + curr.packetN() + " parsed in " + (System.currentTimeMillis() - start) + " ms] [Target " + targetPerFile[fileIndex] + "][Current : " + acks[fileIndex] + "]");
            } catch (InterruptedException | IOException e) {
                System.out.println("ERRORE I/O");
            } finally {
                if (acks[fileIndex] == currentTarget) {
                    try {
                        completionLogger.log(Level.forName("COMPLETED", 650), " $ " + (originals.get(fileIndex).getName().replaceAll("\\.txt", ".json.gz")) + " $ [Time elapsed : " + Hydrator.INSTANCE.setTime(fileIndex) + " ms]");
                        currentCompressedStream.flush();
                        currentCompressedStream.close();
                        currentOutput.flush();
                        currentOutput.close();
                        fileStreams[fileIndex] = null;
                        if (originals.size() == fileIndex + 1) {
                            keepGoing = false;
                            if (parser != null)
                                parser.shutDown();
                            completionLogger.log(Level.forName("FINISHED", 700), "Rehydrated everything,exiting");
                        }
                    } catch (Exception e) {
                        logger.fatal(e.getMessage());
                        System.out.println(e.getMessage());
                    }
                }

            }
        }
    }
}
