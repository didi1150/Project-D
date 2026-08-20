package dev.bukkit.summon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;

class SummonStatsTest {

    @Test
    void capacityStartsAtTwoAndGrowsWithLevel() {
        assertEquals(2, SummonStats.capacityForLevel(1));
        assertEquals(2, SummonStats.capacityForLevel(3));
        assertEquals(3, SummonStats.capacityForLevel(4));
        assertEquals(3, SummonStats.capacityForLevel(5));
        assertEquals(5, SummonStats.capacityForLevel(10));
        assertEquals(5, SummonStats.capacityForLevel(15));
        assertEquals(5, SummonStats.capacityForLevel(99)); // capped
    }

    @Test
    void levelZeroCountsAsLevelOne() {
        assertEquals(1, SummonStats.effectiveLevel(0));
        assertEquals(1, SummonStats.effectiveLevel(-3));
        assertEquals(7, SummonStats.effectiveLevel(7));
        assertTrue(SummonStats.canCapture(0, SpawnTier.BASIC));
    }

    @Test
    void tierGatesCaptureBySupportLevel() {
        assertTrue(SummonStats.canCapture(1, SpawnTier.BASIC));
        assertFalse(SummonStats.canCapture(4, SpawnTier.ADVANCED));
        assertTrue(SummonStats.canCapture(5, SpawnTier.ADVANCED));
        assertFalse(SummonStats.canCapture(9, SpawnTier.ELITE));
        assertTrue(SummonStats.canCapture(10, SpawnTier.ELITE));
    }

    @Test
    void lowestTierPrefersTheMostPermissiveSpawnTier() {
        assertEquals(SpawnTier.BASIC, SummonStats.lowestTier(Set.of()));
        assertEquals(SpawnTier.BASIC, SummonStats.lowestTier(null));
        assertEquals(SpawnTier.BASIC, SummonStats.lowestTier(Set.of(SpawnTier.BASIC, SpawnTier.ELITE)));
        assertEquals(SpawnTier.ADVANCED, SummonStats.lowestTier(Set.of(SpawnTier.ADVANCED, SpawnTier.ELITE)));
        assertEquals(SpawnTier.ELITE, SummonStats.lowestTier(Set.of(SpawnTier.ELITE)));
    }

    @Test
    void tierMultiplierFollowsTierOrder() {
        assertEquals(1, SummonStats.tierMultiplier(SpawnTier.BASIC));
        assertEquals(2, SummonStats.tierMultiplier(SpawnTier.ADVANCED));
        assertEquals(3, SummonStats.tierMultiplier(SpawnTier.ELITE));
        assertEquals(1, SummonStats.tierMultiplier(null));
    }

    @Test
    void statsScaleWithTierAndSupportLevel() {
        StatManager basic = SummonStats.buildStats(SpawnTier.BASIC, 1);
        StatManager elite = SummonStats.buildStats(SpawnTier.ELITE, 10);

        long now = System.currentTimeMillis();
        assertTrue(basic.getCurrentValue(StatType.HEALTH_MAX, now) < elite.getCurrentValue(StatType.HEALTH_MAX, now));
        assertTrue(basic.getCurrentValue(StatType.ATTACK_DAMAGE, now) < elite.getCurrentValue(StatType.ATTACK_DAMAGE, now));

        // The summon spawns at full health: the synthesized health resource is
        // aligned with the scaled max.
        double basicMax = basic.getCurrentValue(StatType.HEALTH_MAX, now);
        assertEquals(basicMax, basic.getCurrentValue(StatType.HEALTH_RESOURCE, now), 0.001);
    }
}