package dev.core.stat.loader;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.Stat;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.impl.ResourceStat;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class StatLoader {

    /**
     * Loads the {@code statDefaults} section of stats.yml into a map keyed by
     * class. The {@link RPGClassType#NONE} entry is loaded alongside the playable
     * classes: it is the universal base stat set that every entity falls back to
     * (players before class selection, mobs and bosses without their own stats).
     * The playable class entries are {@code bonus} stats layered on top of the
     * NONE base.
     */
    public static Map<RPGClassType, Map<StatType, Stat>> loadDefaultStats(ConfigProvider provider) {
        Map<RPGClassType, Map<StatType, Stat>> classStats = new HashMap<>();
        ConfigSection root = provider.getRoot().getSection("statDefaults");
        if (root == null) {
            return classStats;
        }
        for (RPGClassType type : RPGClassType.values()) {
            classStats.put(type, loadStats(root.getSection(type.toString())));
        }
        return classStats;
    }

    /**
     * Loads a {@code Map<StatType, Stat>} from a config section of stat
     * name/value pairs, wiring up the health and mana resource stats from their
     * base stats.
     */
    public static Map<StatType, Stat> loadStats(ConfigSection section) {
        Map<StatType, Stat> stats = new HashMap<>();
        if (section == null) {
            return stats;
        }
        for (String key : section.getKeys()) {
            int amount = section.getInt(key, 0);
            CombatStat stat = new CombatStat(key, amount);
            stats.put(StatType.valueOf(key), stat);
        }
        synthesizeResources(stats);
        return stats;
    }

    /**
     * Deep-copies a stat map: every combat stat is copied so entities never share
     * mutable stat objects (modifiers, resource ticking), and the health / mana
     * resources are re-wired to the copied base stats.
     */
    public static Map<StatType, Stat> copyStats(Map<StatType, Stat> stats) {
        return mergeStats(stats, Map.of());
    }

    /**
     * Merges additive {@code bonus} stats onto a {@code base} stat set. The
     * result is a fresh set of combat stats (base value + bonus value, with any
     * bonus-only stats kept) and freshly synthesized resource stats, safe to
     * install into an entity's {@code StatManager}.
     */
    public static Map<StatType, Stat> mergeStats(Map<StatType, Stat> base, Map<StatType, Stat> bonus) {
        Map<StatType, Stat> merged = new HashMap<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<StatType, Stat> entry : base.entrySet()) {
            StatType type = entry.getKey();
            Stat stat = entry.getValue();
            if (!(stat instanceof CombatStat combatStat)) {
                continue; // resource stats are re-synthesized below
            }
            double bonusValue = bonus.containsKey(type) ? bonus.get(type).getCurrent(now) : 0;
            merged.put(type, new CombatStat(combatStat.getName(), combatStat.getCurrent(now) + bonusValue));
        }
        // Stats the bonus provides that the base does not cover are kept as-is.
        for (Map.Entry<StatType, Stat> entry : bonus.entrySet()) {
            StatType type = entry.getKey();
            if (!merged.containsKey(type) && entry.getValue() instanceof CombatStat combatStat) {
                merged.put(type, new CombatStat(combatStat.getName(), combatStat.getCurrent(now)));
            }
        }
        synthesizeResources(merged);
        return merged;
    }

    private static void synthesizeResources(Map<StatType, Stat> stats) {
        Stat healthMaxStat = stats.getOrDefault(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 0));
        Stat healthRegenStat = stats.getOrDefault(StatType.HEALTH_REGEN, new CombatStat("HEALTH_REGEN", 0));
        Stat healAndShieldPowerStat = stats.getOrDefault(StatType.HEAL_AND_SHIELD_POWER,
                new CombatStat("HEAL_AND_SHIELD_POWER", 0));

        Stat manaMaxStat = stats.getOrDefault(StatType.MANA_MAX, new CombatStat("MANA_MAX", 0));
        Stat manaRegenStat = stats.getOrDefault(StatType.MANA_REGEN, new CombatStat("MANA_REGEN", 0));
        // Raw (getBaseValue) bases: the StatEngine applies all modifiers — legacy
        // buckets via ModifierBucketProvider and item stats via ItemStatProvider —
        // so bucket-applied bases here would double-count.
        stats.put(StatType.HEALTH_RESOURCE,
                new ResourceStat(StatType.HEALTH_RESOURCE.toString(), t -> healthMaxStat.getBaseValue(t),
                        t -> healthRegenStat.getBaseValue(t) * (1 + healAndShieldPowerStat.getBaseValue(t) / 100),
                        System.currentTimeMillis()));
        stats.put(StatType.MANA_RESOURCE, new ResourceStat(StatType.MANA_RESOURCE.toString(),
                t -> manaMaxStat.getBaseValue(t), t -> manaRegenStat.getBaseValue(t), System.currentTimeMillis()));
    }

}
