package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dev.core.game.coords.Point3D;

public class DecorationGenerator {
    private final Random random;
    private int decorationCounter = 0;

    // Size thresholds for different decorations
    private static final int MIN_SIZE_FOR_DECORATIONS = 10;
    private static final int MIN_SIZE_FOR_LARGE_DECORATIONS = 16;
    private static final int MIN_SIZE_FOR_WATER_FEATURES = 12;
    private static final int MIN_SIZE_FOR_FOUNTAINS = 10;
    private static final int MIN_SIZE_FOR_PRISON_CELLS = 50; // Ignore them for the time being

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

        // Always available with higher weight for vines and lichen
        for (int i = 0; i < 3; i++) { // 3x weight
            availableTypes.add(DecorationType.WALL_VINES);
            availableTypes.add(DecorationType.GLOWING_LICHEN);
        }

        availableTypes.add(DecorationType.WALL_VEGETATION);
        availableTypes.add(DecorationType.FLOOR_VEGETATION);
        availableTypes.add(DecorationType.COBWEB_CLUSTER);

        // Size-dependent decorations
        if (room.getSize() >= MIN_SIZE_FOR_WATER_FEATURES) {
            // Remove single water features - only fountains now
            // availableTypes.add(DecorationType.WATER_FEATURE);
        }

        if (room.getSize() >= MIN_SIZE_FOR_FOUNTAINS) {
            availableTypes.add(DecorationType.FOUNTAIN);
        }

        if (room.getSize() >= MIN_SIZE_FOR_PRISON_CELLS) {
            availableTypes.add(DecorationType.PRISON_CELL);
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

            if (type == DecorationType.WALL_VEGETATION || type == DecorationType.COBWEB_CLUSTER
                    || type == DecorationType.WALL_VINES || type == DecorationType.GLOWING_LICHEN) {
                // Find wall positions
                candidate = findWallPosition(room, usedPositions);
            } else if (type == DecorationType.PRISON_CELL) {
                // Find corner positions for prison cells
                candidate = findCornerPosition(room, usedPositions);
            } else if (type == DecorationType.FOUNTAIN) {
                candidate = findCenterPosition(room);
            } else {
                // Find floor positions for other decorations
                candidate = findFloorPosition(room, usedPositions);
            }

            if (candidate != null && !usedPositions.contains(candidate)
                    && roomCenter.distance(candidate) < room.getSize() / 2) {
                return candidate;
            }
        }

        return null;
    }

    private Point3D findCenterPosition(DungeonRoom room) {
        Point3D center = room.getCenter();

        for (int x = -3; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = -3; z < 4; z++) {
                    Point3D point3d = new Point3D(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!room.getAirBlocks().contains(point3d)) {
                        return null;
                    }
                }
            }
        }

        return center;
    }

    private Point3D findWallPosition(DungeonRoom room, Set<Point3D> usedPositions) {
        // Find positions adjacent to walls
        Set<Point3D> floorBlocks = room.getFloorBlocks();
        List<Point3D> wallAdjacentPositions = new ArrayList<>();

        for (Point3D floor : floorBlocks) {
            Point3D airPos = new Point3D(floor.getX(), floor.getY() + 1, floor.getZ());

            // Check if this position is adjacent to a wall
            outer: for (Direction dir : Direction.values()) {
                Point3D adjacent = dir.apply(airPos, 1);
                if (room.getWallBlocks().contains(adjacent)) {
                    // Check if location is NOT inside wall
                    if (room.getWallBlocks().contains(airPos)) {
                        break outer;
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

    private Point3D findCornerPosition(DungeonRoom room, Set<Point3D> usedPositions) {
        // Find corner positions for prison cells - fixed algorithm
        Set<Point3D> floorBlocks = room.getFloorBlocks();
        List<Point3D> cornerPositions = new ArrayList<>();

        for (Point3D floor : floorBlocks) {
            Point3D airPos = new Point3D(floor.getX(), floor.getY() + 1, floor.getZ());

            // Check if this floor position is in a corner by examining adjacent walls
            boolean hasNorthWall = room.getWallBlocks().contains(Direction.NORTH.apply(airPos, 1));
            boolean hasSouthWall = room.getWallBlocks().contains(Direction.SOUTH.apply(airPos, 1));
            boolean hasEastWall = room.getWallBlocks().contains(Direction.EAST.apply(airPos, 1));
            boolean hasWestWall = room.getWallBlocks().contains(Direction.WEST.apply(airPos, 1));

            // Count adjacent walls - corners should have at least 2 adjacent walls
            int adjacentWalls = 0;
            if (hasNorthWall)
                adjacentWalls++;
            if (hasSouthWall)
                adjacentWalls++;
            if (hasEastWall)
                adjacentWalls++;
            if (hasWestWall)
                adjacentWalls++;

            // Also check for actual corner configurations
            boolean isCorner = (hasNorthWall && hasEastWall) || (hasNorthWall && hasWestWall)
                    || (hasSouthWall && hasEastWall) || (hasSouthWall && hasWestWall);

            if ((adjacentWalls >= 2 || isCorner) && !usedPositions.contains(airPos)) {
                cornerPositions.add(airPos);
            }
        }

        if (cornerPositions.isEmpty())
            return findWallPosition(room, usedPositions); // Fallback to wall position
        return cornerPositions.get(random.nextInt(cornerPositions.size()));
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

            if (distanceFromCenter >= 3 && !usedPositions.contains(airPos) && room.getAirBlocks().contains(airPos)) {
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
        case FOUNTAIN:
            return createFountain(id, position);
        case PRISON_CELL:
            return createPrisonCell(id, position);
        case WALL_VINES:
            return createWallVines(id, position);
        case GLOWING_LICHEN:
            return createGlowingLichen(id, position);
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
        // This method is now unused since we removed WATER_FEATURE from selection
        // Keep for compatibility but create a contained water feature
        DecorationElement decoration = new DecorationElement(id, DecorationType.WATER_FEATURE, position, 3);

        // Create a proper 3x3 contained pool instead of single water block
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Point3D poolPos = new Point3D(position.getX() + x, position.getY() - 1, position.getZ() + z);
                if (x == 0 && z == 0) {
                    // Center - water
                    decoration.addBlock(poolPos, "water");
                } else {
                    // Border - stone
                    decoration.addBlock(poolPos, "stone_bricks");
                }
            }
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

    private DecorationElement createFountain(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.FOUNTAIN, position, 4);

        // Create fountain base with proper containment (5x5 base)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Point3D basePos = new Point3D(position.getX() + x, position.getY() - 1, position.getZ() + z);

                // Create a proper basin with walls
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    // Outer rim - raised walls
                    decoration.addBlock(basePos, "stone_bricks");
                    decoration.addBlock(new Point3D(basePos.getX(), basePos.getY() + 1, basePos.getZ()),
                            "stone_bricks");
                } else {
                    // Inner area - basin floor
                    decoration.addBlock(basePos, "stone_bricks");

                    // Add water only in the inner 3x3 area
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                        decoration.addBlock(new Point3D(basePos.getX(), basePos.getY() + 1, basePos.getZ()), "water");
                    }
                }
            }
        }

        // Create central fountain pillar
        decoration.addBlock(position, "stone_bricks");
        decoration.addBlock(new Point3D(position.getX(), position.getY() + 1, position.getZ()), "stone_bricks");

        // Add water source at top of pillar
        decoration.addBlock(new Point3D(position.getX(), position.getY() + 2, position.getZ()), "water");

        // Add decorative corner posts
        for (int x = -2; x <= 2; x += 4) {
            for (int z = -2; z <= 2; z += 4) {
                Point3D cornerPost = new Point3D(position.getX() + x, position.getY() + 1, position.getZ() + z);
                decoration.addBlock(cornerPost, "chiseled_stone_bricks");
            }
        }

        return decoration;
    }

    private DecorationElement createPrisonCell(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.PRISON_CELL, position, 3);

        // Create iron bars wall (3x3 area)
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                Point3D barPos = new Point3D(position.getX() + x, position.getY() + y, position.getZ());
                decoration.addBlock(barPos, "iron_bars");
            }
        }

        // Add side walls to create actual cell enclosure
        for (int y = 0; y < 3; y++) {
            // Left wall
            decoration.addBlock(new Point3D(position.getX() - 1, position.getY() + y, position.getZ()), "cobblestone");
            decoration.addBlock(new Point3D(position.getX() - 1, position.getY() + y, position.getZ() + 1),
                    "cobblestone");
            decoration.addBlock(new Point3D(position.getX() - 1, position.getY() + y, position.getZ() + 2),
                    "cobblestone");

            // Back wall
            decoration.addBlock(new Point3D(position.getX(), position.getY() + y, position.getZ() + 3), "cobblestone");
            decoration.addBlock(new Point3D(position.getX() + 1, position.getY() + y, position.getZ() + 3),
                    "cobblestone");
            decoration.addBlock(new Point3D(position.getX() + 2, position.getY() + y, position.getZ() + 3),
                    "cobblestone");
        }

        // Add chains hanging from ceiling inside the cell
        Point3D chainTop = new Point3D(position.getX() + 1, position.getY() + 2, position.getZ() + 1);
        decoration.addBlock(chainTop, "chain");
        decoration.addBlock(new Point3D(chainTop.getX(), chainTop.getY() - 1, chainTop.getZ()), "chain");

        // Add another chain
        Point3D chainTop2 = new Point3D(position.getX() + 1, position.getY() + 2, position.getZ() + 2);
        decoration.addBlock(chainTop2, "chain");

        // Add some worn blocks inside the cell floor
        decoration.addBlock(new Point3D(position.getX() + 1, position.getY() - 1, position.getZ() + 1),
                "mossy_cobblestone");
        decoration.addBlock(new Point3D(position.getX() + 2, position.getY() - 1, position.getZ() + 2),
                "mossy_cobblestone");

        return decoration;
    }

    private DecorationElement createWallVines(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.WALL_VINES, position, 3);

        // Create much denser vine coverage
        decoration.addBlock(position, "vine");

        // Add vines vertically - more aggressive spreading
        Point3D above = new Point3D(position.getX(), position.getY() + 1, position.getZ());
        decoration.addBlock(above, "vine");

        if (random.nextDouble() < 0.7) { // Increased from 0.4
            Point3D above2 = new Point3D(position.getX(), position.getY() + 2, position.getZ());
            decoration.addBlock(above2, "vine");
        }

        // Add much more horizontal spread - increased density
        for (Direction dir : Direction.values()) {
            if (random.nextDouble() < 0.6) { // Increased from 0.3
                Point3D spreadPos = dir.apply(position, 1);
                decoration.addBlock(spreadPos, "vine");

                // Secondary spread
                if (random.nextDouble() < 0.4) {
                    Point3D spreadPos2 = new Point3D(spreadPos.getX(), spreadPos.getY() + 1, spreadPos.getZ());
                    decoration.addBlock(spreadPos2, "vine");
                }
            }
        }

        return decoration;
    }

    private DecorationElement createGlowingLichen(String id, Point3D position) {
        DecorationElement decoration = new DecorationElement(id, DecorationType.GLOWING_LICHEN, position, 3);

        // Place glow lichen on wall
        decoration.addBlock(position, "glow_lichen");

        // Much more aggressive spreading to adjacent positions
        for (Direction dir : Direction.values()) {
            if (random.nextDouble() < 0.7) { // Increased from 0.4
                Point3D spreadPos = dir.apply(position, 1);
                decoration.addBlock(spreadPos, "glow_lichen");

                // Secondary spread
                if (random.nextDouble() < 0.5) {
                    Point3D spreadPos2 = dir.apply(spreadPos, 1);
                    decoration.addBlock(spreadPos2, "glow_lichen");
                }
            }
        }

        // Add vertical spreading - more aggressive
        if (random.nextDouble() < 0.6) { // Increased from 0.3
            Point3D above = new Point3D(position.getX(), position.getY() + 1, position.getZ());
            decoration.addBlock(above, "glow_lichen");
        }

        if (random.nextDouble() < 0.6) {
            Point3D below = new Point3D(position.getX(), position.getY() - 1, position.getZ());
            decoration.addBlock(below, "glow_lichen");
        }

        return decoration;
    }
}