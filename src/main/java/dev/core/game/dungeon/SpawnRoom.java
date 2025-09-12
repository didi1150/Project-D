package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SpawnRoom extends DungeonRoom {

    private final Set<Point3D> safeSpawnZone;
    private final Point3D primarySpawnPoint;

    public SpawnRoom(String id, Point3D center, int size) {
        super(id, center, Math.max(12, size), 4); // Minimum size 12, height 4
        this.safeSpawnZone = new HashSet<>();
        this.primarySpawnPoint = new Point3D(center.getX(), center.getY() + 1, center.getZ());
        generateSafeZone();
    }

    @Override
    protected void generateRoomStructure() {
        // Generate a safe, well-lit circular room
        int radius = size / 2;

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                double distance = Math.sqrt(Math.pow(x - center.getX(), 2) + Math.pow(z - center.getZ(), 2));

                if (distance <= radius) {
                    addRoomBlocks(x, z);
                }
            }
        }

        addWallsForBlocks();
    }

    private void generateSafeZone() {
        // Create a safe zone in the center where no mobs can spawn
        int safeRadius = 4;

        for (int x = center.getX() - safeRadius; x <= center.getX() + safeRadius; x++) {
            for (int z = center.getZ() - safeRadius; z <= center.getZ() + safeRadius; z++) {
                double distance = Math.sqrt(Math.pow(x - center.getX(), 2) + Math.pow(z - center.getZ(), 2));

                if (distance <= safeRadius) {
                    for (int y = center.getY(); y <= center.getY() + height; y++) {
                        safeSpawnZone.add(new Point3D(x, y, z));
                    }
                }
            }
        }
    }

    // Override spawn generation to prevent mobs in spawn room
    @Override
    protected void generateSpawnLocationsAndDecorations() {
        // Spawn rooms don't get monster spawns - only decorations
        Random roomRandom = new Random(id.hashCode());

        // Generate minimal decorations - torches and basic furniture only
        DecorationGenerator decorationGenerator = new DecorationGenerator(roomRandom);
        List<DecorationElement> safeDecorations = decorationGenerator.generateDecorations(this);

        // Filter out potentially dangerous decorations
        safeDecorations = safeDecorations.stream().filter(decoration -> isSafeDecoration(decoration.getType()))
                .collect(java.util.stream.Collectors.toList());

        decorations.addAll(safeDecorations);

        // Add decoration blocks to the decorative blocks set
        for (DecorationElement decoration : decorations) {
            decorativeBlocks.addAll(decoration.getOccupiedPositions());
        }
    }

    private boolean isSafeDecoration(DecorationType type) {
        return type == DecorationType.FLOOR_VEGETATION || type == DecorationType.STONE_STRUCTURE
                || type == DecorationType.BARREL_GROUP;
    }

    @Override
    public Point3D getConnectionPoint(Direction direction) {
        int radius = size / 2;
        return direction.apply(center, radius - 1);
    }

    @Override
    public RoomType getType() {
        return RoomType.SPAWN_ROOM;
    }

    public Set<Point3D> getSafeSpawnZone() {
        return new HashSet<>(safeSpawnZone);
    }

    public Point3D getPrimarySpawnPoint() {
        return primarySpawnPoint;
    }

    public List<Point3D> getPlayerSpawnPoints(int maxPlayers) {
        List<Point3D> spawnPoints = new ArrayList<>();

        if (maxPlayers == 1) {
            spawnPoints.add(primarySpawnPoint);
            return spawnPoints;
        }

        // Arrange multiple spawn points in a circle around the center
        double angleStep = 2 * Math.PI / maxPlayers;
        int spawnRadius = 2;

        for (int i = 0; i < maxPlayers; i++) {
            double angle = i * angleStep;
            int x = (int) (center.getX() + spawnRadius * Math.cos(angle));
            int z = (int) (center.getZ() + spawnRadius * Math.sin(angle));
            spawnPoints.add(new Point3D(x, center.getY() + 1, z));
        }

        return spawnPoints;
    }
}
