package com.github.einrobin.clusterexecutor;

import java.util.concurrent.CompletableFuture;

public interface ClusterTaskProcess {

    CompletableFuture<Void> run(ClusterTask task, ClusterInstance server);
}
