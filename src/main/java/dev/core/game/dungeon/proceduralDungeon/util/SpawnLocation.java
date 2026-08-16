package dev.core.game.dungeon.proceduralDungeon.util;

import dev.core.game.coords.Point3D;

public class SpawnLocation {

    private final Point3D position;
    private final SpawnTier tier;
    private final double spawnChance;
    private final int maxEnemyLevel;
    private final boolean isEliteSpawn;
    private final boolean isMiniBossSpawn;

    public SpawnLocation(Point3D position, SpawnTier tier, double spawnChance, int maxEnemyLevel,
            boolean isEliteSpawn) {
        this(position, tier, spawnChance, maxEnemyLevel, isEliteSpawn, false);
    }

    public SpawnLocation(Point3D position, SpawnTier tier, double spawnChance, int maxEnemyLevel, boolean isEliteSpawn,
            boolean isMiniBossSpawn) {
        this.position = position;
        this.tier = tier;
        this.spawnChance = Math.max(0.0, Math.min(1.0, spawnChance));
        this.maxEnemyLevel = Math.max(1, maxEnemyLevel);
        this.isEliteSpawn = isEliteSpawn;
        this.isMiniBossSpawn = isMiniBossSpawn;
    }

    public Point3D getPosition() {
        return position.add(new Point3D(0,1,0)); // position is in floor, so return pos above
    }

    public SpawnTier getTier() {
        return tier;
    }

    public double getSpawnChance() {
        return spawnChance;
    }

    public int getMaxEnemyLevel() {
        return maxEnemyLevel;
    }

    public boolean isEliteSpawn() {
        return isEliteSpawn;
    }

    public boolean isMiniBossSpawn() {
        return isMiniBossSpawn;
    }

    @Override
    public String toString() {
        return String.format("SpawnLocation{pos=%s, tier=%s, maxLvl=%d, elite=%s}", position, tier, maxEnemyLevel,
                isEliteSpawn);
    }
}
