package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.List;

public class StraightTunnel extends DungeonTunnel {

    public StraightTunnel(String id, TunnelConnection startConnection, TunnelConnection endConnection, int width) {
        super(id, startConnection, endConnection, width);
    }

    @Override
    protected void generateTunnel() {
        List<Point3D> pathPoints = generateOptimizedStraightPath();
        generateTunnelStructure(pathPoints);
    }

    // In StraightTunnel.java - fix the path generation
    private List<Point3D> generateOptimizedStraightPath() {
        List<Point3D> path = new ArrayList<>();
        Point3D start = startConnection.getConnectionPoint();
        Point3D end = endConnection.getConnectionPoint();

        // CRITICAL: Always start the path exactly at the connection point
        Point3D current = start;
        path.add(current);

        int deltaX = end.getX() - start.getX();
        int deltaZ = end.getZ() - start.getZ();

        // Generate path that ends exactly at the end connection point
        if (deltaX == 0) {
            // Moving only in Z direction
            int stepZ = deltaZ > 0 ? 1 : -1;
            while (current.getZ() != end.getZ()) {
                current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                path.add(current);
            }
        } else if (deltaZ == 0) {
            // Moving only in X direction
            int stepX = deltaX > 0 ? 1 : -1;
            while (current.getX() != end.getX()) {
                current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                path.add(current);
            }
        } else {
            // L-shaped path: move in primary direction first, then secondary
            if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
                // Move in X first
                int stepX = deltaX > 0 ? 1 : -1;
                while (current.getX() != end.getX()) {
                    current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                    path.add(current);
                }
                // Then move in Z
                int stepZ = deltaZ > 0 ? 1 : -1;
                while (current.getZ() != end.getZ()) {
                    current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                    path.add(current);
                }
            } else {
                // Move in Z first
                int stepZ = deltaZ > 0 ? 1 : -1;
                while (current.getZ() != end.getZ()) {
                    current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                    path.add(current);
                }
                // Then move in X
                int stepX = deltaX > 0 ? 1 : -1;
                while (current.getX() != end.getX()) {
                    current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                    path.add(current);
                }
            }
        }

        // Verify the path ends exactly at the end connection point
        if (!path.get(path.size() - 1).equals(end)) {
            System.err.println("WARNING: Tunnel path doesn't end at connection point!");
            System.err.println("Expected: " + end + ", Actual: " + path.get(path.size() - 1));
            path.add(end); // Force add the end point
        }

        return path;
    }

    @Override
    protected TunnelType getType() {
        return TunnelType.STRAIGHT;
    }
}