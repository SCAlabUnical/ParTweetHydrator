package deprecated;

import dataStructures.Buffer;
import dataStructures.WrappedHTTPRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class RejectedRequestsHandler extends Thread {
    private static Logger logger = LogManager.getLogger(RejectedRequestsHandler.class.getName());
    private ReentrantLock lock = new ReentrantLock(true);
    private Stack<WrappedHTTPRequest> rejects = new Stack<>();
    private Condition waitForRequests = lock.newCondition();
    private boolean requestsPending = false;
    private Buffer<WrappedHTTPRequest> executorBuffer;
    private Random random = new Random();

    public RejectedRequestsHandler(Buffer<WrappedHTTPRequest> executorBuffer) {
        this.setDaemon(true);
        this.executorBuffer = executorBuffer;
    }

    void put(WrappedHTTPRequest request) {
        lock.lock();
        try {
            rejects.push(request);
            logger.info("[Ricevuta richiesta da ripetere][File n° " + request.fileInput() + " ][Request n° " + request.reqNumber() + "/" + request.reqTarget() + "]");
            requestsPending = true;
            waitForRequests.signal();
        } finally {
            lock.unlock();
        }

    }

    public void run() {
        while (true) {
            lock.lock();
            try {
                while (!requestsPending)
                    waitForRequests.await();
                //necessariamente non bloccante per evitare deadlock
                if (executorBuffer.putIfAllowed(rejects.peek()))
                    rejects.pop();
                requestsPending = !rejects.isEmpty();
            } catch (InterruptedException e) {
                break;
            } finally {
                lock.unlock();
            }
        }
    }
}
