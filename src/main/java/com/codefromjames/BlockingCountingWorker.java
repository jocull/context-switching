package com.codefromjames;

import org.apache.commons.lang3.time.StopWatch;

import java.time.Duration;

public class BlockingCountingWorker implements Runnable, Worker {
    private final long target;
    private Duration duration;

    BlockingCountingWorker(long target) {
        this.target = target;
    }

    @Override
    public void run() {
        final StopWatch sw = StopWatch.createStarted();
        long i = 0;
        while (i < target) {
            i++;
        }
        duration = Duration.ofMillis(sw.getTime());
    }

    @Override
    public Duration getDuration() {
        return duration;
    }
}
