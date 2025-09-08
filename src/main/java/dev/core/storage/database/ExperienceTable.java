package dev.core.storage.database;

import java.util.HashMap;
import java.util.Map;

public class ExperienceTable {
    private static final Map<Integer, Integer> xpThresholds = new HashMap<>();

    static {
        int xp = 100;
        for (int level = 1; level <= 100; level++) {
            xpThresholds.put(level, xp);
            xp += level * 50; // gets harder each level
        }
    }

    public static int getXpForLevel(int level) {
        return xpThresholds.getOrDefault(level, Integer.MAX_VALUE);
    }
}
