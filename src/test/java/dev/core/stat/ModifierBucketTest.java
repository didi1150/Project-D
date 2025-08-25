package dev.core.stat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.bukkit.game.GameClock;

public class ModifierBucketTest {

	private static ModifierBucket modifierBucket;
	private static GameClock clock;

	@BeforeAll
	static void setup() {
		modifierBucket = new ModifierBucket();
		clock = new GameClock(null, 1);
	}

	@AfterEach
	void reset() {
		clock.stop();
		clock.reset();
		modifierBucket.clear();
	}

	@BeforeEach
	void startEach() {
		clock.start();
	}

	@Test
	void testSimpleAdd() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);
		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(20, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);
	}

	@Test
	void testSimpleRemove() {

		clock.start();
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", 2.5, clock.getTimeTicks(), StatTarget.BOTH);
		Thread testThread = new Thread(() -> {

			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			modifierBucket.removeExpired(clock.getTimeTicks());

			assertEquals(0, modifierBucket.active(clock.getTimeTicks()).size());
			assertFalse(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
			assertEquals(10, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

		});
		testThread.start();

		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(20, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

	}

	@Test
	void testDuplicateUniqueAdd() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(20, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP2));
		assertFalse(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(30, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

	}

	@Test
	void testReplacePolicy() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(20, ModifierStackPolicy.REPLACE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeTicks(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(20, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP2));
		assertFalse(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(30, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);
	}

	@Test
	void testMaxPolicy() {
		StatModifier increaseMaxHP = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(10, ModifierStackPolicy.MAX_ONLY, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeTicks(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(30, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertFalse(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP2));
		assertEquals(30, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);
	}

	@Test
	void testMinPolicy() {
		StatModifier increaseMaxHP = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(10, ModifierStackPolicy.MIN_ONLY, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeTicks(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(30, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP2));
		assertFalse(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(20, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);
	}

	@Test
	void testStackPolicy() {
		StatModifier increaseMaxHP = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeTicks(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(10, ModifierStackPolicy.STACK, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeTicks(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(30, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);

		assertEquals(2, modifierBucket.active(clock.getTimeTicks()).size());
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP2));
		assertTrue(modifierBucket.active(clock.getTimeTicks()).contains(increaseMaxHP));
		assertEquals(40, modifierBucket.getFinalValue(10, clock.getTimeTicks()), 1e-12);
	}

}
