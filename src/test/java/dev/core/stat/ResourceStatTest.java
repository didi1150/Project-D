package dev.core.stat;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.core.game.GameClock;

public class ResourceStatTest {

	private static GameClock clock;
	private static ResourceStat healthStat;
	private static CombatStat healthRegenStat;
	private static CombatStat healthMaxStat;
	private static CombatStat healAndShieldPowerStat;

	@BeforeAll
	static void setup() {
		clock = new GameClock(1000);
	}

	@BeforeEach
	void start() {
		clock.start();
		healthRegenStat = new CombatStat("HEALTH_REGEN", 5); // 5 hp per 5 seconds
		healthMaxStat = new CombatStat("HEALTH_MAX", 100);
		healAndShieldPowerStat = new CombatStat("HEAL_AND_SHIELD_POWER", 0);
		long now = clock.getTimeMillis();
		healthStat = new ResourceStat("HEALTH", t -> healthMaxStat.getCurrent(t),
				t -> healthRegenStat.getCurrent(t) * (1 + healAndShieldPowerStat.getCurrent(t) / 100), now);
	}

	@AfterEach
	void tearDown() {
		clock.stop();
		clock.reset();
	}

	@Test
	void testSimpleRegen() {
		// Set initial health to 0
		long startTime = clock.getTimeMillis();
		healthStat.setCurrent(0);
		assertEquals(0, healthStat.getCurrent(startTime), 1e-12);

		// Simulate 25 seconds of regeneration (5 HP per 5 seconds = 1 HP per second)
		for (int i = 1; i <= 25; i++) {
			long currentTime = startTime + (i * 1000); // Advance 1 second per iteration
			healthStat.tick(currentTime);

			System.out.printf("Time: %ds, Health: %.2f/%.2f%n", i, healthStat.getCurrent(currentTime),
					healthStat.getMax(currentTime));
		}

		long endTime = startTime + 25000; // 25 seconds later
		assertEquals(25, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testFullRegen() {
		long startTime = clock.getTimeMillis();
		healthStat.setCurrent(0);

		// Simulate 100 seconds (should fully regenerate to 100 HP)
		for (int i = 1; i <= 100; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long endTime = startTime + 100000; // 100 seconds later
		assertEquals(100, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testRegenWithMaxChange() {
		long startTime = clock.getTimeMillis();
		healthStat.setCurrent(0);

		// Regenerate for 10 seconds (should get 10 HP)
		for (int i = 1; i <= 10; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long midTime = startTime + 10000;
		assertEquals(10, healthStat.getCurrent(midTime), 1e-12);

		// Increase max health to 200
		healthMaxStat.addModifier(new StatModifier(100, ModifierType.FLAT, StatType.HEALTH_MAX, "test_buff", midTime));

		// Health should maintain same absolute value (10 HP)
		assertEquals(10, healthStat.getCurrent(midTime), 1e-12);
		assertEquals(200, healthStat.getMax(midTime), 1e-12);

		// Continue regenerating for another 10 seconds
		for (int i = 11; i <= 20; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long endTime = startTime + 20000;
		// Should have 10 (first 10s) + 10 (second 10s) = 20 HP
		assertEquals(20, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testHealAndShieldPower() {
		long startTime = clock.getTimeMillis();
		// Increase heal power by 50%
		healAndShieldPowerStat.addModifier(
				new StatModifier(50, ModifierType.FLAT, StatType.HEAL_AND_SHIELD_POWER, "test_heal_power", startTime));

		healthStat.setCurrent(0);

		// Regenerate for 5 seconds
		for (int i = 1; i <= 5; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long endTime = startTime + 5000;
		// With 50% heal power, 5 HP/5sec becomes 7.5 HP/5sec = 1.5 HP/sec
		// Over 5 seconds: 7.5 HP
		assertEquals(7.5, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testInstantHeal() {
		long startTime = clock.getTimeMillis();
		healthStat.setCurrent(0);

		// Apply instant heal of 50 HP
		healthStat.modify(50);
		assertEquals(50, healthStat.getCurrent(startTime), 1e-12);

		// Regenerate for 5 seconds
		for (int i = 1; i <= 5; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long endTime = startTime + 5000;
		// Should have 50 + 5 = 55 HP
		assertEquals(55, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testRegenStopsAtMax() {
		long startTime = clock.getTimeMillis();
		healthStat.setCurrent(95); // Start near max

		// Regenerate for 10 seconds (would normally give 10 HP)
		for (int i = 1; i <= 10; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long endTime = startTime + 10000;
		// Should be capped at max health (100)
		assertEquals(100, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testNegativeRegen() {
		long startTime = clock.getTimeMillis();

		// Set negative regeneration (poison)
		healthRegenStat.setCurrent(-2.0); // -2 HP per 5 seconds
		healthStat.setCurrent(50);

		// Simulate 10 seconds of negative regen
		for (int i = 1; i <= 10; i++) {
			long currentTime = startTime + (i * 1000);
			healthStat.tick(currentTime);
		}

		long endTime = startTime + 10000;
		// Should lose 4 HP over 10 seconds (50 - 4 = 46)
		assertEquals(46, healthStat.getCurrent(endTime), 1e-12);
	}

	@Test
	void testZeroTimeDelta() {
		long time = clock.getTimeMillis();
		healthStat.setCurrent(50);

		// Tick with no time advancement
		healthStat.tick(time);

		// Health should remain unchanged
		assertEquals(50, healthStat.getCurrent(time), 1e-12);
	}

}
