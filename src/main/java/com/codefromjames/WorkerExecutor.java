package com.codefromjames;

public interface WorkerExecutor {
    WorkerResult targetResultForThreadCount(long targetCount, int threadCount);
}
