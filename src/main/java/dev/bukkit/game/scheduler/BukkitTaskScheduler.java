package dev.bukkit.game.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.core.game.ScheduledTask;
import dev.core.game.TaskScheduler;

public class BukkitTaskScheduler implements TaskScheduler {

    private final Plugin plugin;

    public BukkitTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ScheduledTask runTaskLater(Runnable task, long delayTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        return new BukkitScheduledTask(bukkitTask);
    }

    @Override
    public ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return new BukkitScheduledTask(bukkitTask);
    }

    @Override
    public ScheduledTask runTaskLaterAsync(Runnable task, long delayTicks) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        return new BukkitScheduledTask(bukkitTask);
    }

    private static class BukkitScheduledTask implements ScheduledTask {
        private final BukkitTask bukkitTask;

        public BukkitScheduledTask(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
        }

        @Override
        public void cancel() {
            bukkitTask.cancel();
        }

        @Override
        public boolean isCancelled() {
            return bukkitTask.isCancelled();
        }
    }

}
