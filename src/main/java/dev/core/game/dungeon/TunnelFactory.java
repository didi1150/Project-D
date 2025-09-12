//package dev.core.game.dungeon;
//
//import java.util.Random;
//
//public class TunnelFactory {
//
//    public static DungeonTunnel createTunnel(TunnelType type, String id, DungeonRoom startRoom, DungeonRoom endRoom,
//            Direction direction, int width) {
//
//        // Calculate connection points with special handling for L-shaped rooms
//        Point3D startPoint = calculateOptimalConnection(startRoom, direction);
//        Point3D endPoint = calculateOptimalConnection(endRoom, direction.opposite());
//
//        // Normalize Y to the lower of the two centers
//        int connectionY = Math.min(startRoom.getCenter().getY(), endRoom.getCenter().getY());
//        startPoint = new Point3D(startPoint.getX(), connectionY, startPoint.getZ());
//        endPoint = new Point3D(endPoint.getX(), connectionY, endPoint.getZ());
//
//        int tunnelHeight = Math.min(startRoom.getHeight(), endRoom.getHeight());
//
//        TunnelConnection startConnection = new TunnelConnection(startRoom, direction, startPoint, tunnelHeight);
//        TunnelConnection endConnection = new TunnelConnection(endRoom, direction.opposite(), endPoint, tunnelHeight);
//
//        // Auto-pick L_SHAPED if not aligned on either axis
//        if (startPoint.getX() != endPoint.getX() && startPoint.getZ() != endPoint.getZ()) {
//            type = TunnelType.L_SHAPED;
//        }
//
//        switch (type) {
//        case STRAIGHT:
//            return new StraightTunnel(id, startConnection, endConnection, width);
//        case L_SHAPED:
//            return new LShapedTunnel(id, startConnection, endConnection, width);
//        default:
//            return new StraightTunnel(id, startConnection, endConnection, width);
//        }
//    }
//
//    public static DungeonTunnel createRandomTunnel(String id, DungeonRoom startRoom, DungeonRoom endRoom,
//            Direction direction, Random random) {
//        TunnelType type = TunnelType.values()[random.nextInt(TunnelType.values().length)];
//        int width = 3 + random.nextInt(3); // Width between 3–5
//        return createTunnel(type, id, startRoom, endRoom, direction, width);
//    }
//
//    /**
//     * Calculate optimal connection point with special handling for L-shaped rooms
//     */
//    private static Point3D calculateOptimalConnection(DungeonRoom room, Direction direction) {
//        // For L-shaped rooms, try to use the alternative connection point if the
//        // primary isn't valid
//        if (room instanceof LShapedRoom) {
//            LShapedRoom lRoom = (LShapedRoom) room;
//
//            // First try the primary connection point
//            Point3D primaryPoint = lRoom.getConnectionPoint(direction);
//            if (lRoom.hasValidConnectionPoint(direction)) {
//                return primaryPoint;
//            }
//
//            // If primary isn't valid, try alternative
//            Point3D alternativePoint = lRoom.getAlternativeConnectionPoint(direction);
//            return alternativePoint;
//        }
//
//        // For other room types, use the standard midpoint calculation
//        return calculateMidpointConnection(room, direction);
//    }
//
//    private static Point3D calculateMidpointConnection(DungeonRoom room, Direction direction) {
//        Point3D center = room.getCenter();
//        int halfSize = room.getSize() / 2;
//
//        switch (direction) {
//        case NORTH:
//            return new Point3D(center.getX(), center.getY(), center.getZ() - halfSize);
//        case SOUTH:
//            return new Point3D(center.getX(), center.getY(), center.getZ() + halfSize);
//        case EAST:
//            return new Point3D(center.getX() + halfSize, center.getY(), center.getZ());
//        case WEST:
//            return new Point3D(center.getX() - halfSize, center.getY(), center.getZ());
//        default:
//            return center;
//        }
//    }
//}

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
        TunnelType type = TunnelType.values()[random.nextInt(TunnelType.values().length - 1)];
        int width = 3 + random.nextInt(3); // Width between 3-5

        return createTunnel(type, id, startRoom, endRoom, direction, width);
    }

    private static Point3D calculateOptimalConnectionPoint(DungeonRoom room, Direction direction) {
        Point3D basePoint = room.getConnectionPoint(direction);

// For L-shaped and round rooms, we need to find a better connection point
        if (room.getType() == RoomType.L_SHAPED_ROOM || room.getType() == RoomType.CIRCULAR_ROOM) {
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
