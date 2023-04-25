package com.codefromjames;

import org.apache.commons.lang3.time.StopWatch;

import java.time.Duration;
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

    public static void main(String[] args) {
        // Warm-up
        {
            final Duration targetOperationInterval = Duration.ofMillis(250);
            final long targetNumber = findTargetInterval(targetOperationInterval);
            final int targetWorkers = MAX_CPU_TARGET; // Math.max(1, CPU_COUNT / 2);
            final List<Worker> workers = IntStream.range(0, targetWorkers)
                    .mapToObj(i -> new Worker(targetNumber))
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
                System.out.println("Total runtime: " + resultingInterval);
                System.out.println("Total impact ratio: " + (resultingInterval.toMillis() / targetOperationInterval.toMillis()));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Performs simple worker iterations searching for a target execution time on a single thread.
     */
    private static long findTargetInterval(Duration targetOperationTime) {
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
}
