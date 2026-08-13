package dev.core.storage.database;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ExperienceTable {

    private static final Map<Integer, Integer> xpThresholds = new HashMap<>();
    private static Function<Integer, Integer> strategy;
    private static int levelCap = 100; // default cap

    static {
        // Default strategy
        strategy = level -> {
            int xp = 100;
            for (int i = 1; i <= level; i++) {
                xp += (i - 1) * 50; // progressively harder
            }
            return xp;
        };

        recalculateThresholds();
    }

    /**
     * Returns the XP required to reach a given level.
     *
     * @param level The level
     * @return The XP required for this level
     */
    public static int getXpForLevel(int level) {
        if (level > levelCap) {
            return Integer.MAX_VALUE; // unreachable
        }
        if (level == 0) {
            return 0;
        }
        return xpThresholds.getOrDefault(level, Integer.MAX_VALUE);
    }

    /**
     * Override the XP requirement strategy.
     *
     * @param newStrategy Function that takes a level and returns required XP
     */
    public static void setStrategy(Function<Integer, Integer> newStrategy) {
        strategy = newStrategy;
        recalculateThresholds();
    }

    /**
     * Set the maximum level cap. Rebuilds XP thresholds accordingly.
     *
     * @param cap The new maximum level
     */
    public static void setLevelCap(int cap) {
        if (cap < 1) {
            throw new IllegalArgumentException("Level cap must be at least 1");
        }
        levelCap = cap;
        recalculateThresholds();
    }

    public static int getLevelCap() {
        return levelCap;
    }

    /**
     * Rebuilds the XP thresholds using the current strategy.
     */
    private static void recalculateThresholds() {
        xpThresholds.clear();
        for (int level = 1; level <= levelCap; level++) {
            xpThresholds.put(level, strategy.apply(level));
        }
    }
}
