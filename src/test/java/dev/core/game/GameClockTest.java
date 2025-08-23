package dev.core.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GameClockTest {

	private static GameClock clock;

	@BeforeAll
	static void setup() {
		clock = new GameClock(1);
	}

	@AfterEach
	void tearDown() {
		clock.stop();
		clock.reset();
	}

	@Test
	void testRunFiveSeconds() {
		assertTrue(clock != null);
		assertFalse(clock.isRunning());
		Thread testThread = new Thread(() -> {
			clock.start();
			try {
				Thread.sleep(Duration.ofSeconds(5));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			assertEquals(5000, clock.getTimeMillis());
		});
		testThread.start();
	}

	@Test
	void testRunTwoAndHalfSeconds() {
		assertTrue(clock != null);
		assertFalse(clock.isRunning());
		Thread testThread = new Thread(() -> {
			clock.start();
			try {
				Thread.sleep(Duration.ofMillis(2500));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			assertEquals(2500, clock.getTimeMillis());
			assertEquals(2.5, clock.getTimeSeconds(), 1e-12);
		});
		testThread.start();
	}

	@Test
	void testRunPauseRun() {
		assertTrue(clock != null);
		assertFalse(clock.isRunning());
		Thread testThread = new Thread(() -> {
			clock.start();
			try {
				Thread.sleep(Duration.ofMillis(2500));

				clock.pause();

				assertEquals(2500, clock.getTimeMillis());
				assertEquals(2.5, clock.getTimeSeconds(), 1e-12);
				assertTrue(clock.isPaused());
				assertFalse(clock.isRunning());

				clock.start();

				Thread.sleep(Duration.ofMillis(2500));

				assertEquals(5000, clock.getTimeMillis());
				assertEquals(5.0, clock.getTimeSeconds(), 1e-12);
				assertFalse(clock.isPaused());
				assertFalse(clock.isRunning());

			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		});
		testThread.start();
	}

	@Test
	void testDoubleSpeed() {
		assertTrue(clock != null);
		assertFalse(clock.isRunning());
		Thread testThread = new Thread(() -> {
			clock.setSpeed(2);
			clock.start();
			try {
				Thread.sleep(Duration.ofMillis(2500));

				clock.stop();

				assertEquals(5000, clock.getTimeMillis());
				assertEquals(5.0, clock.getTimeSeconds(), 1e-12);
				assertTrue(clock.isPaused());
				assertFalse(clock.isRunning());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		});
		testThread.start();
	}
}
