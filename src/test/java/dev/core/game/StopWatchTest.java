package dev.core.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.MockClock;

class StopWatchTest {

	private MockClock mockClock;
	private StopWatch stopWatch;

	@BeforeEach
	void setUpClock() {
		mockClock = new MockClock();
		stopWatch = new StopWatch(mockClock); // 1 tick = 50ms
	}

	@AfterEach
	void tearDown() {
		stopWatch.reset();
	}


	@Test
	void testRunFiveSeconds() {
		assertNotNull(stopWatch);

		mockClock.tick(5); // 10 seconds = 100 millis

		assertEquals(5000, stopWatch.getTimeMillis());
		assertEquals(5.0, stopWatch.getTimeSeconds(), 1e-12);
	}

	@Test
	void testRunTwoAndHalfSeconds() {
		mockClock.tick(2.5); // 2.5 seconds = 2500 ticks

		assertEquals(2500, stopWatch.getTimeMillis());
		assertEquals(2.5, stopWatch.getTimeSeconds(), 1e-12);
	}

	@Test
	void testRunPauseRun() {
		mockClock.tick(2.5);// 2.5s
		stopWatch.pause();

		assertEquals(2500, stopWatch.getTimeMillis());
		assertTrue(stopWatch.isPaused());

		// Clock should not advance while paused
		mockClock.tick(2.5);
		assertEquals(2500, stopWatch.getTimeMillis());

		// Resume
		stopWatch.resume();
		mockClock.tick(2.5);// another 2.5s

		assertEquals(5000, stopWatch.getTimeMillis());
		assertFalse(stopWatch.isPaused());
	}

	@Test
	void testDoubleSpeed() {
		stopWatch.setSpeed(2.0); // 2x faster

		mockClock.tick(2.5);// 2.5s wall time

		// Should be 5s game time because of double speed
		assertEquals(5000, stopWatch.getTimeMillis());
		assertEquals(5.0, stopWatch.getTimeSeconds(), 1e-12);
	}
}
