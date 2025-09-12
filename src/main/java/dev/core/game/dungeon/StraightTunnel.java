package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.List;

public class StraightTunnel extends DungeonTunnel {

    public StraightTunnel(String id, TunnelConnection startConnection, TunnelConnection endConnection, int width) {
        super(id, startConnection, endConnection, width);
    }

    @Override
    protected void generateTunnel() {
        List<Point3D> pathPoints = generateStraightPath();
        generateTunnelStructure(pathPoints);
    }

    private List<Point3D> generateStraightPath() {
        List<Point3D> path = new ArrayList<>();
        Point3D start = startConnection.getConnectionPoint();
        Point3D end = endConnection.getConnectionPoint();

        int deltaX = end.getX() - start.getX();
        int deltaZ = end.getZ() - start.getZ();

        // Determine the primary direction
        if (Math.abs(deltaX) >= Math.abs(deltaZ)) {
            // Move horizontally first, then vertically
            Point3D current = start;
            path.add(current);

            int stepX = deltaX == 0 ? 0 : (deltaX > 0 ? 1 : -1);
            while (current.getX() != end.getX()) {
                current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                path.add(current);
            }

            int stepZ = deltaZ == 0 ? 0 : (deltaZ > 0 ? 1 : -1);
            while (current.getZ() != end.getZ()) {
                current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                path.add(current);
            }
        } else {
            // Move vertically first, then horizontally
            Point3D current = start;
            path.add(current);

            int stepZ = deltaZ == 0 ? 0 : (deltaZ > 0 ? 1 : -1);
            while (current.getZ() != end.getZ()) {
                current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                path.add(current);
            }

            int stepX = deltaX == 0 ? 0 : (deltaX > 0 ? 1 : -1);
            while (current.getX() != end.getX()) {
                current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                path.add(current);
            }
        }

        return path;
    }

    @Override
    protected TunnelType getType() {
        return TunnelType.STRAIGHT;
    }

}
