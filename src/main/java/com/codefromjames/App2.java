package com.codefromjames;

import org.apache.commons.lang3.time.StopWatch;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
            final long targetNumber = 10_000_000_000L;
            final int targetWorkers = Math.max(1, CPU_COUNT / 2);
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
                System.out.println("Total runtime: " + Duration.ofMillis(sw.getTime()));
            } finally {
                executor.shutdownNow();
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
