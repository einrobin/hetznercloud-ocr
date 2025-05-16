package com.github.einrobin.clusterexecutor.cloud;

public record CloudClusterConfig(int maxSimultaneousInstances, int maxSimultaneousTasksPerInstance, long instanceStaleMillis) {
}
