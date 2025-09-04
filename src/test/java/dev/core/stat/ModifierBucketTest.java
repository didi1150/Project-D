package dev.core.stat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.core.MockClock;
import dev.core.game.StopWatch;

import static org.junit.jupiter.api.Assertions.*;

public class ModifierBucketTest {

	private static ModifierBucket modifierBucket;
	private static StopWatch clock;
	private static MockClock scheduler;

	@BeforeAll
	static void setup() {
		modifierBucket = new ModifierBucket();
		scheduler = new MockClock();
		clock = new StopWatch(scheduler);
	}

	@AfterEach
	void reset() {
		clock.reset();
		modifierBucket.clear();
	}

	@Test
	void testSimpleAdd() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);
		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(1, modifierBucket.active(clock.getTimeMillis()).size());
		assertTrue(modifierBucket.active(clock.getTimeMillis()).contains(increaseMaxHP));
		assertEquals(20, modifierBucket.getFinalValue(10), 1e-12); // 10 base + 10 flat
	}

	@Test
	void testSimpleRemoveByExpiry() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", 2.5, clock.getTimeMillis(), StatTarget.BOTH);
		modifierBucket.addModifier(increaseMaxHP);

		assertEquals(20, modifierBucket.getFinalValue(10), 1e-12);

		// advance time to expire modifier
		scheduler.tick(125); // 2.5s = 2500ms, tick is 20ms => 125 ticks
		modifierBucket.removeExpired(clock.getTimeMillis());

		assertEquals(0, modifierBucket.active(clock.getTimeMillis()).size());
		assertEquals(10, modifierBucket.getFinalValue(10), 1e-12); // only base value remains
	}

	@Test
	void testDuplicateUniqueAdd() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);
		assertEquals(20, modifierBucket.getFinalValue(10), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);
		assertEquals(30, modifierBucket.getFinalValue(10), 1e-12); // replaced with second modifier
	}

	@Test
	void testReplacePolicy() {
		StatModifier increaseMaxHP = new StatModifier(10, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(20, ModifierStackPolicy.REPLACE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeMillis(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);
		assertEquals(20, modifierBucket.getFinalValue(10), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);
		assertEquals(30, modifierBucket.getFinalValue(10), 1e-12);
	}

	@Test
	void testMaxPolicy() {
		StatModifier increaseMaxHP = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(10, ModifierStackPolicy.MAX_ONLY, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeMillis(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);
		assertEquals(30, modifierBucket.getFinalValue(10), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);
		assertEquals(30, modifierBucket.getFinalValue(10), 1e-12); // smaller one ignored
	}

	@Test
	void testMinPolicy() {
		StatModifier increaseMaxHP = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(10, ModifierStackPolicy.MIN_ONLY, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeMillis(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);
		assertEquals(30, modifierBucket.getFinalValue(10), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);
		assertEquals(20, modifierBucket.getFinalValue(10), 1e-12); // bigger one ignored
	}

	@Test
	void testStackPolicy() {
		StatModifier increaseMaxHP = new StatModifier(20, ModifierStackPolicy.UNIQUE_BY_SOURCE, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test", -1, clock.getTimeMillis(), StatTarget.BOTH);
		StatModifier increaseMaxHP2 = new StatModifier(10, ModifierStackPolicy.STACK, ModifierType.FLAT,
				StatType.HEALTH_MAX, "Test2", -1, clock.getTimeMillis(), StatTarget.BOTH);

		modifierBucket.addModifier(increaseMaxHP);
		assertEquals(30, modifierBucket.getFinalValue(10), 1e-12);

		modifierBucket.addModifier(increaseMaxHP2);
		assertEquals(40, modifierBucket.getFinalValue(10), 1e-12); // both stack
	}
}
