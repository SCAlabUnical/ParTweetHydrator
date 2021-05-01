package lightweightVersion;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

public class IOHandler implements Runnable {
    private Logger logger = LogManager.getLogger(IOHandler.class.getName());
    private Logger completionLogger = LogManager.getLogger("completionTracker");
    private GZIPOutputStream currentCompressedStream;
    private BufferedOutputStream currentOutput;
    private int risposte = 0, fileCounter = 0, fileIndex = Integer.MIN_VALUE;
    private long[] acks, targetPerFile;
    private final Buffer<ByteAndDestination> buffer;
    private final Hydrator hydrator;
    private OutputStream[][] fileStreams;

    public IOHandler(Buffer<ByteAndDestination> buffer, int files, Hydrator hydrator) {
        this.buffer = buffer;
        this.acks = new long[files];
        this.hydrator = hydrator;
        this.targetPerFile = new long[files];
        this.fileStreams = new OutputStream[files][2];
        Arrays.fill(targetPerFile, -1L);
    }


    void setTargetPerFile(int file, int target) {
        targetPerFile[file] = (((long) target * (target - 1)) / 2);
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
                    assert targetPerFile[fileIndex] > 0;
                    currentTarget = targetPerFile[fileIndex];
                    if (fileStreams[fileIndex] == null) {
                        streams = new OutputStream[2];
                        streams[0] = new BufferedOutputStream(new FileOutputStream(hydrator.getOutputFile(fileIndex), false));
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
                if (acks[fileIndex] == currentTarget)
                    try {
                        completionLogger.log(Level.forName("COMPLETED", 650), " $ " + hydrator.getOutputFile(fileIndex).getName() + " $ ");
                        currentCompressedStream.flush();
                        currentCompressedStream.close();
                        currentOutput.flush();
                        currentOutput.close();
                        fileStreams[fileIndex] = null;
                    } catch (Exception e) {
                        logger.fatal(e.getMessage());
                        System.out.println(e.getMessage());
                    }

            }
        }
    }
}
