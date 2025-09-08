package dev.core.storage.database;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.CombatStat;
import dev.core.stat.DefaultStats;
import dev.core.stat.ResourceStat;
import dev.core.stat.Stat;
import dev.core.stat.StatType;

public class PlayerClassProgression {

    private final RPGClassType classType;
    private int level;
    private int xp;
    private int usableItems;

    public PlayerClassProgression(RPGClassType classType) {
        this.classType = classType;
        this.level = 1;
        this.xp = 0;
        this.usableItems = 5; // default
    }

    public RPGClassType getClassType() {
        return classType;
    }

    public void setLevel(int newLevel) {
        if (newLevel < 1) {
            newLevel = 1;
        }

        int oldLevel = this.level;
        this.level = newLevel;

        // Update usable items based on level change
        int levelDifference = newLevel - oldLevel;
        this.usableItems += levelDifference; // 1 slot per level

        // Ensure minimum usable items (5 at level 1)
        if (this.usableItems < 5) {
            this.usableItems = 5;
        }

        // Adjust XP to be appropriate for the new level
        // Set XP to the minimum required for this level
        if (newLevel > 1) {
            this.xp = Math.max(0,
                    ExperienceTable.getXpForLevel(newLevel) - ExperienceTable.getXpForLevel(newLevel - 1));
        } else {
            this.xp = 0;
        }
    }

    public void setXp(int newXp) {
        if (newXp < 0) {
            newXp = 0;
        }

        this.xp = newXp;

        // Check if we need to level up
        while (this.level < Integer.MAX_VALUE && this.xp >= ExperienceTable.getXpForLevel(this.level + 1)) {
            this.xp -= ExperienceTable.getXpForLevel(this.level + 1);
            this.level++;
            this.usableItems++; // 1 slot per level
        }

        // Check if we need to level down (in case XP was set to a lower value)
        while (this.level > 1) {
            int xpRequiredForCurrentLevel = ExperienceTable.getXpForLevel(this.level)
                    - ExperienceTable.getXpForLevel(this.level - 1);
            if (this.xp >= 0) {
                break; // XP is valid for current level
            }

            // Need to level down
            this.level--;
            this.usableItems = Math.max(5, this.usableItems - 1); // Don't go below 5
            this.xp += xpRequiredForCurrentLevel;
        }
    }

    public void setUsableItems(int usableItems) {
        this.usableItems = Math.max(5, usableItems); // Ensure minimum of 5
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }

    public int getUsableItems() {
        return usableItems;
    }

    public void addXp(int amount) {
        if (amount <= 0) {
            return;
        }

        xp += amount;
        while (level < Integer.MAX_VALUE && xp >= ExperienceTable.getXpForLevel(level + 1)) {
            xp -= ExperienceTable.getXpForLevel(level + 1);
            level++;
            usableItems++; // simple rule: 1 slot per level
        }
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