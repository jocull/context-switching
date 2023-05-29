package com.codefromjames;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

public class WorkerResult {
    private final long targetCount;
    private final int targetThreads;
    private final Duration targetDuration;
    private final Duration totalDuration;
    private final List<Worker> workers;
    private final DescriptiveStatistics descriptiveStatistics;

    WorkerResult(long targetCount,
                 int targetThreads,
                 Duration targetDuration,
                 Duration totalDuration,
                 Collection<Worker> workers) {
        this.targetCount = targetCount;
        this.targetThreads = targetThreads;
        this.targetDuration = targetDuration;
        this.totalDuration = totalDuration;
        this.workers = List.copyOf(workers);

        descriptiveStatistics = new DescriptiveStatistics();
        workers.forEach(w -> descriptiveStatistics.addValue(w.getDuration().toMillis()));
    }

    public long getTargetCount() {
        return targetCount;
    }

    public int getTargetThreads() {
        return targetThreads;
    }

    public Duration getTotalDuration() {
        return totalDuration;
    }

    public List<Worker> getWorkers() {
        return workers;
    }

    public double getImpactRatio() {
        return (double) totalDuration.toMillis() / (double) targetDuration.toMillis();
    }

    public DescriptiveStatistics getDescriptiveStatistics() {
        return descriptiveStatistics;
    }
}
