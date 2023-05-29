package com.codefromjames;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class App2 {
    final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    final static int MAX_CPU_TARGET = CPU_COUNT * 4;
    final static Duration TARGET_OPERATION_INTERVAL = Duration.ofMillis(250);

    public static void main(String[] args) {
        final long targetCount = findTargetCountForInterval(TARGET_OPERATION_INTERVAL);

        // Warm-up
        {
            System.out.println("Running warm-up...");
            IntStream.range(0, 3).forEach(i -> targetResultForThreadCount(targetCount, MAX_CPU_TARGET));
            System.out.println("Completed warm-up");
            System.out.println("======================================================================");
        }

        final List<WorkerResult> results = IntStream.range(1, MAX_CPU_TARGET + 1)
                .mapToObj(threadCount -> targetResultForThreadCount(targetCount, threadCount))
                .collect(Collectors.toList());

        System.out.println("Outputting results: data.csv");
        final CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(',')
                .setQuote('"')
                .setHeader("Threads", "DurationMs", "ImpactRatio", "StdDev")
                .build();
        try (Writer writer = new FileWriter("data.csv");
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (WorkerResult result : results) {
                printer.printRecord(
                        result.getTargetThreads(),
                        result.getTotalDuration().toMillis(),
                        result.getImpactRatio(),
                        result.getDescriptiveStatistics().getStandardDeviation());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static WorkerResult targetResultForThreadCount(long targetCount, int threadCount) {
        System.out.println("Running for " + threadCount + " threads...");
        final List<Worker> workers = IntStream.range(0, threadCount)
                .mapToObj(i -> new Worker(targetCount))
                .collect(Collectors.toList());
        final ExecutorService executor = Executors.newFixedThreadPool(workers.size());
        final StopWatch sw = StopWatch.createStarted();
        try {
            final List<CompletableFuture<Void>> futures = workers.stream()
                    .map(w -> CompletableFuture.runAsync(w, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            sw.stop();
            workers.forEach(w -> System.out.println(w.getDuration()));

            final Duration resultingInterval = Duration.ofMillis(sw.getTime());
            final WorkerResult result = new WorkerResult(targetCount, threadCount, TARGET_OPERATION_INTERVAL, resultingInterval, workers);

            System.out.println("Total runtime: " + result.getTotalDuration());
            System.out.println("Total impact ratio: " + result.getImpactRatio());
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Performs simple worker iterations searching for a target execution time on a single thread.
     */
    private static long findTargetCountForInterval(Duration targetOperationTime) {
        Objects.requireNonNull(targetOperationTime);
        if (targetOperationTime.toMillis() <= 0) {
            throw new IllegalArgumentException("Target operation time must be at least 1 millisecond");
        }

        final long targetMillis = targetOperationTime.toMillis();
        final int targetConfidence = 10;
        final double targetThreshold = 0.03;
        final Duration upper = Duration.ofMillis((long) (targetMillis * (1 + targetThreshold)));
        final Duration lower = Duration.ofMillis((long) (targetMillis * (1 - targetThreshold)));

        long interval = 10_000_000;
        int confidence = 0;
        System.out.println("Target seeking from " + interval);
        while (true) {
            final Worker worker = new Worker(interval);
            worker.run();
            final Duration last = worker.getDuration();

            double ratio = (double) last.toMillis() / (double) targetMillis;
            if (ratio <= 0) {
                ratio = 0.1; // Weird math - 10x it to boost to a positive # of milliseconds
            }
            final long newTarget = (long) (interval / ratio);

            if (last.compareTo(upper) <= 0 && last.compareTo(lower) >= 0) {
                // Just right
                if (++confidence >= targetConfidence) {
                    System.out.println("Last: " + last + " - Selected " + interval);
                    return interval;
                }
                System.out.println("Last: " + last + " - Repeating at " + interval + " / " + confidence);
            } else {
                interval = newTarget;
                confidence = 0;
                System.out.println("Last: " + last + " - Moving to " + interval + " from ratio " + ratio);
            }
        }
    }

    static class Worker implements Runnable {
        private final long target;
        private Duration duration;

        Worker(long target) {
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

        public Duration getDuration() {
            return duration;
        }
    }

    static class WorkerResult {
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
}
