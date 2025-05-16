package com.github.einrobin.clusterexecutor.cloud;

import com.github.einrobin.clusterexecutor.ClusterInstance;

public interface CloudClusterInstance extends ClusterInstance {

    void setMeta(CloudClusterInstanceMeta meta);
}
