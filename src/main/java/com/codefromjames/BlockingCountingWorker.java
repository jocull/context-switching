package com.codefromjames;

import org.apache.commons.lang3.time.StopWatch;

import java.time.Duration;

public class BlockingCountingWorker extends Worker implements Runnable {
    private final long target;

    BlockingCountingWorker(long target) {
        this.target = target;
    }

    @Override
    public void run() {
        long i = 0;
        while (i < target) {
            i++;
        }
        stopClock();
    }
}
