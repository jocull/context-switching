package com.codefromjames;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class App {
    final static int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    final static int MAX_CPU_TARGET = CPU_COUNT * 4;
    private static final Semaphore SEMAPHORE = new Semaphore(MAX_CPU_TARGET);

    // TODO: I want to change the implementation. Here are some thoughts:
    // - The target should be a fixed count. It should be something high enough that it takes a significant amount of time, but not so high that it is hard to achieve.
    // - Maybe this target count can be determined during warm-up?
    // - After the count is set, it should walk up the number of concurrent threads to measure the impacts on the operations.
    // - After that, we can look at tuning them somehow to reduce concurrency, or add in some kind of a sleep or I/O step to make it more "realistic".
    // - The stats generation I think is still good. I like to chart the CSVs in Excel/Sheets to visualize the results.

    public static void main(String[] args) throws IOException {
        // Warm-up
        {
            IntStream.range(0, 5)
                    .forEach(i -> {
                        final WorkerResults results = runForThreadCount(Math.max(1, CPU_COUNT / 2), Duration.ofSeconds(20), "Warming-up #" + i);
                        System.out.println("Warm-up #" + i + " results: " + results);
                        try {
                            System.out.println("Breathing...");
                            Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        System.out.println("Warm-up complete - here we go for real!");
        System.out.println();
        final List<WorkerResults> resultsList = new ArrayList<>(MAX_CPU_TARGET);
        for (int threadCount = 1; threadCount <= MAX_CPU_TARGET; threadCount++) {
            final WorkerResults results = runForThreadCount(threadCount, Duration.ofSeconds(30), "Testing");
            System.out.println("Testing results: " + results);
            resultsList.add(results);
        }

        System.out.println("Outputting results: data.csv");
        final CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(',')
                .setQuote('"')
                .setHeader("Threads", "Sum", "Spread", "Min", "Max", "Median", "StdDev")
                .build();
        try (Writer writer = new FileWriter("data.csv");
             CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (WorkerResults results : resultsList) {
                printer.printRecord(
                        results.getTotals().size(),
                        results.getSum(),
                        results.getSpread(),
                        results.getMin(),
                        results.getMax(),
                        results.getMedian(),
                        results.getStdDev());
            }
        }
    }

    private static WorkerResults runForThreadCount(int threadCount,
                                                   Duration duration,
                                                   String phaseMessage) {
        System.out.println(phaseMessage + " w/ duration = " + duration.toString() + " and threads = " + threadCount);
        final List<Worker> workers = IntStream.range(0, threadCount)
                .mapToObj(i -> new Worker())
                .collect(Collectors.toList());

        try {
            // Hold all the locks until all threads are ready
            SEMAPHORE.acquire(MAX_CPU_TARGET);
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            try {
                workers.forEach(executor::submit);
                while (workers.stream().anyMatch(Worker::isStopped)) {
                    Thread.sleep(50);
                }


                // Release all the locks at once to release threads close to simultaneously
                SEMAPHORE.release(MAX_CPU_TARGET);

                Thread.sleep(duration.toMillis());
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Expected pool to shutdown");
                }
                if (!workers.stream().allMatch(Worker::isStopped)) {
                    throw new RuntimeException("Expected all workers to be stopped");
                }
                return new WorkerResults(workers);
            } finally {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static class WorkerResults {
        private final List<Long> totals;
        private final DescriptiveStatistics statistics;

        WorkerResults(Collection<Worker> workers) {
            statistics = new DescriptiveStatistics();
            totals = workers.stream()
                    .map(Worker::getTotal)
                    .peek(statistics::addValue)
                    .sorted()
                    .collect(Collectors.toList());
        }

        public List<Long> getTotals() {
            return totals;
        }

        public StatisticalSummary getStats() {
            return statistics;
        }

        public long getMin() {
            return totals.get(0);
        }

        public long getMedian() {
            return totals.get((totals.size() - 1) / 2);
        }

        public long getMax() {
            return totals.get(totals.size() - 1);
        }

        public long getSpread() {
            return getMax() - getMin();
        }

        public long getSum() {
            return totals.stream().reduce(0L, Long::sum);
        }

        public long getStdDev() {
            return (long) statistics.getStandardDeviation();
        }

        @Override
        public String toString() {
            return "WorkerResults{" +
                    "size=" + totals.size() + "," +
                    "min=" + getMin() + "," +
                    "median=" + getMedian() + "," +
                    "max=" + getMax() + "," +
                    "spread=" + getSpread() + "," +
                    "sum=" + getSum() + "," +
                    "stddev=" + getStdDev() +
                    '}';
        }
    }

    static class Worker implements Runnable {
        private volatile Thread thread = null;
        private long total = 0;

        @Override
        public void run() {
            try {
                thread = Thread.currentThread();
                SEMAPHORE.acquire();
                while (!Thread.interrupted()) {
                    total++;
                }
                thread = null;
            } catch (InterruptedException ex) {
                System.err.println("Thread interrupted unexpectedly! " + Thread.currentThread().getName());
                throw new RuntimeException(ex);
            } finally {
                SEMAPHORE.release();
            }
        }

        public boolean isStopped() {
            return thread == null;
        }

        public long getTotal() {
            return total;
        }
    }
}
