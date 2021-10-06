package lightweightVersion;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;
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

    public IOHandler(Buffer<ByteAndDestination> buffer, int files) {
        this.buffer = buffer;
        this.acks = new long[files];
        this.targetPerFile = new long[files];
        this.fileStreams = new OutputStream[files][];
        Arrays.fill(targetPerFile, -1L);
    }


    void setTargetPerFile(int file, int target) {
        targetPerFile[file] = (((long) target * (target - 1)) / 2);
    }

    long getCurrentAcks() {
        return fileIndex >= 0 ? acks[fileIndex] : 0L;
    }

    public void run() {
        ByteAndDestination curr;
        OutputStream[] streams;
        long start, currentTarget = -1L;
        while (true) {
            try {
                curr = buffer.get();
                start = System.currentTimeMillis();
                if (curr.fileIndex() != fileIndex) {
                    fileIndex = curr.fileIndex();
                    GraphicModule.INSTANCE.updateCurrentFile(fileIndex, (int) acks[fileIndex], (int) targetPerFile[fileIndex]);
                    currentTarget = targetPerFile[fileIndex];
                    if (fileStreams[fileIndex] == null) {
                        streams = new OutputStream[2];
                        streams[0] = new BufferedOutputStream(new FileOutputStream(Hydrator.INSTANCE.getOutputFile(fileIndex), false));
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
                logger.info("[Ack file : " + fileIndex + " n° " + curr.packetN() + " parsed in " + (System.currentTimeMillis() - start) + " ms] [Target " + targetPerFile[fileIndex] + "][Current : " + acks[fileIndex] + "]");
            } catch (InterruptedException | IOException e) {
                System.out.println("ERRORE I/O");
            } finally {
                if (acks[fileIndex] == currentTarget) {
                    Hydrator.INSTANCE.fileCompleted();
                    GraphicModule.INSTANCE.fileDone(fileIndex);
                    try {
                        completionLogger.log(Level.forName("COMPLETED", 650), " $ " + Hydrator.INSTANCE.getOutputFile(fileIndex).getName() + " $ [Time elapsed : " + Hydrator.INSTANCE.setTime(fileIndex) + " ms]");
                        currentCompressedStream.flush();
                        currentCompressedStream.close();
                        currentOutput.flush();
                        currentOutput.close();
                        fileStreams[fileIndex] = null;
                        if (!Hydrator.INSTANCE.areThereMore(fileIndex + 1)) {
                            completionLogger.log(Level.forName("FINISHED", 700), "Rehydrated everything,exiting");
                            System.exit(0);
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
