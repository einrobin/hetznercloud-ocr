package com.github.einrobin.clusterexecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ClusterTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterTask.class);
    private final UUID id = UUID.randomUUID();
    private final long creationTimestamp = System.currentTimeMillis();
    private final String name;

    private long startTimestamp;
    private long finishTimestamp;
    private TaskState state = TaskState.QUEUED;

    private final ClusterTaskProcess process;

    public ClusterTask(ClusterTaskProcess process, String name) {
        this.process = process;
        this.name = name;
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public TaskState state() {
        return this.state;
    }

    public long creationTimestamp() {
        return this.creationTimestamp;
    }

    public long startTimestamp() {
        return this.startTimestamp;
    }

    public long finishTimestamp() {
        return this.finishTimestamp;
    }

    public void setState(TaskState state) {
        this.state = state;

        switch (state) {
            case QUEUED -> this.startTimestamp = this.finishTimestamp = -1L;
            case RUNNING -> this.startTimestamp = System.currentTimeMillis();
            case FINISHED -> this.finishTimestamp = System.currentTimeMillis();
        }
    }

    public String generateTempFile(String name) {
        return "/root/" + this.id + "-" + name;
    }

    public void execute(ClusterInstance instance) {
        this.setState(TaskState.RUNNING);

        this.process.run(this, instance).handle((v, t) -> {
            this.setState(TaskState.FINISHED);

            if (t != null) {
                LOGGER.warn("Task {} has failed to execute", this.name(), t);
            } else {
                LOGGER.info("Task {} has successfully finished execution", this.name());
            }

            return null;
        });
    }
}
