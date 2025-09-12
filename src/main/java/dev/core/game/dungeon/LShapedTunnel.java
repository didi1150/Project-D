package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.List;

public class LShapedTunnel extends DungeonTunnel {

    private List<BoundingBox> boundingBoxes; // two segments

    public LShapedTunnel(String id, TunnelConnection start, TunnelConnection end, int width) {
        super(id, start, end, width);
        generateTunnelBoundingBoxes();
    }

    private void generateTunnelBoundingBoxes() {
        Point3D start = startConnection.getConnectionPoint();
        Point3D end = endConnection.getConnectionPoint();
        Point3D mid;

        if (Math.random() < 0.5) {
            mid = new Point3D(end.getX(), start.getY(), start.getZ()); // X then Z
        } else {
            mid = new Point3D(start.getX(), start.getY(), end.getZ()); // Z then X
        }

        boundingBoxes = List.of(new BoundingBox(start, mid), new BoundingBox(mid, end));
    }

    public List<BoundingBox> getBoundingBoxes() {
        return boundingBoxes;
    }

    @Override
    protected void generateTunnel() {
        List<Point3D> pathPoints = generateLShapedPath();
        generateTunnelStructure(pathPoints);
    }

    private List<Point3D> generateLShapedPath() {
        List<Point3D> path = new ArrayList<>();
        Point3D start = startConnection.getConnectionPoint();
        Point3D end = endConnection.getConnectionPoint();
        Point3D current = start;
        path.add(current);

        int stepX = Integer.compare(end.getX(), start.getX());
        while (current.getX() != end.getX()) {
            current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
            path.add(current);
        }

        int stepZ = Integer.compare(end.getZ(), current.getZ());
        while (current.getZ() != end.getZ()) {
            current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
            path.add(current);
        }

        return path;
    }

    @Override
    protected TunnelType getType() {
        return TunnelType.L_SHAPED;
    }
}
