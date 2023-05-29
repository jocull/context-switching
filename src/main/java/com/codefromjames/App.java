package com.codefromjames;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class App {
    final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    final static int MAX_CPU_TARGET = CPU_COUNT * 4;
    final static Duration TARGET_OPERATION_INTERVAL = Duration.ofMillis(250);

    public static void main(String[] args) {
        final long targetCount = findTargetCountForInterval(TARGET_OPERATION_INTERVAL);
        final WorkerExecutor executorImpl = new WorkerLimitedPlatformThreadExecutor();

        // Warm-up
        {
            System.out.println("Running warm-up...");
            IntStream.range(0, 3).forEach(i -> executorImpl.targetResultForThreadCount(targetCount, MAX_CPU_TARGET));
            System.out.println("Completed warm-up");
            System.out.println("======================================================================");
        }

        final List<WorkerResult> results = IntStream.range(1, MAX_CPU_TARGET + 1)
                .mapToObj(threadCount -> executorImpl.targetResultForThreadCount(targetCount, threadCount))
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
            final BlockingCountingWorker worker = new BlockingCountingWorker(interval);
            worker.startClock();
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
}
