package com.github.einrobin.clusterexecutor.cloud;

import com.github.einrobin.clusterexecutor.ClusterExecutor;
import com.github.einrobin.clusterexecutor.ClusterTask;
import com.github.einrobin.clusterexecutor.TaskState;
import com.github.einrobin.clusterexecutor.Timer;
import com.github.einrobin.clusterexecutor.cloud.exception.CloudInstanceCreateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class CloudClusterExecutor implements ClusterExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudClusterExecutor.class);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService instanceReadyExecutor = Executors.newCachedThreadPool();
    private final CloudClusterProvider provider;
    private final CloudClusterConfig config;

    private final Queue<ClusterTask> waitingTasks = new ConcurrentLinkedQueue<>();
    private final List<CloudClusterInstanceMeta> instances = new ArrayList<>();

    private final Timer provisioningPaused = Timer.notStarted();

    private boolean acceptTasks = false;

    public CloudClusterExecutor(CloudClusterProvider provider, CloudClusterConfig config) {
        this.provider = provider;
        this.config = config;

        this.scheduler.scheduleWithFixedDelay(() -> {
            try {
                this.tick();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
    }

    public ExecutorService instanceReadyExecutor() {
        return this.instanceReadyExecutor;
    }

    private void tick() {
        if (!this.acceptTasks) {
            for (CloudClusterInstanceMeta instance : this.instances) {
                if (!instance.state().isAfterRunning()) {
                    this.deleteInstance(instance);
                }
            }

            return;
        }

        Iterator<CloudClusterInstanceMeta> it = this.instances.iterator();
        while (it.hasNext()) {
            CloudClusterInstanceMeta instance = it.next();
            if (instance.state() == CloudClusterInstanceState.DELETED) {
                it.remove();
                continue;
            }

            if (instance.state() == CloudClusterInstanceState.FAILED) {
                instance.tasks().removeIf(task -> {
                    if (task.state() != TaskState.FINISHED) {
                        task.setState(TaskState.QUEUED);
                        this.scheduleTask(task);
                        return true;
                    }
                    return false;
                });

                this.deleteInstance(instance);
                continue;
            }

            instance.tick();
        }

        while (!this.waitingTasks.isEmpty()) {
            CloudClusterInstanceMeta instance = this.getBestInstance();

            if (instance == null) {
                break; // no instances available at the moment
            }

            ClusterTask task = this.waitingTasks.poll();

            LOGGER.info("Delegating task {} to instance {} at {}", task, instance.instance().name(), this.provider.name());
            instance.scheduleTask(task);
        }

        for (CloudClusterInstanceMeta instance : this.instances) {
            if (!instance.state().isAfterRunning() && instance.isStale()) {
                this.deleteInstance(instance);
            }
        }
    }

    private void deleteInstance(CloudClusterInstanceMeta instance) {
        LOGGER.info("Requesting instance {} to be deleted at {}", instance.instance().name(), this.provider.name());
        instance.setState(CloudClusterInstanceState.DELETE_REQUESTED);

        this.provider.deleteInstance(instance);
    }

    private CloudClusterInstanceMeta getBestInstance() {
        int minTasks = Integer.MAX_VALUE;
        CloudClusterInstanceMeta minInstance = null;

        // first see if there is an instance that is already running that we can use
        for (CloudClusterInstanceMeta instance : this.instances) {
            if (instance.state() == CloudClusterInstanceState.RUNNING
                    && !instance.isAtLimit()
                    && instance.countScheduledTasks() < minTasks) {
                minTasks = instance.countScheduledTasks();
                minInstance = instance;
            }
        }
        if (minInstance != null) {
            return minInstance;
        }

        // second see if there is an instance that is still starting that we can use once it is started
        for (CloudClusterInstanceMeta instance : this.instances) {
            if (!instance.state().isAfterRunning()
                    && !instance.isAtLimit()
                    && instance.countScheduledTasks() < minTasks) {
                minTasks = instance.countScheduledTasks();
                minInstance = instance;
            }
        }

        if (minInstance != null) {
            return minInstance;
        }

        if (this.instances.size() >= this.config.maxSimultaneousInstances()
                || this.provisioningPaused.isRunning()) {
            return null; // limit reached
        }

        // and lastly request a new instance
        LOGGER.info("Requesting a new instance from {}", this.provider.name());
        CloudClusterInstance instance = null;
        try {
            instance = this.provider.createInstance(this);
        } catch (CloudInstanceCreateException e) {
            this.provisioningPaused.resetFor(TimeUnit.SECONDS.toMillis(30));
            LOGGER.error("There was an error provisioning a new instance from {}", this.provider.name(), e);
        }

        if (instance == null) {
            return null;
        }

        CloudClusterInstanceMeta meta = new CloudClusterInstanceMeta(this.config, instance);
        instance.setMeta(meta);

        this.instances.add(meta);

        return meta;
    }

    @Override
    public void init() {
        this.provider.init();
        this.acceptTasks = true;
    }

    @Override
    public void shutdown() {
        this.acceptTasks = false;

        this.provider.shutdown();
        this.scheduler.shutdown();
    }

    @Override
    public void scheduleTask(ClusterTask task) {
        if (!this.acceptTasks) {
            throw new IllegalStateException("This executor is currently not accepting any tasks");
        }

        this.waitingTasks.offer(task);
    }
}
