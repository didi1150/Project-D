package dev.core.game.dungeon;

public enum SpawnTier {
    BASIC(1, 5, 0.8), // Level 1-5, 80% spawn chance
    ADVANCED(3, 10, 0.6), // Level 3-10, 60% spawn chance
    ELITE(8, 15, 0.4), // Level 8-15, 40% spawn chance
    BOSS(12, 20, 0.2); // Level 12-20, 20% spawn chance (rare)

    private final int minLevel;
    private final int maxLevel;
    private final double baseSpawnChance;

    SpawnTier(int minLevel, int maxLevel, double baseSpawnChance) {
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
        this.baseSpawnChance = baseSpawnChance;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public double getBaseSpawnChance() {
        return baseSpawnChance;
    }
}
