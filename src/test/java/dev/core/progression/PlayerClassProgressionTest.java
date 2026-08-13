package dev.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.storage.database.ExperienceTable;

public class PlayerClassProgressionTest {

    @BeforeAll
    static void setup() {
        // Stub the ExperienceTable
        // Example: XP requirement = level * 100
        ExperienceTable.setStrategy(level -> level * 100);

        // Stub DefaultStats for MAGE
        Map<StatType, Stat> mageStats = new EnumMap<>(StatType.class);
        mageStats.put(StatType.HEALTH_MAX, new CombatStat("Health", 100));
        mageStats.put(StatType.HEALTH_REGEN, new CombatStat("HealthRegen", 10));
        mageStats.put(StatType.HEAL_AND_SHIELD_POWER, new CombatStat("HealPower", 20));
        mageStats.put(StatType.MANA_MAX, new CombatStat("Mana", 50));
        mageStats.put(StatType.MANA_REGEN, new CombatStat("ManaRegen", 5));

        DefaultStats.loadAll(Map.of(RPGClassType.MAGE, mageStats));
    }

    @Test
    void testDefaultInitialization() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);

        assertEquals(RPGClassType.MAGE, prog.getClassType());
        assertEquals(0, prog.getLevel());
        assertEquals(0, prog.getXp());
        assertEquals(5, prog.getUsableItems());
    }

    @Test
    void testSetLevelIncreasesUsableItems() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);
        prog.setLevel(3);

        assertEquals(3, prog.getLevel());
        assertTrue(prog.getUsableItems() >= 5);
    }

    @Test
    void testSetLevelBelowZeroResetsToZero() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);
        prog.setLevel(-5);

        assertEquals(0, prog.getLevel());
        assertEquals(5, prog.getUsableItems());
    }

    @Test
    void testAddXpLevelsUp() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);
        prog.addXp(250); // > 200 required for level 2 and 3

        assertEquals(2, prog.getLevel());
        assertTrue(prog.getUsableItems() >= 6);
    }

    @Test
    void testSetXpCausesLevelUpAndDown() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);

        prog.setXp(500); // Enough for multiple levels
        assertTrue(prog.getLevel() > 1);

        prog.setXp(10); // Force level down
        assertEquals(1, prog.getLevel());
        assertEquals(5, prog.getUsableItems()); // Should not go below 5
    }

    @Test
    void testSetUsableItemsCannotGoBelowFive() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);
        prog.setUsableItems(0);

        assertEquals(5, prog.getUsableItems());
    }

    @Test
    void testGetBonusStatsScalesWithLevel() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.MAGE);
        prog.setLevel(3); // +10% scaling

        Map<StatType, Stat> stats = prog.getBonusStats();

        // Bonus stats are combat stats only; resources are wired from the base.
        assertNull(stats.get(StatType.HEALTH_RESOURCE));
        assertNull(stats.get(StatType.MANA_RESOURCE));

        double scaledHealth = stats.get(StatType.HEALTH_MAX).getCurrent(System.currentTimeMillis());
        assertEquals(100 * 1.10, scaledHealth, 0.01); // 10% increase
    }

    @Test
    void testGetBonusStatsIsEmptyForNoneClass() {
        PlayerClassProgression prog = new PlayerClassProgression(RPGClassType.NONE);
        assertTrue(prog.getBonusStats().isEmpty());
    }
}
