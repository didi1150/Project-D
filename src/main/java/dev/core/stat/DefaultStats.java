package dev.core.stat;

import java.util.HashMap;
import java.util.Map;

import dev.core.entity.rpgclass.RPGClassType;

public class DefaultStats {

    private static Map<RPGClassType, Map<StatType, Stat>> defaultStats = new HashMap<RPGClassType, Map<StatType, Stat>>();

    public static Map<StatType, Stat> getStatsByClass(RPGClassType classType) {
        if (!defaultStats.containsKey(classType)) {
            return null;
        }

        return new HashMap<>(defaultStats.get(classType));
    }

    public static void loadAll(Map<RPGClassType, Map<StatType, Stat>> stats) {
        defaultStats.putAll(stats);
    }
}
