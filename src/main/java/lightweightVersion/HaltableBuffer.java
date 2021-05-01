package lightweightVersion;

import java.util.concurrent.Semaphore;

public class HaltableBuffer<T> extends Buffer<T> {
    private Semaphore haltConsumers = new Semaphore(1), haltProducers = new Semaphore(1);

    public HaltableBuffer() {
        super();
    }

    public HaltableBuffer(int size) {
        super(size);
    }

    @Override
    public void put(T item) throws InterruptedException {
        haltConsumers.acquire();
        super.put(item);
        haltConsumers.release();
    }

    @Override
    public boolean putIfAllowed(T item) {
        throw new UnsupportedOperationException();
    }

    public synchronized void halt() throws InterruptedException {
        haltConsumers.acquire();
        haltProducers.acquire();
    }

    public synchronized void resume() {
        haltProducers.release();
        haltConsumers.release();
    }

    @Override
    public T get() throws InterruptedException {
        haltConsumers.acquire();
        T item = super.get();
        haltConsumers.release();
        return item;
    }

}
