package dev.core;

import dev.core.game.IClock;

public class MockClock implements IClock {

	private long currentTime = 0;

	@Override
	public long getCurrentTime() {
		return currentTime;
	}

	public void tick(double seconds) {
		currentTime += 1000 * seconds;
	}

}
