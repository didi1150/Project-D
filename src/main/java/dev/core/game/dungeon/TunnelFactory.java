package dev.core.game.dungeon;

import java.util.Random;

import dev.core.game.coords.Point3D;

public class TunnelFactory {

    public static DungeonTunnel createTunnel(TunnelType type, String id, DungeonRoom startRoom, DungeonRoom endRoom,
            Direction direction, int width, int maxTunnelDistance) {

        // Ensure connection points are exactly aligned
        Point3D startPoint = startRoom.getConnectionPoint(direction);
        Point3D endPoint = endRoom.getConnectionPoint(direction.opposite());

        // Force same Y level
        int connectionY = Math.min(startRoom.getCenter().getY(), endRoom.getCenter().getY());
        startPoint = new Point3D(startPoint.getX(), connectionY, startPoint.getZ());
        endPoint = new Point3D(endPoint.getX(), connectionY, endPoint.getZ());

        int tunnelHeight = Math.min(startRoom.getHeight(), endRoom.getHeight());

        TunnelConnection startConnection = new TunnelConnection(startRoom, direction, startPoint, tunnelHeight);
        TunnelConnection endConnection = new TunnelConnection(endRoom, direction.opposite(), endPoint, tunnelHeight);

        // Auto-select tunnel type based on alignment and distance
        TunnelType selectedType = selectOptimalTunnelType(startPoint, endPoint, type, maxTunnelDistance);

        switch (selectedType) {
        case STRAIGHT:
            return new StraightTunnel(id, startConnection, endConnection, width);
        case L_SHAPED:
            return new LShapedTunnel(id, startConnection, endConnection, width);
        default:
            return new StraightTunnel(id, startConnection, endConnection, width);
        }
    }

    /**
     * Create a tunnel with proper alignment based on room connection points. This
     * method ensures tunnels connect correctly and follow distance constraints.
     */
    public static DungeonTunnel createAlignedTunnel(String id, DungeonRoom startRoom, DungeonRoom endRoom,
            Direction direction, Random random, int maxTunnelDistance) {

        // Calculate the actual connection points
        Point3D startPoint = startRoom.getConnectionPoint(direction);
        Point3D endPoint = endRoom.getConnectionPoint(direction.opposite());

        // Ensure same Y level for proper tunnel connection
        int connectionY = Math.min(startRoom.getCenter().getY(), endRoom.getCenter().getY());
        startPoint = new Point3D(startPoint.getX(), connectionY, startPoint.getZ());
        endPoint = new Point3D(endPoint.getX(), connectionY, endPoint.getZ());

//        // Validate distance constraint
//        if (totalDistance > maxTunnelDistance) {
//            System.out.println("Tunnel distance " + totalDistance + " exceeds maximum " + maxTunnelDistance);
//            return null;
//        }

        // Determine tunnel type based on alignment and constraints
        TunnelType tunnelType = determineOptimalTunnelType(startPoint, endPoint, maxTunnelDistance);

        int width = 3 + random.nextInt(3); // Width between 3-5
        int tunnelHeight = Math.min(startRoom.getHeight(), endRoom.getHeight());

        TunnelConnection startConnection = new TunnelConnection(startRoom, direction, startPoint, tunnelHeight);
        TunnelConnection endConnection = new TunnelConnection(endRoom, direction.opposite(), endPoint, tunnelHeight);

        switch (tunnelType) {
        case STRAIGHT:
            return new StraightTunnel(id, startConnection, endConnection, width);
        case L_SHAPED:
            return new LShapedTunnel(id, startConnection, endConnection, width);
        default:
            return new StraightTunnel(id, startConnection, endConnection, width);
        }
    }

    public static DungeonTunnel createRandomTunnel(String id, DungeonRoom startRoom, DungeonRoom endRoom,
            Direction direction, Random random, int maxTunnelDistance) {
        return createAlignedTunnel(id, startRoom, endRoom, direction, random, maxTunnelDistance);
    }

    /**
     * Determine the optimal tunnel type based on connection points and distance
     * constraints
     */
    private static TunnelType determineOptimalTunnelType(Point3D start, Point3D end, int maxTunnelDistance) {
        int deltaX = Math.abs(end.getX() - start.getX());
        int deltaZ = Math.abs(end.getZ() - start.getZ());

        // For very short max distances, prefer straight tunnels only
        if (maxTunnelDistance <= 20) {
            return TunnelType.STRAIGHT;
        }

        // If points are perfectly aligned on one axis, use straight tunnel
        if (deltaX == 0 || deltaZ == 0) {
            return TunnelType.STRAIGHT;
        }

        // If both deltas are small, L-shaped is fine
        if (deltaX <= 10 && deltaZ <= 10) {
            return TunnelType.L_SHAPED;
        }

        // For larger distances with both X and Z components, prefer L-shaped
        if (deltaX > 0 && deltaZ > 0) {
            return TunnelType.L_SHAPED;
        }

        return TunnelType.STRAIGHT;
    }

    private static TunnelType selectOptimalTunnelType(Point3D start, Point3D end, TunnelType requestedType,
            int maxDistance) {
        int deltaX = Math.abs(end.getX() - start.getX());
        int deltaZ = Math.abs(end.getZ() - start.getZ());
        int totalDistance = deltaX + deltaZ;

        // If distance is within max and points are aligned, force straight tunnel
        if (totalDistance <= maxDistance && (deltaX == 0 || deltaZ == 0)) {
            return TunnelType.STRAIGHT;
        }

        // If distance is within max but not aligned, use L-shaped
        if (totalDistance <= maxDistance && deltaX > 0 && deltaZ > 0) {
            return TunnelType.L_SHAPED;
        }

        // For longer distances, honor the requested type but prefer L-shaped for
        // non-aligned
        if (deltaX > 0 && deltaZ > 0) {
            return TunnelType.L_SHAPED;
        }

        return TunnelType.STRAIGHT;
    }

    // Add this method to TunnelFactory.java

    public static DungeonTunnel createAlignedTunnelWithWidth(String id, DungeonRoom startRoom, DungeonRoom endRoom,
            Direction direction, Random random, int maxTunnelDistance, int width) {

        // Calculate the actual connection points
        Point3D startPoint = startRoom.getConnectionPoint(direction);
        Point3D endPoint = endRoom.getConnectionPoint(direction.opposite());

        // Ensure same Y level for proper tunnel connection
        int connectionY = Math.min(startRoom.getCenter().getY(), endRoom.getCenter().getY());
        startPoint = new Point3D(startPoint.getX(), connectionY, startPoint.getZ());
        endPoint = new Point3D(endPoint.getX(), connectionY, endPoint.getZ());

        // Determine tunnel type based on alignment and constraints
        TunnelType tunnelType = determineOptimalTunnelType(startPoint, endPoint, maxTunnelDistance);

        int tunnelHeight = Math.min(startRoom.getHeight(), endRoom.getHeight());

        TunnelConnection startConnection = new TunnelConnection(startRoom, direction, startPoint, tunnelHeight);
        TunnelConnection endConnection = new TunnelConnection(endRoom, direction.opposite(), endPoint, tunnelHeight);

        switch (tunnelType) {
        case STRAIGHT:
            return new StraightTunnel(id, startConnection, endConnection, width);
        case L_SHAPED:
            return new LShapedTunnel(id, startConnection, endConnection, width);
        default:
            return new StraightTunnel(id, startConnection, endConnection, width);
        }
    }

}