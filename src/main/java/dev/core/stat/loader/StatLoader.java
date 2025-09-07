package dev.core.stat.loader;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.CombatStat;
import dev.core.stat.ResourceStat;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class StatLoader {

    public static Map<RPGClassType, Map<StatType, Stat>> loadDefaultStats(ConfigProvider provider) {
        Map<RPGClassType, Map<StatType, Stat>> classStats = new HashMap<>();
        ConfigSection root = provider.getRoot().getSection("statDefaults");

        for (RPGClassType type : RPGClassType.values()) {
            Map<StatType, Stat> stats = load(type, root.getSection(type.toString()));
            classStats.put(type, stats);
        }

        return classStats;
    }

    private static Map<StatType, Stat> load(RPGClassType type, ConfigSection section) {
        Map<StatType, Stat> stats = new HashMap<>();
        for (String key : section.getKeys()) {
            int amount = section.getInt(key, 0);
            CombatStat stat = new CombatStat(key, amount);
            stats.put(StatType.valueOf(key), stat);
        }
        Stat healthMaxStat = stats.get(StatType.HEALTH_MAX);
        Stat healthRegenStat = stats.get(StatType.HEALTH_REGEN);
        Stat healAndShieldPowerStat = stats.get(StatType.HEAL_AND_SHIELD_POWER);

        Stat manaMaxStat = stats.get(StatType.MANA_MAX);
        Stat manaRegenStat = stats.get(StatType.MANA_REGEN);
        stats.put(StatType.HEALTH_RESOURCE,
                new ResourceStat(StatType.HEALTH_RESOURCE.toString(), t -> healthMaxStat.getCurrent(t),
                        t -> healthRegenStat.getCurrent(t) * (1 + healAndShieldPowerStat.getCurrent(t) / 100),
                        System.currentTimeMillis()));
        stats.put(StatType.MANA_RESOURCE, new ResourceStat(StatType.MANA_RESOURCE.toString(),
                t -> manaMaxStat.getCurrent(t), t -> manaRegenStat.getCurrent(t), System.currentTimeMillis()));

        return stats;
    }

}
