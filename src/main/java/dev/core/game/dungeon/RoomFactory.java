package dev.core.game.dungeon;

import java.util.Random;

public class RoomFactory {

    public static DungeonRoom createRoom(RoomType type, String id, Point3D center, int size, int height) {
        switch (type) {
        case SQUARE_ROOM:
            return new QuadraticRoom(id, center, size, height);
        case L_SHAPED_ROOM:
            // Default orientation for explicitly created L-shaped rooms
            return new LShapedRoom(id, center, size, height, LShapedRoom.LOrientation.NORTH_EAST);
        case CIRCULAR_ROOM:
            return new RoundRoom(id, center, size, height);
        case CROSS_ROOM:
            return new QuadraticRoom(id, center, size, height); // Fallback for now
        case SPAWN_ROOM:
            return new SpawnRoom(id, center, size);
        case END_PORTAL_ROOM:
            return new EndPortalRoom(id, center, size);
        case TREASURE_ROOM:
            return new QuadraticRoom(id, center, size, height); // Fallback for now
        case BOSS_ROOM:
            return new QuadraticRoom(id, center, size, height); // Fallback for now
        default:
            return new QuadraticRoom(id, center, size, height);
        }
    }

    public static DungeonRoom createRandomRoom(String id, Point3D center, Random random, int minTunnelWidth) {
        return createRandomRoom(id, center, random, false, minTunnelWidth);
    }

    public static DungeonRoom createRandomRoom(String id, Point3D center, Random random, boolean favorSmaller,
            int minTunnelWidth) {
        // Only use standard room types for random generation (not special rooms)
        RoomType[] standardTypes = { RoomType.SQUARE_ROOM, RoomType.L_SHAPED_ROOM, RoomType.CIRCULAR_ROOM };
        RoomType type = standardTypes[random.nextInt(standardTypes.length)];

        // Calculate minimum room size based on tunnel width
        int minRoomSize = (int) Math.ceil(minTunnelWidth * random.nextDouble(1.5, 2.1));

        // Adjust size based on preference for smaller rooms when space is constrained
        int baseSize = favorSmaller ? Math.max(6, minRoomSize) : Math.max(8, minRoomSize);
        int sizeRange = favorSmaller ? 6 : 8;
        int size = baseSize + random.nextInt(sizeRange);

        if (type == RoomType.L_SHAPED_ROOM) {
            size = Math.max((int) (size * 3), minRoomSize); // L-shaped rooms need more space
            // Create L-shaped room with random orientation
            LShapedRoom.LOrientation orientation = getRandomOrientation(random);
            return new LShapedRoom(id, center, size, 5 + random.nextInt(favorSmaller ? 6 : 10), orientation);
        }

        int height = 5 + random.nextInt(favorSmaller ? 6 : 10); // Height between 5-10 or 5-14

        return createRoom(type, id, center, size, height);
    }

    public static DungeonRoom createRoomWithConnectionDirection(String id, Point3D center, Random random,
            Direction requiredDirection, boolean favorSmaller, int minTunnelWidth) {

        // Calculate minimum room size based on tunnel width
        int minRoomSize = (int) Math.ceil(minTunnelWidth * 1.5);

        // Adjust probabilities based on connection requirements
        // L-shaped rooms are less likely since they have limited connection options
        double lShapedProbability = 0.2; // Reduced from 1/3
        double circularProbability = 0.3;

        double roll = random.nextDouble();

        int baseSize = favorSmaller ? Math.max(6, minRoomSize) : Math.max(8, minRoomSize);
        int sizeRange = favorSmaller ? 6 : 8;
        int size = baseSize + random.nextInt(sizeRange);
        int height = 5 + random.nextInt(favorSmaller ? 6 : 10);

        if (roll < lShapedProbability) {
            // Create L-shaped room with compatible orientation
            size = Math.max((int) (size * 1.5), minRoomSize);
            return createLShapedRoomWithOrientation(id, center, size, height, requiredDirection, random);
        } else if (roll < lShapedProbability + circularProbability) {
            return new RoundRoom(id, center, size, height);
        } else {
            return new QuadraticRoom(id, center, size, height);
        }
    }

    public static DungeonRoom createRoomForDistance(String id, Point3D center, Random random, int maxTunnelDistance,
            int minTunnelWidth) {
        boolean favorSmaller = maxTunnelDistance <= 20; // Use smaller rooms for short tunnel distances
        return createRandomRoom(id, center, random, favorSmaller, minTunnelWidth);
    }

    /**
     * Create an L-shaped room with a specific orientation to work with existing
     * connections
     */
    public static LShapedRoom createLShapedRoomWithOrientation(String id, Point3D center, int size, int height,
            Direction requiredDirection, Random random) {
        // Find an orientation that includes the required direction as one of its arms
        LShapedRoom.LOrientation[] possibleOrientations = getPossibleOrientations(requiredDirection);

        if (possibleOrientations.length > 0) {
            // Randomly select from compatible orientations
            LShapedRoom.LOrientation selectedOrientation = possibleOrientations[random
                    .nextInt(possibleOrientations.length)];
            return new LShapedRoom(id, center, size, height, selectedOrientation);
        } else {
            // Fallback to default orientation if no match found
            return new LShapedRoom(id, center, size, height, LShapedRoom.LOrientation.NORTH_EAST);
        }
    }

    /**
     * Get possible orientations that have the required direction as one of their
     * arms
     */
    private static LShapedRoom.LOrientation[] getPossibleOrientations(Direction requiredDirection) {
        switch (requiredDirection) {
        case NORTH:
            return new LShapedRoom.LOrientation[] { LShapedRoom.LOrientation.NORTH_EAST,
                    LShapedRoom.LOrientation.NORTH_WEST };
        case SOUTH:
            return new LShapedRoom.LOrientation[] { LShapedRoom.LOrientation.SOUTH_EAST,
                    LShapedRoom.LOrientation.SOUTH_WEST };
        case EAST:
            return new LShapedRoom.LOrientation[] { LShapedRoom.LOrientation.NORTH_EAST,
                    LShapedRoom.LOrientation.SOUTH_EAST };
        case WEST:
            return new LShapedRoom.LOrientation[] { LShapedRoom.LOrientation.NORTH_WEST,
                    LShapedRoom.LOrientation.SOUTH_WEST };
        default:
            return new LShapedRoom.LOrientation[] {};
        }
    }

    /**
     * Get a random L-shaped room orientation
     */
    private static LShapedRoom.LOrientation getRandomOrientation(Random random) {
        LShapedRoom.LOrientation[] orientations = LShapedRoom.LOrientation.values();
        return orientations[random.nextInt(orientations.length)];
    }

    /**
     * Create a room that can connect in the specified direction Useful for dungeon
     * generation where we need guaranteed connection points
     */
    public static DungeonRoom createRoomWithConnectionDirection(String id, Point3D center, Random random,
            Direction requiredDirection, boolean favorSmaller) {
        // Adjust probabilities based on connection requirements
        // L-shaped rooms are less likely since they have limited connection options
        double lShapedProbability = 0.2; // Reduced from 1/3
        double circularProbability = 0.3;

        double roll = random.nextDouble();

        int baseSize = favorSmaller ? 6 : 8;
        int sizeRange = favorSmaller ? 6 : 8;
        int size = baseSize + random.nextInt(sizeRange);
        int height = 5 + random.nextInt(favorSmaller ? 6 : 10);

        if (roll < lShapedProbability) {
            // Create L-shaped room with compatible orientation
            size = (int) (size * 1.5);
            return createLShapedRoomWithOrientation(id, center, size, height, requiredDirection, random);
        } else if (roll < lShapedProbability + circularProbability) {
            return new RoundRoom(id, center, size, height);
        } else {
            return new QuadraticRoom(id, center, size, height);
        }
    }
}