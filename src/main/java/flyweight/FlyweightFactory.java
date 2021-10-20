package flyweight;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FlyweightFactory {
    //non proprio ideale implementare collection
    public static class Flyweight implements Collection<byte[]> {

        private ArrayList<byte[]> arrayList = new ArrayList<>(100);
        private final int index;
        private int actualElementsInside = 0;


        public Flyweight(int index) {
            this.index = index;
        }

        public ArrayList<byte[]> getArrayList() {
            return arrayList;
        }

        @Override
        public int size() {
            return actualElementsInside;
        }

        @Override
        public boolean isEmpty() {
            return arrayList.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return arrayList.contains(o);
        }

        @NotNull
        @Override
        public Iterator<byte[]> iterator() {
            return arrayList.iterator();
        }

        @NotNull
        @Override
        public Object[] toArray() {
            return arrayList.toArray();
        }

        @NotNull
        @Override
        public <T> T[] toArray(@NotNull T[] a) {
            return arrayList.toArray((T[]) new Object[a.length]);
        }


        @Override
        public boolean add(byte[] bytes) {
            actualElementsInside++;
            return arrayList.add(bytes);
        }

        @Override
        public boolean remove(Object o) {
            actualElementsInside--;
            return arrayList.remove(o);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return arrayList.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends byte[]> c) {
            return arrayList.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return arrayList.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return arrayList.retainAll(c);
        }

        @Override
        public void clear() {
            actualElementsInside = 0;
            arrayList.clear();
        }
    }

    private Flyweight[] flyweightPool;
    private int poolSize;
    private HashMap<Thread, Integer> threadFlyweight = new HashMap<>();
    private boolean[] usableFlyweights;
    private Lock lock = new ReentrantLock();
    private Condition areThereUsableFlyweights = lock.newCondition();
    private int busyFlyweights = 0;

    public FlyweightFactory(int poolSize) {
        if (poolSize <= 0) throw new IllegalArgumentException();
        this.poolSize = poolSize;
        usableFlyweights = new boolean[poolSize];

        flyweightPool = new Flyweight[poolSize];
        for (int i = 0; i < poolSize; i++)
            flyweightPool[i] = new Flyweight(i);
    }

    @SuppressWarnings("all")
    public Flyweight getFlyweight() {
        Flyweight retValue = null;
        lock.lock();
        try {
            while (busyFlyweights == poolSize)
                areThereUsableFlyweights.await();
            int i;
            for (i = 0; i < poolSize && usableFlyweights[i]; i++) ;
            busyFlyweights++;
            retValue = flyweightPool[i];
            usableFlyweights[i] = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return retValue;
    }

    public void usedFlyweight(Flyweight flyweight) {
        lock.lock();
        try {
            usableFlyweights[flyweight.index] = false;
            if (busyFlyweights == poolSize)
                areThereUsableFlyweights.signalAll();
            busyFlyweights--;
            flyweight.clear();
        } finally {
            lock.unlock();
        }
    }
}
