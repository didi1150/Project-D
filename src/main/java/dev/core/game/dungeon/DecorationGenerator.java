package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class DecorationGenerator {
    private final Random random;
    private int decorationCounter = 0;

    // Size thresholds for different decorations
    private static final int MIN_SIZE_FOR_DECORATIONS = 10;
    private static final int MIN_SIZE_FOR_LARGE_DECORATIONS = 16;
    private static final int MIN_SIZE_FOR_WATER_FEATURES = 12;

    public DecorationGenerator(Random random) {
        this.random = random;
    }

    public List<DecorationElement> generateDecorations(DungeonRoom room) {
        List<DecorationElement> decorations = new ArrayList<>();

        if (room.getSize() < MIN_SIZE_FOR_DECORATIONS) {
            return decorations; // Too small for decorations
        }

        int decorationCount = calculateDecorationCount(room.getSize());
        Set<Point3D> usedPositions = new HashSet<>();

        for (int i = 0; i < decorationCount; i++) {
            DecorationType type = selectDecorationTypeForRoom(room);
            Point3D position = findSuitableDecorationPosition(room, type, usedPositions);

            if (position != null) {
                DecorationElement decoration = createDecoration(type, position, room);
                if (decoration != null) {
                    decorations.add(decoration);
                    usedPositions.addAll(decoration.getOccupiedPositions());

                    // Add buffer space around decoration
                    for (Point3D occupied : decoration.getOccupiedPositions()) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                usedPositions
                                        .add(new Point3D(occupied.getX() + x, occupied.getY(), occupied.getZ() + z));
                            }
                        }
                    }
                }
            }
        }

        return decorations;
    }

    private int calculateDecorationCount(int roomSize) {
        if (roomSize < MIN_SIZE_FOR_DECORATIONS)
            return 0;
        if (roomSize < 14)
            return 1 + random.nextInt(2); // 1-2 decorations
        if (roomSize < MIN_SIZE_FOR_LARGE_DECORATIONS)
            return 2 + random.nextInt(3); // 2-4 decorations
        return 3 + random.nextInt(4); // 3-6 decorations for large rooms
    }

    private DecorationType selectDecorationTypeForRoom(DungeonRoom room) {
        List<DecorationType> availableTypes = new ArrayList<>();

        // Always available
        availableTypes.add(DecorationType.WALL_VEGETATION);
        availableTypes.add(DecorationType.FLOOR_VEGETATION);
        availableTypes.add(DecorationType.COBWEB_CLUSTER);

        // Size-dependent decorations
        if (room.getSize() >= MIN_SIZE_FOR_WATER_FEATURES) {
            availableTypes.add(DecorationType.WATER_FEATURE);
        }

        if (room.getSize() >= 12) {
            availableTypes.add(DecorationType.STONE_STRUCTURE);
            availableTypes.add(DecorationType.BARREL_GROUP);
        }

        if (room.getSize() >= MIN_SIZE_FOR_LARGE_DECORATIONS) {
            availableTypes.add(DecorationType.ALTAR);
            availableTypes.add(DecorationType.BOOKSHELF_AREA);
        }

        return availableTypes.get(random.nextInt(availableTypes.size()));
    }

    private Point3D findSuitableDecorationPosition(DungeonRoom room, DecorationType type, Set<Point3D> usedPositions) {
        Point3D roomCenter = room.getCenter();
        int attempts = 50;

        for (int i = 0; i < attempts; i++) {
            Point3D candidate;

            if (type == DecorationType.WALL_VEGETATION || type == DecorationType.COBWEB_CLUSTER) {
                // Find wall positions
                candidate = findWallPosition(room, usedPositions);
            } else {
                // Find floor positions for other decorations
                candidate = findFloorPosition(room, usedPositions);
            }

            if (candidate != null && !usedPositions.contains(candidate)
                    && roomCenter.distance(candidate) < room.getSize() / 2 - 2) {
                return candidate;
            }
        }

        return null;
    }

    private Point3D findWallPosition(DungeonRoom room, Set<Point3D> usedPositions) {
        // Find positions adjacent to walls
        Set<Point3D> floorBlocks = room.getFloorBlocks();
        List<Point3D> wallAdjacentPositions = new ArrayList<>();

        for (Point3D floor : floorBlocks) {
            Point3D airPos = new Point3D(floor.getX(), floor.getY() + 1, floor.getZ());

            // Check if this position is adjacent to a wall
            for (Direction dir : Direction.values()) {
                Point3D adjacent = dir.apply(airPos, 1);
                if (room.getWallBlocks().contains(adjacent)) {
                    // Check if location is NOT inside wall
                    if (room.getWallBlocks().contains(airPos)) {
                        break;
                    }
                    wallAdjacentPositions.add(airPos);
                    break;
                }
            }
        }

        if (wallAdjacentPositions.isEmpty())
            return null;
        return wallAdjacentPositions.get(random.nextInt(wallAdjacentPositions.size()));
    }

    private Point3D findFloorPosition(DungeonRoom room, Set<Point3D> usedPositions) {
        Set<Point3D> floorBlocks = room.getFloorBlocks();
        Point3D roomCenter = room.getCenter();
        List<Point3D> suitablePositions = new ArrayList<>();

        for (Point3D floor : floorBlocks) {
            Point3D airPos = new Point3D(floor.getX(), floor.getY() + 1, floor.getZ());

            // Avoid positions too close to room center (leave space for movement)
            double distanceFromCenter = Math.sqrt(
                    Math.pow(floor.getX() - roomCenter.getX(), 2) + Math.pow(floor.getZ() - roomCenter.getZ(), 2));

            if (distanceFromCenter >= 3 && !usedPositions.contains(airPos)) {
                suitablePositions.add(airPos);
            }
        }

        if (suitablePositions.isEmpty())
            return null;
        return suitablePositions.get(random.nextInt(suitablePositions.size()));
    }

    private DecorationElement createDecoration(DecorationType type, Point3D position, DungeonRoom room) {
        String id = "decoration_" + (++decorationCounter);

        switch (type) {
        case WALL_VEGETATION:
            return createWallVegetation(id, position);
        case FLOOR_VEGETATION:
            return createFloorVegetation(id, position);
        case WATER_FEATURE:
            return createWaterFeature(id, position, room);
        case STONE_STRUCTURE:
            return createStoneStructure(id, position);
        case ALTAR:
            return createAltar(id, position);
        case BARREL_GROUP:
            return createBarrelGroup(id, position);
        case BOOKSHELF_AREA:
            return createBookshelfArea(id, position);
        case COBWEB_CLUSTER:
            return createCobwebCluster(id, position);
        default:
            return null;
        }
    }

    private DecorationElement createWallVegetation(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.WALL_VEGETATION, position, 1);

        // Add vines or leaves
        decoration.addBlock(position, random.nextBoolean() ? "vine" : "oak_leaves");

        // Sometimes add additional blocks vertically
        if (random.nextDouble() < 0.3) {
            Point3D above = new Point3D(position.getX(), position.getY() + 1, position.getZ());
            decoration.addBlock(above, "oak_leaves");
        }

        return decoration;
    }

    private DecorationElement createFloorVegetation(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.FLOOR_VEGETATION, position, 1);

        String[] vegetation = { "grass", "fern", "dead_bush", "brown_mushroom", "red_mushroom" };
        decoration.addBlock(position, vegetation[random.nextInt(vegetation.length)]);

        return decoration;
    }

    private DecorationElement createWaterFeature(String id, Point3D position, DungeonRoom room) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.WATER_FEATURE, position, 2);

        // Create a small pool by adding a lower floor level
        Point3D poolBottom = new Point3D(position.getX(), position.getY() - 2, position.getZ());
        decoration.addBlock(poolBottom, "stone_bricks"); // Pool bottom

        // Add water
        Point3D waterPos = new Point3D(position.getX(), position.getY() - 1, position.getZ());
        decoration.addBlock(waterPos, "water");

        // Add surrounding stone rim
        for (Direction dir : Direction.values()) {
            Point3D rimPos = dir.apply(position, 1);
            Point3D rimBottom = new Point3D(rimPos.getX(), position.getY() - 1, rimPos.getZ());
            decoration.addBlock(rimBottom, "stone_bricks");
        }

        return decoration;
    }

    private DecorationElement createStoneStructure(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.STONE_STRUCTURE, position, 1);

        // Create a small pillar or monument
        decoration.addBlock(position, "cobblestone");
        decoration.addBlock(new Point3D(position.getX(), position.getY() + 1, position.getZ()), "cobblestone");

        if (random.nextDouble() < 0.5) {
            decoration.addBlock(new Point3D(position.getX(), position.getY() + 2, position.getZ()), "stone_bricks");
        }

        return decoration;
    }

    private DecorationElement createAltar(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.ALTAR, position, 3);

        // Create altar base
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Point3D basePos = new Point3D(position.getX() + x, position.getY(), position.getZ() + z);
                decoration.addBlock(basePos, "stone_bricks");
            }
        }

        // Add altar top
        decoration.addBlock(new Point3D(position.getX(), position.getY() + 1, position.getZ()),
                "chiseled_stone_bricks");

        // Add decorative elements
        decoration.addBlock(new Point3D(position.getX() - 1, position.getY() + 1, position.getZ()), "candle");
        decoration.addBlock(new Point3D(position.getX() + 1, position.getY() + 1, position.getZ()), "candle");

        return decoration;
    }

    private DecorationElement createBarrelGroup(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.BARREL_GROUP, position, 2);

        decoration.addBlock(position, "barrel");
        decoration.addBlock(new Point3D(position.getX() + 1, position.getY(), position.getZ()), "barrel");

        if (random.nextDouble() < 0.5) {
            decoration.addBlock(new Point3D(position.getX(), position.getY(), position.getZ() + 1), "barrel");
        }

        return decoration;
    }

    private DecorationElement createBookshelfArea(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.BOOKSHELF_AREA, position, 2);

        decoration.addBlock(position, "bookshelf");
        decoration.addBlock(new Point3D(position.getX(), position.getY() + 1, position.getZ()), "bookshelf");
        decoration.addBlock(new Point3D(position.getX() + 1, position.getY(), position.getZ()), "bookshelf");

        // Add reading table
        decoration.addBlock(new Point3D(position.getX() - 1, position.getY(), position.getZ()), "oak_planks");

        return decoration;
    }

    private DecorationElement createCobwebCluster(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.COBWEB_CLUSTER, position, 1);

        decoration.addBlock(position, "cobweb");

        // Add nearby cobwebs
        for (Direction dir : Direction.values()) {
            if (random.nextDouble() < 0.3) {
                Point3D nearbyPos = dir.apply(position, 1);
                decoration.addBlock(nearbyPos, "cobweb");
            }
        }

        return decoration;
    }
}
