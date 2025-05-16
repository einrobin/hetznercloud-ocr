package com.github.einrobin.clusterexecutor.cloud;

import com.github.einrobin.clusterexecutor.cloud.exception.CloudInstanceCreateException;

public interface CloudClusterProvider {

    void init();

    void shutdown();

    String name();

    CloudClusterInstance createInstance(CloudClusterExecutor executor) throws CloudInstanceCreateException;

    void deleteInstance(CloudClusterInstanceMeta instance);
}
