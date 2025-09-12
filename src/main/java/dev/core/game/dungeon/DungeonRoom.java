package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public abstract class DungeonRoom {

    protected final String id;
    protected final Point3D center;
    protected final int size;
    protected final int height;
    protected final Map<Direction, DungeonRoom> connections;
    protected final Set<Point3D> floorBlocks;
    protected final Set<Point3D> wallBlocks;
    protected final Set<Point3D> roofBlocks;
    protected final Set<Point3D> airBlocks;

    // New features
    protected final List<SpawnLocation> spawnLocations;
    protected final List<DecorationElement> decorations;
    protected final Set<Point3D> decorativeBlocks; // Additional blocks for decorations

    public DungeonRoom(String id, Point3D center, int size, int height) {
        this.id = id;
        this.center = center;
        this.size = size;
        this.height = height;
        this.connections = new EnumMap<>(Direction.class);
        this.floorBlocks = new HashSet<>();
        this.wallBlocks = new HashSet<>();
        this.roofBlocks = new HashSet<>();
        this.airBlocks = new HashSet<>();
        this.spawnLocations = new ArrayList<>();
        this.decorations = new ArrayList<>();
        this.decorativeBlocks = new HashSet<>();
        generateBlocks();
    }

    // Abstract methods that each room type must implement
    protected abstract void generateRoomStructure();

    public abstract Point3D getConnectionPoint(Direction direction);

    public abstract RoomType getType();

    private void generateBlocks() {
        generateRoomStructure();
        generateRoof();
        generateSpawnLocationsAndDecorations();
    }

    private void generateSpawnLocationsAndDecorations() {
        Random roomRandom = new Random(id.hashCode()); // Consistent generation based on room ID

        // Generate spawn locations
        SpawnLocationGenerator spawnGenerator = new SpawnLocationGenerator(roomRandom);
        spawnLocations.addAll(spawnGenerator.generateSpawnLocations(this));

        // Generate decorations
        DecorationGenerator decorationGenerator = new DecorationGenerator(roomRandom);
        decorations.addAll(decorationGenerator.generateDecorations(this));

        // Add decoration blocks to the decorative blocks set
        for (DecorationElement decoration : decorations) {
            decorativeBlocks.addAll(decoration.getOccupiedPositions());
        }
    }

    protected void addRoomBlocks(int x, int z) {
        // Floor
        floorBlocks.add(new Point3D(x, center.getY() - 1, z));

        // Air inside (based on room height)
        for (int y = center.getY(); y < center.getY() + height; y++) {
            airBlocks.add(new Point3D(x, y, z));
        }
    }

    protected void addWallsForBlocks() {
        Set<Point3D> borderBlocks = new HashSet<>();

        for (Point3D floor : floorBlocks) {
            // Check all 4 directions for border detection
            for (Direction dir : Direction.values()) {
                Point3D adjacent = dir.apply(new Point3D(floor.getX(), floor.getY(), floor.getZ()), 1);
                if (!floorBlocks.contains(adjacent)) {
                    // This is a border position
                    for (int y = floor.getY() + 1; y < floor.getY() + height + 1; y++) {
                        Point3D wallPos = new Point3D(floor.getX(), y, floor.getZ());
                        borderBlocks.add(wallPos);
                    }
                }
            }
        }

        wallBlocks.addAll(borderBlocks);
        airBlocks.removeAll(borderBlocks);
    }

    private void generateRoof() {
        // Generate roof blocks for all floor positions
        for (Point3D floor : floorBlocks) {
            Point3D roofPos = new Point3D(floor.getX(), center.getY() + height, floor.getZ());
            roofBlocks.add(roofPos);
        }
    }

    public void addConnection(DungeonRoom other, Direction direction) {
        this.connections.put(direction, other);
        other.connections.put(direction.opposite(), this);
    }

    // Getters
    public String getId() {
        return id;
    }

    public Point3D getCenter() {
        return center;
    }

    public int getSize() {
        return size;
    }

    public int getHeight() {
        return height;
    }

    public Map<Direction, DungeonRoom> getConnections() {
        return new EnumMap<>(connections);
    }

    public Set<Point3D> getFloorBlocks() {
        return floorBlocks;
    }

    public Set<Point3D> getWallBlocks() {
        return wallBlocks;
    }

    public Set<Point3D> getRoofBlocks() {
        return new HashSet<>(roofBlocks);
    }

    public Set<Point3D> getAirBlocks() {
        return airBlocks;
    }

    // New getters for spawn locations and decorations
    public List<SpawnLocation> getSpawnLocations() {
        return new ArrayList<>(spawnLocations);
    }

    public List<DecorationElement> getDecorations() {
        return new ArrayList<>(decorations);
    }

    public Set<Point3D> getDecorativeBlocks() {
        return new HashSet<>(decorativeBlocks);
    }

    // Utility methods for spawn management
    public List<SpawnLocation> getSpawnLocationsByTier(SpawnTier tier) {
        return spawnLocations.stream().filter(spawn -> spawn.getTier() == tier)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<SpawnLocation> getEliteSpawnLocations() {
        return spawnLocations.stream().filter(SpawnLocation::isEliteSpawn)
                .collect(java.util.stream.Collectors.toList());
    }

    // Utility methods for decorations
    public List<DecorationElement> getDecorationsByType(DecorationType type) {
        return decorations.stream().filter(decoration -> decoration.getType() == type)
                .collect(java.util.stream.Collectors.toList());
    }
}
