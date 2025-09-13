package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class EndPortalRoom extends DungeonRoom {
    private final Point3D portalCenter;
    private final Set<Point3D> portalStructure;
    private final Set<Point3D> ceremonyArea;
    private Direction connectionDirection; // Track where the connection will be

    public EndPortalRoom(String id, Point3D center, int size) {
        super(id, center, Math.max(30, size), 20); // Minimum size 20, height 20 for grandeur
        this.portalCenter = new Point3D(center.getX(), center.getY() + 1, center.getZ());
        this.portalStructure = new HashSet<>();
        this.ceremonyArea = new HashSet<>();
        this.connectionDirection = null; // Will be set when connection is made
        Random roomRandom = new Random(id.hashCode());

        generateEndPortalSpawnLocations(roomRandom);
        generatePortalStructure();
        generateEndPortalRoomDecorations(roomRandom);
    }

    // Method to set connection direction when tunnel is created
    public void setConnectionDirection(Direction direction) {
        this.connectionDirection = direction;
        // Regenerate decorations to avoid blocking the entrance
        decorations.clear();
        decorativeBlocks.clear();
        Random roomRandom = new Random(id.hashCode());
        generateEndPortalRoomDecorations(roomRandom);
    }

    @Override
    protected void generateRoomStructure() {
        // Generate a large ceremonial chamber
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

    private void generatePortalStructure() {
        // Create the nether portal frame structure
        int portalWidth = 4;
        int portalHeight = 5;

        // Portal frame blocks
        for (int x = -portalWidth / 2; x <= portalWidth / 2; x++) {
            for (int y = 0; y <= portalHeight; y++) {
                Point3D framePos = new Point3D(portalCenter.getX() + x, portalCenter.getY() + y, portalCenter.getZ());

                // Only frame blocks, not interior
                if (x == -portalWidth / 2 || x == portalWidth / 2 || y == 0 || y == portalHeight) {
                    portalStructure.add(framePos);
                }
            }
        }

        // Ceremonial platform under + around portal
        int platformRadius = 6;
        for (int x = -platformRadius; x <= platformRadius; x++) {
            for (int z = -platformRadius; z <= platformRadius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= platformRadius) {
                    Point3D platformPos = new Point3D(portalCenter.getX() + x, portalCenter.getY() - 1,
                            portalCenter.getZ() + z);
                    ceremonyArea.add(platformPos);
                }
            }
        }
    }

    // Override spawn generation for boss room characteristics
    @Override
    protected void generateSpawnLocationsAndDecorations() {
    }

    private void generateEndPortalRoomDecorations(Random roomRandom) {
        // Generate ceremonial decorations
        DecorationGenerator decorationGenerator = new DecorationGenerator(roomRandom);
        List<DecorationElement> endDecorations = generateEndRoomDecorations(decorationGenerator, roomRandom);
        decorations.addAll(endDecorations);

        // Add decoration blocks
        for (DecorationElement decoration : decorations) {
            decorativeBlocks.addAll(decoration.getOccupiedPositions());
        }
    }

    private void generateEndPortalSpawnLocations(Random roomRandom) {
        // Generate elite/boss spawns around the perimeter
        SpawnLocationGenerator spawnGenerator = new SpawnLocationGenerator(roomRandom);
        List<SpawnLocation> allSpawns = spawnGenerator.generateSpawnLocations(this);

        // Convert regular spawns to elite spawns for end room
        for (SpawnLocation spawn : allSpawns) {
            Point3D position = spawn.getPosition();
            SpawnTier tier = spawn.getTier() == SpawnTier.BASIC ? SpawnTier.ELITE : SpawnTier.BOSS;

            SpawnLocation eliteSpawn = new SpawnLocation(position, tier, spawn.getSpawnChance() * 0.8, // Slightly lower
                                                                                                       // chance
                    spawn.getMaxEnemyLevel() + 2, // Higher level
                    true // Always elite
            );

            spawnLocations.add(eliteSpawn);
        }
    }

    private List<DecorationElement> generateEndRoomDecorations(DecorationGenerator generator, Random random) {
        List<DecorationElement> endDecorations = new ArrayList<>();

        int radius = size / 2;
        int margin = 2; // ensure clearance from walls

        // Get connection point if connection direction is known
        Point3D connectionPoint = null;
        if (connectionDirection != null) {
            connectionPoint = getConnectionPoint(connectionDirection);
        }

        // Altars - but skip the one closest to the connection point
        List<Direction> altarDirections = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            altarDirections.add(dir);
        }

        // If we have a connection direction, remove the closest altar direction
        if (connectionDirection != null) {
            // Remove the direction that would place an altar closest to the entrance
            Direction closestAltarDirection = findClosestAltarDirection(connectionDirection);
            altarDirections.remove(closestAltarDirection);
        }

        // Create altars in the remaining directions
        for (Direction dir : altarDirections) {
            Point3D altarPos = dir.apply(portalCenter, radius - margin - 2).add(new Point3D(0, -1, 0));

            // Double-check distance from connection point
            if (connectionPoint != null) {
                double distanceToConnection = altarPos.distance(connectionPoint);
                if (distanceToConnection < 8) { // If too close to entrance, skip
                    continue;
                }
            }

            endDecorations.add(createCeremonialAltar("altar_" + dir.name().toLowerCase(), altarPos));
        }

        // Torch pillars - arranged in a hexagon around the portal
        int pillarRadius = Math.max(4, radius - 3);
        outer: for (int i = 0; i < 6; i++) {
            double angle = (2 * Math.PI * i) / 6;
            int x = (int) (portalCenter.getX() + pillarRadius * Math.cos(angle));
            int z = (int) (portalCenter.getZ() + pillarRadius * Math.sin(angle));
            Point3D pillarPos = new Point3D(x, portalCenter.getY() - 1, z);

            // Check if pillar would conflict with altars or connection
            for (DecorationElement decorationElement : endDecorations) {
                if (decorationElement.getId().contains("altar")
                        && decorationElement.getOccupiedPositions().contains(pillarPos)) {
                    continue outer;
                }
            }

            // Check distance from connection point
            if (connectionPoint != null && pillarPos.distance(connectionPoint) < 6) {
                continue;
            }

            endDecorations.add(createTorchPillar("pillar_" + i, pillarPos));
        }

        return endDecorations;
    }

    /**
     * Find which altar direction would be closest to the connection entrance
     */
    private Direction findClosestAltarDirection(Direction connectionDir) {
        // The altar that would be closest to the entrance is the one
        // in the same direction as the connection
        return connectionDir;
    }

    private DecorationElement createCeremonialAltar(String id, Point3D position) {
        DecorationElement altar = new DecorationElement(id, DecorationType.ALTAR, position, 3);

        // Create larger altar structure on platform
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Point3D basePos = new Point3D(position.getX() + x, position.getY(), position.getZ() + z);
                altar.addBlock(basePos, "chiseled_stone_bricks");

                if (x == 0 && z == 0) {
                    altar.addBlock(new Point3D(position.getX(), position.getY() + 1, position.getZ()),
                            "chiseled_stone_bricks");
                    altar.addBlock(new Point3D(position.getX(), position.getY() + 2, position.getZ()), "candle");
                }
            }
        }

        return altar;
    }

    private DecorationElement createTorchPillar(String id, Point3D position) {
        DecorationElement pillar = new DecorationElement(id, DecorationType.STONE_STRUCTURE, position, 1);

        // Pillar rises 4 blocks above platform
        for (int y = 0; y < 4; y++) {
            Point3D pillarBlock = new Point3D(position.getX(), position.getY() + y, position.getZ());
            if (y == 3) {
                pillar.addBlock(pillarBlock, "candle");
            } else {
                pillar.addBlock(pillarBlock, "stone_bricks");
            }
        }

        return pillar;
    }

    @Override
    public Point3D getConnectionPoint(Direction direction) {
        int radius = size / 2;
        return direction.apply(center, radius - 1);
    }

    @Override
    public RoomType getType() {
        return RoomType.END_PORTAL_ROOM;
    }

    public Point3D getPortalCenter() {
        return portalCenter;
    }

    public Set<Point3D> getPortalStructure() {
        return new HashSet<>(portalStructure);
    }

    public Set<Point3D> getCeremonyArea() {
        return new HashSet<>(ceremonyArea);
    }
}