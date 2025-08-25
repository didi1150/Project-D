package dev.core.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import dev.bukkit.game.GameClock;

class GameClockTest {

	private static ServerMock server;
	private static Plugin plugin;
	private GameClock clock;

	@BeforeAll
	static void setupMock() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();

	}

	@BeforeEach
	void setUpClock() {
		clock = new GameClock(plugin); // 1 tick = 50ms
	}

	@AfterEach
	void tearDown() {
		clock.stop();
		clock.reset();
	}

	@AfterAll
	static void tearDownMock() {
		MockBukkit.unmock();
	}

	@Test
	void testRunFiveSeconds() {
		assertNotNull(clock);
		assertFalse(clock.isRunning());

		clock.start();
		server.getScheduler().performTicks(100); // 5 seconds / 50ms = 100 ticks

		assertEquals(100, clock.getTimeTicks());
		assertEquals(5.0, clock.getTimeSeconds(), 1e-12);
	}

	@Test
	void testRunTwoAndHalfSeconds() {
		clock.start();
		server.getScheduler().performTicks(50); // 2.5 seconds = 50 ticks

		assertEquals(50, clock.getTimeTicks());
		assertEquals(2.5, clock.getTimeSeconds(), 1e-12);
	}

	@Test
	void testRunPauseRun() {
		clock.start();
		server.getScheduler().performTicks(50); // 2.5s
		clock.pause();

		assertEquals(50, clock.getTimeTicks());
		assertTrue(clock.isPaused());

		// Clock should not advance while paused
		server.getScheduler().performTicks(50);
		assertEquals(50, clock.getTimeTicks());

		// Resume
		clock.resume();
		server.getScheduler().performTicks(50); // another 2.5s

		assertEquals(100, clock.getTimeTicks());
		assertFalse(clock.isPaused());
	}

	@Test
	void testDoubleSpeed() {
		clock.setSpeed(2.0); // 2x faster
		clock.start();

		server.getScheduler().performTicks(50); // 2.5s wall time
		clock.stop();

		// Should be 5s game time because of double speed
		assertEquals(100, clock.getTimeTicks());
		assertEquals(5.0, clock.getTimeSeconds(), 1e-12);
	}
}
