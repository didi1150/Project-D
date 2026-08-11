package dev.core.stat;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.loader.StatLoader;

/**
 * Registry of the stat sets defined in stats.yml. The {@code NONE} entry is the
 * universal base stat set every entity falls back to (players before class
 * selection, mobs and bosses without their own stats); the playable class
 * entries are bonuses layered on top of that base.
 */
public class DefaultStats {

    private static Map<RPGClassType, Map<StatType, Stat>> defaultStats = new HashMap<RPGClassType, Map<StatType, Stat>>();

    /**
     * Returns a deep copy of the stored stats for the given class. Never returns
     * {@code null}: unknown classes yield an empty map so entity construction can
     * always build a {@code StatManager}.
     */
    public static Map<StatType, Stat> getStatsByClass(RPGClassType classType) {
        Map<StatType, Stat> stored = defaultStats.get(classType);
        if (stored == null) {
            return new HashMap<>();
        }
        return StatLoader.copyStats(stored);
    }

    /**
     * The universal base stat set ({@code statDefaults.NONE}) used by entities
     * that have no class or no dedicated stats.
     */
    public static Map<StatType, Stat> getDefaultStats() {
        return getStatsByClass(RPGClassType.NONE);
    }

    public static void loadAll(Map<RPGClassType, Map<StatType, Stat>> stats) {
        defaultStats.putAll(stats);
    }
}
