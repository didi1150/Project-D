package dev.core.game.dungeon;

import java.util.Random;

public class TunnelFactory {

    public static DungeonTunnel createTunnel(TunnelType type, String id, DungeonRoom startRoom, DungeonRoom endRoom,
            Direction direction, int width) {

// Calculate proper connection points
        Point3D startPoint = calculateOptimalConnectionPoint(startRoom, direction);
        Point3D endPoint = calculateOptimalConnectionPoint(endRoom, direction.opposite());

// Ensure connection points are at the same Y level
        int connectionY = Math.min(startRoom.getCenter().getY(), endRoom.getCenter().getY());
        startPoint = new Point3D(startPoint.getX(), connectionY, startPoint.getZ());
        endPoint = new Point3D(endPoint.getX(), connectionY, endPoint.getZ());

        int tunnelHeight = Math.min(startRoom.getHeight(), endRoom.getHeight());

        TunnelConnection startConnection = new TunnelConnection(startRoom, direction, startPoint, tunnelHeight);
        TunnelConnection endConnection = new TunnelConnection(endRoom, direction.opposite(), endPoint, tunnelHeight);

        switch (type) {
        case STRAIGHT:
            return new StraightTunnel(id, startConnection, endConnection, width);
// Add more tunnel types here as needed
        default:
            return new StraightTunnel(id, startConnection, endConnection, width);
        }
    }

    public static DungeonTunnel createRandomTunnel(String id, DungeonRoom startRoom, DungeonRoom endRoom,
            Direction direction, Random random) {
        TunnelType type = TunnelType.STRAIGHT; // For now, only straight tunnels
        int width = 3 + random.nextInt(3); // Width between 3-5

        return createTunnel(type, id, startRoom, endRoom, direction, width);
    }

    private static Point3D calculateOptimalConnectionPoint(DungeonRoom room, Direction direction) {
        Point3D basePoint = room.getConnectionPoint(direction);

// For L-shaped and round rooms, we need to find a better connection point
        if (room.getType() == RoomType.L_SHAPE || room.getType() == RoomType.ROUND) {
            return findValidConnectionPoint(room, direction, basePoint);
        }

        return basePoint;
    }

    private static Point3D findValidConnectionPoint(DungeonRoom room, Direction direction, Point3D defaultPoint) {
// Search for a valid connection point that actually has floor beneath it
        int searchRadius = 2;
        Point3D roomCenter = room.getCenter();

        for (int offset = 0; offset <= searchRadius; offset++) {
            for (int side = -1; side <= 1; side += 2) {
                Point3D candidate = calculateCandidatePoint(defaultPoint, direction, offset * side);
                Point3D floorCheck = new Point3D(candidate.getX(), roomCenter.getY() - 1, candidate.getZ());

                if (room.getFloorBlocks().contains(floorCheck)) {
// Ensure there's also room interior space behind this point
                    Point3D interiorCheck = direction.opposite().apply(candidate, 1);
                    Point3D interiorFloor = new Point3D(interiorCheck.getX(), roomCenter.getY() - 1,
                            interiorCheck.getZ());

                    if (room.getFloorBlocks().contains(interiorFloor)) {
                        return candidate;
                    }
                }
            }
        }

        return defaultPoint; // Fallback to original point
    }

    private static Point3D calculateCandidatePoint(Point3D basePoint, Direction direction, int offset) {
        Direction[] perpendiculars = getPerpendicularDirections(direction);
        return perpendiculars[0].apply(basePoint, offset);
    }

    private static Direction[] getPerpendicularDirections(Direction dir) {
        switch (dir) {
        case NORTH:
        case SOUTH:
            return new Direction[] { Direction.EAST, Direction.WEST };
        case EAST:
        case WEST:
            return new Direction[] { Direction.NORTH, Direction.SOUTH };
        default:
            return new Direction[] { Direction.EAST, Direction.WEST };
        }
    }

}
