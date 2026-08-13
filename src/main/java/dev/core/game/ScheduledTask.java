package dev.core.game;

public interface ScheduledTask {
    void cancel();

    boolean isCancelled();
}
