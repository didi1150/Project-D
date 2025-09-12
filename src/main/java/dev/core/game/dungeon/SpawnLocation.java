package dev.core.game.dungeon;

public class SpawnLocation {
    private final Point3D position;
    private final SpawnTier tier;
    private final double spawnChance;
    private final int maxEnemyLevel;
    private final boolean isEliteSpawn;

    public SpawnLocation(Point3D position, SpawnTier tier, double spawnChance, int maxEnemyLevel,
            boolean isEliteSpawn) {
        this.position = position;
        this.tier = tier;
        this.spawnChance = Math.max(0.0, Math.min(1.0, spawnChance));
        this.maxEnemyLevel = Math.max(1, maxEnemyLevel);
        this.isEliteSpawn = isEliteSpawn;
    }

    public Point3D getPosition() {
        return position;
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

    @Override
    public String toString() {
        return String.format("SpawnLocation{pos=%s, tier=%s, maxLvl=%d, elite=%s}", position, tier, maxEnemyLevel,
                isEliteSpawn);
    }

}
