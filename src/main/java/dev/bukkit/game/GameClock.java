package dev.bukkit.game;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class GameClock {

	private final long period; // Default: 50ms = 1 tick in Minecraft
	private long timeInTicks;
	private long speed; // speed * 1000 multiplier

	private boolean running;
	private boolean paused;
	private boolean shutdown;

	private BukkitTask task;

	private final Plugin plugin;

	public GameClock(Plugin plugin, long period) {
		this.plugin = plugin;
		this.period = period;
		this.speed = 1000; // normal speed
		this.timeInTicks = 0;

		this.running = false;
		this.paused = false;
		this.shutdown = false;
	}

	public GameClock(Plugin plugin) {
		this(plugin, 1); // default 1 tick = 50ms
	}

	private void tick() {
		if (!shutdown && running && !paused) {
			long increment = (period * speed) / 1000;
			timeInTicks += increment;
		}
	}

	public synchronized void start() {
		if (shutdown) {
			throw new IllegalStateException("GameClock has been shutdown and cannot be restarted");
		}
		if (running && !paused)
			return;

		cancelCurrentTask();

		running = true;
		paused = false;

		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, period);
	}

	public synchronized void stop() {
		running = false;
		paused = false;
		cancelCurrentTask();
	}

	public synchronized void pause() {
		if (!running)
			return;
		paused = true;
	}

	public synchronized void resume() {
		if (!paused || shutdown)
			return;
		paused = false;
	}

	public synchronized void kill() {
		shutdown = true;
		running = false;
		paused = false;
		cancelCurrentTask();
	}

	public void reset() {
		timeInTicks = 0;
	}

	public void reset(long time) {
		timeInTicks = time;
	}

	public void setSpeed(double multiplier) {
		if (multiplier < 0) {
			throw new IllegalArgumentException("Speed multiplier cannot be negative");
		}
		this.speed = (long) (multiplier * 1000);
	}

	public double getSpeed() {
		return (double) speed / 1000;
	}

	public long getTimeTicks() {
		return timeInTicks;
	}

	public double getTimeSeconds() {
		return timeInTicks / 20.0;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean isPaused() {
		return paused;
	}

	public boolean isShutdown() {
		return shutdown;
	}

	private void cancelCurrentTask() {
		if (task != null && !task.isCancelled()) {
			task.cancel();
		}
		task = null;
	}
}
