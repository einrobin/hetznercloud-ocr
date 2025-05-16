package com.github.einrobin.clusterexecutor.cloud;

public enum CloudClusterInstanceState {

    CREATING,
    STARTING,
    RUNNING,
    DELETE_REQUESTED,
    FAILED,
    STOPPING,
    DELETING,
    DELETED;

    public boolean isAfterRunning() {
        return this == DELETE_REQUESTED || this == STOPPING || this == FAILED || this == DELETING || this == DELETED;
    }
}
