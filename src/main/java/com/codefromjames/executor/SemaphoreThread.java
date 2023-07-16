package com.codefromjames.executor;

import java.util.concurrent.Semaphore;

public class SemaphoreThread extends Thread {
    private final Semaphore semaphore;

    public SemaphoreThread(Runnable target, String name, Semaphore semaphore) {
        super(target, name);
        this.semaphore = semaphore;
    }

    public interface InterruptibleRunnable {
        void run() throws InterruptedException;
    }

    public interface InterruptibleSupplier<T> {
        T get() throws InterruptedException;
    }

    /* package-private */
    static Semaphore getSemaphore() {
        return getSemaphore(Thread.currentThread());
    }

    /* package-private */
    static Semaphore getSemaphore(Thread thread) {
        if (thread instanceof SemaphoreThread) {
            return ((SemaphoreThread) thread).semaphore;
        }
        throw new IllegalStateException("Thread semaphore not found in "
                + thread.getClass().getName()
                + " \"" + thread.getName() + "\"");
    }

    public static void releaseForOperation(InterruptibleRunnable runnable) throws InterruptedException {
        final Semaphore semaphore = getSemaphore();
        semaphore.release();
        try {
            runnable.run();
        } finally {
            semaphore.acquire();
        }
    }

    public static <T> T releaseForOperation(InterruptibleSupplier<T> supplier) throws InterruptedException {
        final Semaphore semaphore = getSemaphore();
        semaphore.release();
        try {
            return supplier.get();
        } finally {
            semaphore.acquire();
        }
    }
}
