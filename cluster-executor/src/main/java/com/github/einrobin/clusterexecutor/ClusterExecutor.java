package com.github.einrobin.clusterexecutor;

public interface ClusterExecutor {

    void init();

    void shutdown();

    void scheduleTask(ClusterTask task);
}
