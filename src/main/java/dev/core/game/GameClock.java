package dev.core.game;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class GameClock {

	private final ScheduledExecutorService executor;

	private final AtomicLong timeMillis;
	private final AtomicLong speed;
	private final long period; // Default: 50ms = 1 Tick in Minecraft

	private final AtomicBoolean running;
	private final AtomicBoolean paused;
	private final AtomicBoolean shutdown;

	private volatile Future<?> serviceFuture;

	public GameClock(long period) {
		this.executor = Executors.newSingleThreadScheduledExecutor();
		this.timeMillis = new AtomicLong(0);
		this.speed = new AtomicLong(1000);
		this.period = period;

		// Default = False
		this.running = new AtomicBoolean();
		this.paused = new AtomicBoolean();
		this.shutdown = new AtomicBoolean();
	}

	public GameClock() {
		this(50);
	}

	private void tick() {
		if (!shutdown.get() && running.get() && !paused.get()) {
			long increment = (period * speed.get()) / 1000;
			timeMillis.addAndGet(increment);
		}
	}

	public synchronized void start() {
		if (shutdown.get()) {
			throw new IllegalStateException("GameClock has been shutdown and cannot be restarted");
		}

		if (running.get() && !paused.get()) {
			return;
		}

		cancelCurrentTask();

		serviceFuture = executor.scheduleAtFixedRate(this::tick, 0, period, TimeUnit.MILLISECONDS);
	}

	public synchronized void stop() {
		this.running.set(false);
		this.paused.set(false);
		cancelCurrentTask();
	}

	public synchronized void pause() {
		if (!running.get()) {
			return; // Can't pause if not running
		}

		paused.set(true);
		cancelCurrentTask();
	}

	public synchronized void resume() {
		if (!paused.get() || shutdown.get()) {
			return; // Not paused or already shutdown
		}

		paused.set(false);

		// Restart the scheduled task
		serviceFuture = executor.scheduleAtFixedRate(this::tick, 0, period, TimeUnit.MILLISECONDS);
	}

	public synchronized void kill() {
		shutdown.set(true);
		running.set(false);
		paused.set(false);
		cancelCurrentTask();

		executor.shutdown();
		try {
			if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	public void reset() {
		timeMillis.set(0);
	}

	public void setSpeed(double multiplier) {
		if (multiplier < 0) {
			throw new IllegalArgumentException("Speed multiplier cannot be negative");
		}
		speed.set((long) (multiplier * 1000));
	}

	public double getSpeed() {
		return (double) speed.get() / 1000;
	}

	public long getTimeMillis() {
		return timeMillis.get();
	}

	public double getTimeSeconds() {
		return timeMillis.get() / 1000.0;
	}

	public boolean isRunning() {
		return running.get();
	}

	public boolean isPaused() {
		return paused.get();
	}

	public boolean isShutdown() {
		return shutdown.get();
	}

	private synchronized void cancelCurrentTask() {
		Future<?> current = serviceFuture;
		if (current != null && !current.isDone()) {
			current.cancel(false); // Don't interrupt running task
		}
	}
}
