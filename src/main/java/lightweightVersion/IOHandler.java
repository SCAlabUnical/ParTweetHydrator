package lightweightVersion;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

public class IOHandler implements Runnable {
    private Logger tracker = LogManager.getLogger("hydrator_tracker");
    private Logger logger = LogManager.getLogger(IOHandler.class.getName());
    private Logger completionLogger = LogManager.getLogger("completionTracker");
    private HashMap<File, OutputStream[]> fileStreams = new HashMap<>();
    private GZIPOutputStream currentCompressedStream;
    private BufferedOutputStream currentOutput;
    private int risposte = 0, fileCounter = 0, fileIndex = 0;
    private HashMap<File, Long> targetPerFile = new HashMap<>();
    private HashMap<File, Integer> fileIndexDB = new HashMap<>();
    private long[] acks;
    private final Buffer<ByteAndDestination> buffer;

    public IOHandler(Buffer<ByteAndDestination> buffer, int files) {
        this.buffer = buffer;
        acks = new long[files];
    }


    void setTargetPerFile(File f, int target) {
        targetPerFile.put(f, ((long) target * (target - 1)) / 2);
    }


    public void run() {
        ByteAndDestination curr = null;
        OutputStream[] streams;
        long start, currentTarget = -1L;
        File prev = new File("placeholder!%/%/&$%&");
        while (true) {
            try {
                curr = buffer.get();
                start = System.currentTimeMillis();
                if (!curr.output().equals(prev)) {
                    if (!fileIndexDB.containsKey(curr.output()))
                        fileIndexDB.put(curr.output(), fileIndex = fileCounter++);
                    fileIndex = fileIndexDB.get(curr.output());
                    currentTarget = targetPerFile.get(curr.output());
                    if (!fileStreams.containsKey(curr.output())) {
                        streams = new OutputStream[2];
                        streams[0] = new BufferedOutputStream(new FileOutputStream(curr.output(), false));
                        streams[1] = new GZIPOutputStream(streams[0]);
                        fileStreams.put(curr.output(), streams);
                    }
                    streams = fileStreams.get(curr.output());
                    currentOutput = (BufferedOutputStream) streams[0];
                    currentCompressedStream = (GZIPOutputStream) streams[1];
                    prev = curr.output();
                }
                acks[fileIndex] += curr.packetN();
                for (byte[] array : curr.array())
                    currentCompressedStream.write(array);
                logger.info("[Ack file : " + fileIndex + " n° " + curr.packetN() + " parsed in " + (System.currentTimeMillis() - start) + " ms] [Target " + targetPerFile.get(curr.output()) + "][Current : " + acks[fileIndex] + "]");
            } catch (InterruptedException | IOException e) {
                System.out.println("ERRORE I/O");
            } finally {
                if (acks[fileIndex] == currentTarget)
                try {
                    tracker.trace("File: " + curr.output() + " hydrated");
                    completionLogger.log(Level.forName("COMPLETED", 650), " $ " + curr.output().getName() + " $ ");
                    currentCompressedStream.flush();
                    currentCompressedStream.close();
                    currentOutput.flush();
                    currentOutput.close();
                    fileStreams.put(curr.output(), null);
                } catch (Exception e) {
                    logger.fatal(e.getMessage());
                    System.out.println(e.getMessage());
                }

            }
        }
    }
}
