package dev.core.progression;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;
import dev.core.storage.database.ExperienceTable;

public class PlayerClassProgression {

    private final RPGClassType classType;
    private int level;
    private int totalXp; // total XP, not per-level
    private int usableItems;

    public PlayerClassProgression(RPGClassType classType) {
        this.classType = classType;
        this.level = 0;
        this.totalXp = 0;
        this.usableItems = 5; // default
    }

    public RPGClassType getClassType() {
        return classType;
    }

    public void setLevel(int newLevel) {
        if (newLevel < 0)
            newLevel = 0;

        this.level = newLevel;
        this.totalXp = ExperienceTable.getXpForLevel(newLevel); // set to minimum XP of new level
        this.usableItems = 5 + newLevel; // 1 slot per level, min 5
    }

    public void setXp(int newXp) {
        if (newXp < 0)
            newXp = 0;
        this.totalXp = newXp;
        recalcLevelAndItems();
    }

    public void addXp(int amount) {
        if (amount <= 0)
            return;
        this.totalXp += amount;
        recalcLevelAndItems();
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return totalXp;
    }

    /**
     * XP relative to current level
     */
    public int getRelativeXp() {
        return totalXp - ExperienceTable.getXpForLevel(level);
    }

    /**
     * XP left until next level
     */
    public int getXpToNextLevel() {
        return ExperienceTable.getXpForLevel(level + 1) - totalXp;
    }

    private void recalcLevelAndItems() {
        int newLevel = 1;
        int cap = ExperienceTable.getLevelCap();

        while (newLevel < cap && totalXp >= ExperienceTable.getXpForLevel(newLevel + 1)) {
            newLevel++;
        }

        this.level = newLevel;
        this.usableItems = 5 + (newLevel - 1); // min 5, +1 per level
    }

    public void setUsableItems(int usableItems) {
        this.usableItems = Math.max(5, usableItems); // Ensure minimum of 5
    }

    public int getUsableItems() {
        return usableItems;
    }

    public Map<StatType, Stat> getBaseStats() {
        Map<StatType, Stat> base = DefaultStats.getStatsByClass(classType);
        if (base == null) {
            return new HashMap<>();
        }

        Map<StatType, Stat> scaled = new HashMap<>();
        for (Map.Entry<StatType, Stat> entry : base.entrySet()) {
            StatType type = entry.getKey();
            Stat stat = entry.getValue();

            // +5% per level above 1
            double multiplier = 1.0 + 0.05 * (level - 1);
            double newCurrent = stat.getCurrent(System.currentTimeMillis()) * multiplier;

            // Create a new stat of the same subclass
            if (stat instanceof CombatStat) {
                scaled.put(type, new CombatStat(stat.getName(), newCurrent));
            }
        }

        Stat healthMaxStat = scaled.get(StatType.HEALTH_MAX);
        Stat healthRegenStat = scaled.get(StatType.HEALTH_REGEN);
        Stat healAndShieldPowerStat = scaled.get(StatType.HEAL_AND_SHIELD_POWER);

        Stat manaMaxStat = scaled.get(StatType.MANA_MAX);
        Stat manaRegenStat = scaled.get(StatType.MANA_REGEN);

        scaled.put(StatType.HEALTH_RESOURCE,
                new ResourceStat(StatType.HEALTH_RESOURCE.toString(), t -> healthMaxStat.getCurrent(t),
                        t -> healthRegenStat.getCurrent(t) * (1 + healAndShieldPowerStat.getCurrent(t) / 100),
                        System.currentTimeMillis()));

        scaled.put(StatType.MANA_RESOURCE, new ResourceStat(StatType.MANA_RESOURCE.toString(),
                t -> manaMaxStat.getCurrent(t), t -> manaRegenStat.getCurrent(t), System.currentTimeMillis()));

        return scaled;
    }
}