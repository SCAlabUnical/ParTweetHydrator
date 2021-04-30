package lightweightVersion;


import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Buffer<T> {
    private final int BUFFER_SIZE;
    private final static AtomicInteger instanceTracker = new AtomicInteger(0);
    private final int instanceID = instanceTracker.getAndIncrement();
    private T[] buffer;
    private int putIndex = 0, getIndex = 0, size = 0;
    private Semaphore producerSemaphore, consumerSemaphore, mutex;


    private void init() {
        buffer = (T[]) new Object[BUFFER_SIZE];
        producerSemaphore = new Semaphore(BUFFER_SIZE);
        consumerSemaphore = new Semaphore(0);
        mutex = new Semaphore(1);
        Runnable logger = () -> {
            boolean tookIt;
            while (true) {
                tookIt = false;
                try {
                    TimeUnit.SECONDS.sleep(5);
                    mutex.acquire();
                    tookIt = true;
                    System.out.println("<Buffer filled at : " + size + " max : " + BUFFER_SIZE + "> <isFull : " + (size == BUFFER_SIZE) + ">");
                } catch (Throwable e) {

                } finally {
                    if (tookIt) mutex.release();
                }
            }
        };
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
