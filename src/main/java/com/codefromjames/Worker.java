package com.codefromjames;

import org.apache.commons.lang3.time.StopWatch;

import java.time.Duration;

public abstract class Worker {
    private final StopWatch stopWatch;

    public Worker() {
        this.stopWatch = new StopWatch();
    }

    public void startClock() {
        stopWatch.reset();
        stopWatch.start();
    }

    public void stopClock() {
        stopWatch.stop();
    }

    public Duration getDuration() {
        return Duration.ofMillis(stopWatch.getTime());
    }
}
