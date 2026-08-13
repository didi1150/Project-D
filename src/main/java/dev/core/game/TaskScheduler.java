package dev.core.game;

public interface TaskScheduler {
    ScheduledTask runTaskLater(Runnable task, long delayTicks);
    ScheduledTask runTaskLaterAsync(Runnable task, long delayTicks);
    ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks);
    
    void cancelAllTasks();
}
