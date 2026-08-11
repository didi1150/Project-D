package dev.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.progression.PlayerProgression;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.loader.StatLoader;

/**
 * Verifies the class/stat decoupling: the NONE stat block is the universal base
 * stat set every entity falls back to, and playable classes layer additive
 * bonuses on top of it instead of replacing it.
 */
class StatDefaultsTest {

    @BeforeAll
    static void setup() {
        Map<StatType, Stat> none = new EnumMap<>(StatType.class);
        none.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100));
        none.put(StatType.ARMOR, new CombatStat("ARMOR", 10));
        none.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 5));

        Map<StatType, Stat> tank = new EnumMap<>(StatType.class);
        tank.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 200));
        tank.put(StatType.ARMOR, new CombatStat("ARMOR", 60));

        DefaultStats.loadAll(Map.of(RPGClassType.NONE, none, RPGClassType.TANK, tank));
    }

    @Test
    void defaultStatsAreNeverNull() {
        // The original bug: NONE was never loaded, so getStatsByClass returned null.
        assertNotNull(DefaultStats.getDefaultStats());
        assertNotNull(DefaultStats.getStatsByClass(RPGClassType.NONE));
        // Unknown class -> empty map, never null.
        assertEquals(0, DefaultStats.getStatsByClass(RPGClassType.ARCHER).size());
    }

    @Test
    void defaultStatsIncludeResources() {
        Map<StatType, Stat> defaults = DefaultStats.getDefaultStats();
        assertNotNull(defaults.get(StatType.HEALTH_RESOURCE));
        assertNotNull(defaults.get(StatType.MANA_RESOURCE));
        assertEquals(100, defaults.get(StatType.HEALTH_MAX).getCurrent(0));
    }

    @Test
    void statManagerBuildsFromNoneDefaultsWithoutNpe() {
        StatManager manager = new StatManager(DefaultStats.getDefaultStats());
        assertEquals(100, manager.getCurrentValue(StatType.HEALTH_MAX, 0));
        assertEquals(100, manager.getCurrentValue(StatType.HEALTH_RESOURCE, 0));
    }

    @Test
    void mergeAddsBonusOnTopOfBase() {
        Map<StatType, Stat> merged = StatLoader.mergeStats(DefaultStats.getDefaultStats(),
                DefaultStats.getStatsByClass(RPGClassType.TANK));
        assertEquals(300, merged.get(StatType.HEALTH_MAX).getCurrent(0)); // 100 base + 200 bonus
        assertEquals(70, merged.get(StatType.ARMOR).getCurrent(0)); // 10 base + 60 bonus
        assertEquals(5, merged.get(StatType.ATTACK_DAMAGE).getCurrent(0)); // base-only stat kept
        assertNotNull(merged.get(StatType.HEALTH_RESOURCE));
    }

    @Test
    void defaultStatsAreDeepCopied() {
        Map<StatType, Stat> a = DefaultStats.getDefaultStats();
        Map<StatType, Stat> b = DefaultStats.getDefaultStats();
        a.get(StatType.HEALTH_MAX).modify(50);
        // Mutating one copy must not affect another.
        assertEquals(100, b.get(StatType.HEALTH_MAX).getCurrent(0));
    }

    @Test
    void setActiveClassLayersClassBonusOnDefaults() {
        PlayerProgression progression = new PlayerProgression(UUID.randomUUID());
        StatManager manager = new StatManager(DefaultStats.getDefaultStats());
        progression.getProgression(RPGClassType.TANK).setLevel(1);
        progression.setActiveClass(RPGClassType.TANK, manager);

        assertEquals(300, manager.getCurrentValue(StatType.HEALTH_MAX, System.currentTimeMillis()));
        assertEquals(300, manager.getCurrentValue(StatType.HEALTH_RESOURCE, System.currentTimeMillis()));
    }

    @Test
    void setActiveClassNoneKeepsOnlyDefaults() {
        PlayerProgression progression = new PlayerProgression(UUID.randomUUID());
        StatManager manager = new StatManager(DefaultStats.getDefaultStats());
        progression.setActiveClass(RPGClassType.NONE, manager);

        assertEquals(100, manager.getCurrentValue(StatType.HEALTH_MAX, System.currentTimeMillis()));
        assertEquals(100, manager.getCurrentValue(StatType.HEALTH_RESOURCE, System.currentTimeMillis()));
    }
}
