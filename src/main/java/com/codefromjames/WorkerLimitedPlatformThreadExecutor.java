package com.codefromjames;

import org.apache.commons.lang3.time.StopWatch;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WorkerLimitedPlatformThreadExecutor implements WorkerExecutor {
    @Override
    public WorkerResult targetResultForThreadCount(long targetCount, int threadCount) {
        System.out.println("Running for " + threadCount + " threads...");
        final List<Worker> workers = IntStream.range(0, threadCount)
                .mapToObj(i -> new BlockingCountingWorker(targetCount))
                .collect(Collectors.toList());
        final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        final StopWatch sw = StopWatch.createStarted();
        try {
            final List<CompletableFuture<Void>> futures = workers.stream()
                    .map(w -> {
                        w.startClock();
                        return CompletableFuture.runAsync((BlockingCountingWorker) w, executor);
                    })
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            sw.stop();
            workers.forEach(w -> System.out.println(w.getDuration()));

            final Duration resultingInterval = Duration.ofMillis(sw.getTime());
            final WorkerResult result = new WorkerResult(targetCount, threadCount, App.TARGET_OPERATION_INTERVAL, resultingInterval, workers);

            System.out.println("Total runtime: " + result.getTotalDuration());
            System.out.println("Total impact ratio: " + result.getImpactRatio());
            return result;
        } finally {
            executor.shutdownNow();
        }
    }
}
