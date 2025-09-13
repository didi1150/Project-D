package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.List;

public class LShapedTunnel extends DungeonTunnel {

    private List<BoundingBox> boundingBoxes;

    public LShapedTunnel(String id, TunnelConnection start, TunnelConnection end, int width) {
        super(id, start, end, width);
        generateTunnelBoundingBoxes();
    }

    private void generateTunnelBoundingBoxes() {
        List<Point3D> path = generateLShapedPath();
        boundingBoxes = new ArrayList<>();

        if (path.size() < 2) {
            return;
        }

        // Find the corner point where direction changes
        Point3D start = path.get(0);
        Point3D end = path.get(path.size() - 1);
        Point3D corner = null;

        // Find where the path changes direction
        for (int i = 1; i < path.size() - 1; i++) {
            Point3D prev = path.get(i - 1);
            Point3D curr = path.get(i);
            Point3D next = path.get(i + 1);

            // Check if direction changes
            Direction dir1 = getDirectionBetween(prev, curr);
            Direction dir2 = getDirectionBetween(curr, next);

            if (dir1 != dir2) {
                corner = curr;
                break;
            }
        }

        if (corner != null) {
            // Create two segments
            Point3D segment1Start = start;
            Point3D segment1End = corner;
            Point3D segment2Start = corner;
            Point3D segment2End = end;

            boundingBoxes.add(createSegmentBoundingBox(segment1Start, segment1End));
            boundingBoxes.add(createSegmentBoundingBox(segment2Start, segment2End));
        } else {
            // Fallback: treat as single segment
            boundingBoxes.add(createSegmentBoundingBox(start, end));
        }
    }

    private BoundingBox createSegmentBoundingBox(Point3D start, Point3D end) {
        int halfWidth = (int) Math.ceil(width / 2.0);

        int minX = Math.min(start.getX(), end.getX()) - halfWidth;
        int maxX = Math.max(start.getX(), end.getX()) + halfWidth;
        int minY = Math.min(start.getY(), end.getY()) - 1;
        int maxY = Math.max(start.getY(), end.getY()) + height;
        int minZ = Math.min(start.getZ(), end.getZ()) - halfWidth;
        int maxZ = Math.max(start.getZ(), end.getZ()) + halfWidth;

        return new BoundingBox(new Point3D(minX, minY, minZ), new Point3D(maxX, maxY, maxZ));
    }

    private Direction getDirectionBetween(Point3D from, Point3D to) {
        int deltaX = to.getX() - from.getX();
        int deltaZ = to.getZ() - from.getZ();

        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            return deltaX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return deltaZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public List<BoundingBox> getBoundingBoxes() {
        return new ArrayList<>(boundingBoxes);
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

        // Always start exactly at the start connection point
        Point3D current = start;
        path.add(current);

        // Choose corner based on connection direction preference
        Point3D corner = chooseBestCorner(start, end);

        // Move to corner
        int deltaX = corner.getX() - current.getX();
        int deltaZ = corner.getZ() - current.getZ();

        // Move in X direction to corner
        if (deltaX != 0) {
            int stepX = deltaX > 0 ? 1 : -1;
            while (current.getX() != corner.getX()) {
                current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                path.add(current);
            }
        }

        // Move in Z direction to corner
        if (deltaZ != 0) {
            int stepZ = deltaZ > 0 ? 1 : -1;
            while (current.getZ() != corner.getZ()) {
                current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                path.add(current);
            }
        }

        // Move from corner to end
        deltaX = end.getX() - current.getX();
        deltaZ = end.getZ() - current.getZ();

        // Move in X direction to end
        if (deltaX != 0) {
            int stepX = deltaX > 0 ? 1 : -1;
            while (current.getX() != end.getX()) {
                current = new Point3D(current.getX() + stepX, current.getY(), current.getZ());
                path.add(current);
            }
        }

        // Move in Z direction to end
        if (deltaZ != 0) {
            int stepZ = deltaZ > 0 ? 1 : -1;
            while (current.getZ() != end.getZ()) {
                current = new Point3D(current.getX(), current.getY(), current.getZ() + stepZ);
                path.add(current);
            }
        }

        // Verify the path ends exactly at the end connection point
        if (!path.get(path.size() - 1).equals(end)) {
            System.err.println("WARNING: L-shaped tunnel path doesn't end at connection point!");
            path.add(end); // Force add the end point
        }

        return path;
    }

    private Point3D chooseBestCorner(Point3D start, Point3D end) {
        // Two possible corners: (start.x, end.z) or (end.x, start.z)
        Point3D corner1 = new Point3D(start.getX(), start.getY(), end.getZ());
        Point3D corner2 = new Point3D(end.getX(), start.getY(), start.getZ());

        // Choose the corner that aligns better with the intended connection direction
        Direction startDir = startConnection.getDirection();

        // If we're going EAST or WEST first, prefer corner1
        if (startDir == Direction.EAST || startDir == Direction.WEST) {
            return corner1;
        } else {
            return corner2;
        }
    }

    @Override
    protected TunnelType getType() {
        return TunnelType.L_SHAPED;
    }
}