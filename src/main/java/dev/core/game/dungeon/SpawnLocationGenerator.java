package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SpawnLocationGenerator {
    private final Random random;

    // Scaling factors for spawn location generation
    private static final double BASE_SPAWN_DENSITY = 0.3; // Base spawns per floor area
    private static final int MIN_ROOM_SIZE_FOR_ELITE = 14;
    private static final int MIN_ROOM_SIZE_FOR_BOSS = 20;

    public SpawnLocationGenerator(Random random) {
        this.random = random;
    }

    public List<SpawnLocation> generateSpawnLocations(DungeonRoom room) {
        List<SpawnLocation> spawnLocations = new ArrayList<>();

        int roomSize = room.getSize();
        int floorArea = room.getFloorBlocks().size();

        // Calculate number of spawn locations based on room size
        int baseSpawnCount = Math.max(1, (int) (floorArea * BASE_SPAWN_DENSITY));
        int bonusSpawns = Math.max(0, (roomSize - 10) / 4); // Extra spawns for larger rooms
        int totalSpawns = baseSpawnCount + bonusSpawns;

        // Determine spawn tier distribution based on room size
        List<SpawnTier> availableTiers = getAvailableSpawnTiers(roomSize);

        Set<Point3D> usedPositions = new HashSet<>();

        for (int i = 0; i < totalSpawns; i++) {
            Point3D spawnPosition = findValidSpawnPosition(room, usedPositions);
            if (spawnPosition != null) {
                SpawnTier tier = selectSpawnTier(availableTiers, roomSize);
                SpawnLocation spawn = createSpawnLocation(spawnPosition, tier, roomSize);
                spawnLocations.add(spawn);

                // Mark area around spawn as used to prevent clustering
                markAreaAsUsed(spawnPosition, usedPositions, 3);
            }
        }

        return spawnLocations;
    }

    private List<SpawnTier> getAvailableSpawnTiers(int roomSize) {
        List<SpawnTier> tiers = new ArrayList<>();

        tiers.add(SpawnTier.BASIC); // Always available

        if (roomSize >= 8) {
            tiers.add(SpawnTier.ADVANCED);
        }

        if (roomSize >= MIN_ROOM_SIZE_FOR_ELITE) {
            tiers.add(SpawnTier.ELITE);
        }

        if (roomSize >= MIN_ROOM_SIZE_FOR_BOSS) {
            tiers.add(SpawnTier.BOSS);
        }

        return tiers;
    }

    private SpawnTier selectSpawnTier(List<SpawnTier> availableTiers, int roomSize) {
        // Larger rooms have higher chances for advanced tiers
        double roomSizeBonus = Math.min(0.4, (roomSize - 10) * 0.05); // Up to 40% bonus

        double roll = random.nextDouble();

        // Higher tier spawns are rarer but more likely in large rooms
        if (availableTiers.contains(SpawnTier.BOSS) && roll < (0.1 + roomSizeBonus)) {
            return SpawnTier.BOSS;
        } else if (availableTiers.contains(SpawnTier.ELITE) && roll < (0.25 + roomSizeBonus)) {
            return SpawnTier.ELITE;
        } else if (availableTiers.contains(SpawnTier.ADVANCED) && roll < (0.5 + roomSizeBonus)) {
            return SpawnTier.ADVANCED;
        } else {
            return SpawnTier.BASIC;
        }
    }

    private Point3D findValidSpawnPosition(DungeonRoom room, Set<Point3D> usedPositions) {
        Set<Point3D> floorBlocks = room.getFloorBlocks();
        Point3D roomCenter = room.getCenter();
        List<Point3D> candidatePositions = new ArrayList<>();

        for (Point3D floor : floorBlocks) {
            Point3D spawnPos = new Point3D(floor.getX(), floor.getY() + 1, floor.getZ());

            // Ensure minimum distance from room center (don't spawn right in the entrance)
            double distanceFromCenter = Math.sqrt(
                    Math.pow(floor.getX() - roomCenter.getX(), 2) + Math.pow(floor.getZ() - roomCenter.getZ(), 2));

            if (distanceFromCenter >= 2 && !usedPositions.contains(spawnPos) && room.getAirBlocks().contains(spawnPos)
                    && !room.getDecorativeBlocks().contains(spawnPos)) {
                candidatePositions.add(spawnPos);
            }
        }

        if (candidatePositions.isEmpty()) {
            return null;
        }

        return candidatePositions.get(random.nextInt(candidatePositions.size()));
    }

    private SpawnLocation createSpawnLocation(Point3D position, SpawnTier tier, int roomSize) {
        // Calculate spawn parameters based on tier and room size
        double spawnChance = tier.getBaseSpawnChance();
        int maxLevel = Math.min(tier.getMaxLevel(), tier.getMinLevel() + (roomSize / 5));
        boolean isEliteSpawn = tier == SpawnTier.ELITE || tier == SpawnTier.BOSS;

        // Larger rooms increase spawn chance slightly
        double roomBonus = Math.min(0.2, roomSize * 0.01);
        spawnChance = Math.min(1.0, spawnChance + roomBonus);

        return new SpawnLocation(position, tier, spawnChance, maxLevel, isEliteSpawn);
    }

    private void markAreaAsUsed(Point3D center, Set<Point3D> usedPositions, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Point3D pos = new Point3D(center.getX() + x, center.getY(), center.getZ() + z);
                usedPositions.add(pos);
            }
        }
    }
}
