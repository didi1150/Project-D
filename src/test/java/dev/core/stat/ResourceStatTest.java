package dev.core.stat;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import dev.core.MockClock;
import dev.core.game.StopWatch;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.modifier.StatModifier;

public class ResourceStatTest {

    private MockClock scheduler;

    private StopWatch stopWatch;
    private ResourceStat healthStat;
    private CombatStat healthRegenStat;
    private CombatStat healthMaxStat;
    private CombatStat healAndShieldPowerStat;

    @BeforeEach
    void setup() {
        // Start a mock server and plugin
//		MockBukkit.mock();
        scheduler = new MockClock();
        stopWatch = new StopWatch(scheduler);
        healthRegenStat = new CombatStat("HEALTH_REGEN", 5); // 5 hp per 5 seconds
        healthMaxStat = new CombatStat("HEALTH_MAX", 100);
        healAndShieldPowerStat = new CombatStat("HEAL_AND_SHIELD_POWER", 0);

        long now = stopWatch.getTimeMillis();
        healthStat = new ResourceStat("HEALTH", t -> healthMaxStat.getCurrent(t),
                t -> healthRegenStat.getCurrent(t) * (1 + healAndShieldPowerStat.getCurrent(t) / 100), now);
    }

    @AfterEach
    void tearDown() {
        stopWatch.reset();
//		MockBukkit.unmock();
    }

    @Test
    void testSimpleRegen() {
        long startTime = stopWatch.getTimeMillis();
        healthStat.setCurrent(0);
        assertEquals(0, healthStat.getCurrent(startTime), 1e-12);

        for (int i = 1; i <= 25; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 25000;
        assertEquals(25, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testFullRegen() {
        long startTime = stopWatch.getTimeMillis();
        healthStat.setCurrent(0);

        for (int i = 1; i <= 100; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 100000;
        assertEquals(100, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testRegenWithMaxChange() {
        long startTime = stopWatch.getTimeMillis();
        healthStat.setCurrent(0);

        for (int i = 1; i <= 10; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long midTime = startTime + 10000;
        assertEquals(10, healthStat.getCurrent(midTime), 1e-12);

        healthMaxStat.addModifier(new StatModifier(100, StatModifierType.FLAT, StatType.HEALTH_MAX, "test_buff", midTime));

        assertEquals(10, healthStat.getCurrent(midTime), 1e-12);
        assertEquals(200, healthStat.getMax(midTime), 1e-12);

        for (int i = 11; i <= 20; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 20000;
        assertEquals(20, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testHealAndShieldPower() {
        long startTime = stopWatch.getTimeMillis();
        healAndShieldPowerStat.addModifier(
                new StatModifier(50, StatModifierType.FLAT, StatType.HEAL_AND_SHIELD_POWER, "test_heal_power", startTime));

        healthStat.setCurrent(0);

        for (int i = 1; i <= 5; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 5000;
        assertEquals(7.5, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testInstantHeal() {
        long startTime = stopWatch.getTimeMillis();
        healthStat.setCurrent(0);

        healthStat.modify(50);
        assertEquals(50, healthStat.getCurrent(startTime), 1e-12);

        for (int i = 1; i <= 5; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 5000;
        assertEquals(55, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testRegenStopsAtMax() {
        long startTime = stopWatch.getTimeMillis();
        healthStat.setCurrent(95);

        for (int i = 1; i <= 10; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 10000;
        assertEquals(100, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testNegativeRegen() {
        long startTime = stopWatch.getTimeMillis();
        healthRegenStat.setCurrent(-2.0);
        healthStat.setCurrent(50);

        for (int i = 1; i <= 10; i++) {
            long currentTime = startTime + (i * 1000);
            healthStat.tick(currentTime);
        }

        long endTime = startTime + 10000;
        assertEquals(46, healthStat.getCurrent(endTime), 1e-12);
    }

    @Test
    void testZeroTimeDelta() {
        long time = stopWatch.getTimeMillis();
        healthStat.setCurrent(50);

        healthStat.tick(time);

        assertEquals(50, healthStat.getCurrent(time), 1e-12);
    }

}
