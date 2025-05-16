package com.github.einrobin.clusterexecutor.cloud;

import com.github.einrobin.clusterexecutor.ClusterInstance;
import com.github.einrobin.clusterexecutor.ClusterTask;
import com.github.einrobin.clusterexecutor.Tickable;
import com.github.einrobin.clusterexecutor.Timer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CloudClusterInstanceMeta {

    private final List<ClusterTask> tasks = new ArrayList<>();

    private final CloudClusterConfig config;
    private final ClusterInstance instance;

    private CloudClusterInstanceState state = CloudClusterInstanceState.CREATING;

    private final Timer staleTimer = Timer.notStarted();

    public CloudClusterInstanceMeta(CloudClusterConfig config, ClusterInstance instance) {
        this.config = config;
        this.instance = instance;
    }

    public void scheduleTask(ClusterTask task) {
        this.tasks.add(task);
        this.staleTimer.resetFor(this.config.instanceStaleMillis());
    }

    public ClusterInstance instance() {
        return this.instance;
    }

    public CloudClusterInstanceState state() {
        return this.state;
    }

    public void setState(CloudClusterInstanceState state) {
        this.state = state;
    }

    public List<ClusterTask> tasks() {
        return this.tasks;
    }

    public int countScheduledTasks() {
        return this.tasks.size();
    }

    public void tick() {
        if (this.instance instanceof Tickable tickable) {
            tickable.tick();
        }

        if (this.state != CloudClusterInstanceState.RUNNING) {
            return; // nothing to do, we have to wait till the instance is running
        }

        Iterator<ClusterTask> it = this.tasks.iterator();
        while (it.hasNext()) {
            ClusterTask task = it.next();

            switch (task.state()) {
                case QUEUED -> {
                    task.execute(this.instance);
                    this.staleTimer.resetFor(this.config.instanceStaleMillis());
                }
                case RUNNING -> this.staleTimer.resetFor(this.config.instanceStaleMillis());
                case FINISHED -> it.remove();
            }
        }
    }

    public boolean isStale() {
        if (this.state == CloudClusterInstanceState.CREATING || this.state == CloudClusterInstanceState.STARTING) {
            return false;
        }
        return !this.staleTimer.hasStarted() // instance was never used
                || this.staleTimer.hasEnded();
    }

    public boolean isAtLimit() {
        return this.tasks.size() >= this.config.maxSimultaneousTasksPerInstance();
    }

}
