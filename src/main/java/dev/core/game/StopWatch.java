package dev.core.game;

public class StopWatch {

	private long timeInTicks;
	private long lastUpdate;
	private long speed; // speed * 1000 multiplier

	private boolean paused;
	private boolean shutdown;

	private IClock clock;

	public StopWatch(IClock clock) {
		this.clock = clock;
		this.speed = 1000; // normal speed
		this.timeInTicks = 0;
		this.lastUpdate = clock.getCurrentTime();

		this.paused = false;
		this.shutdown = false;
	}

	private void tick() {
		if (!shutdown && !paused) {
			long increment = ((clock.getCurrentTime() - lastUpdate) * speed) / 1000;
			timeInTicks += increment;
		}
		lastUpdate = clock.getCurrentTime();
	}

	public synchronized void pause() {
		tick();
		paused = true;
	}

	public synchronized void resume() {
		if (!paused || shutdown) {
			return;
		}
		tick();
		paused = false;
	}

	public void reset(long timeInTicks) {
		this.timeInTicks = timeInTicks;
		lastUpdate = clock.getCurrentTime();
	}

	public void reset() {
		this.reset(0);
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

	public long getTimeMillis() {
		tick();
		return timeInTicks;
	}

	public double getTimeSeconds() {
		tick();
		return timeInTicks / 1000.0;
	}

	public boolean isPaused() {
		return paused;
	}

	public boolean isShutdown() {
		return shutdown;
	}
}
