package lightweightVersion;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public final class Buffer<T> {
    private final int id = instanceTracker.getAndIncrement();
    private static final Logger logger = LogManager.getLogger(Buffer.class.getName());
    private final int BUFFER_SIZE;
    private final static AtomicInteger instanceTracker = new AtomicInteger(0);
    private T[] buffer;
    private int putIndex = 0, getIndex = 0, size = 0;
    private Semaphore producerSemaphore, consumerSemaphore, mutex;
    private final static List<Buffer> instances = Collections.synchronizedList(new ArrayList<>());
    private String stringId;
    private static Runnable backgroundTracker = () -> {

        while (true) {
            try {
                TimeUnit.SECONDS.sleep(15);
                int length = instances.size();
                for (int i = 0; i < length; i++)
                    logger.trace("<Buffer filled at : " + instances.get(i).size + "/" + instances.get(i).buffer.length + " name " + instances.get(i).stringId + " >");
                logger.trace("");
            } catch (InterruptedException e) {
                System.out.println("Tracker shutting down..");
            }
        }
    };

    private void init() {
        logger.info("Buffer n° " + this.id + " created");
        if (this.id == 0) {
            new Thread(backgroundTracker).start();
            logger.trace("Background tracker started");
        }
        buffer = (T[]) new Object[BUFFER_SIZE];
        producerSemaphore = new Semaphore(BUFFER_SIZE);
        consumerSemaphore = new Semaphore(0);
        mutex = new Semaphore(1);
        instances.add(this);
    }

    public Buffer() {
        BUFFER_SIZE = 500;
        init();
    }

    public Buffer(int size) {
        if (size <= 0) throw new IllegalArgumentException();
        BUFFER_SIZE = size;
        init();
    }

    public Buffer(int size, String stringId) {
        this(size);
        this.stringId = stringId;
    }

    public void put(T item) throws InterruptedException {
        producerSemaphore.acquire();
        mutex.acquire();
        buffer[putIndex] = item;
        size++;
        putIndex = (putIndex + 1 < BUFFER_SIZE) ? putIndex + 1 : 0;
        mutex.release();
        consumerSemaphore.release();
    }

    public boolean putIfAllowed(T item) throws InterruptedException {
        if (!producerSemaphore.tryAcquire())
            return false;
        mutex.acquire();
        buffer[putIndex] = item;
        size++;
        putIndex = (putIndex + 1 < BUFFER_SIZE) ? putIndex + 1 : 0;
        mutex.release();
        consumerSemaphore.release();
        return true;
    }


    public T get() throws InterruptedException {
        consumerSemaphore.acquire();
        mutex.acquire();
        T item = buffer[getIndex];
        buffer[getIndex] = null;
        size--;
        getIndex = (getIndex + 1 < BUFFER_SIZE) ? getIndex + 1 : 0;
        mutex.release();
        producerSemaphore.release();
        return item;
    }
}
