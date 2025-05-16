package com.github.einrobin.clusterexecutor;

public class Timer {

    private long endTimestamp;

    private Timer(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public static Timer until(long endTimestamp) {
        return new Timer(endTimestamp);
    }

    public static Timer notStarted() {
        return until(0L);
    }

    public void reset(long until) {
        this.endTimestamp = until;
    }

    public void resetFor(long millisFromNow) {
        this.reset(System.currentTimeMillis() + millisFromNow);
    }

    public boolean hasStarted() {
        return this.endTimestamp != 0L;
    }

    public boolean hasEnded() {
        return this.endTimestamp <= System.currentTimeMillis();
    }

    public boolean isRunning() {
        return this.hasStarted() && !this.hasEnded();
    }
}
