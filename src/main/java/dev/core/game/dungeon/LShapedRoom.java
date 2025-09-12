package dev.core.game.dungeon;

public class LShapedRoom extends DungeonRoom {

    public LShapedRoom(String id, Point3D center, int size, int height) {
        super(id, center, size, height);
    }

    @Override
    protected void generateRoomStructure() {
        int halfSize = size / 2;

        // Generate two rectangles forming an L
        // Horizontal part (left rectangle)
        for (int x = center.getX() - halfSize; x <= center.getX() + halfSize; x++) {
            for (int z = center.getZ() - halfSize; z <= center.getZ(); z++) {
                addRoomBlocks(x, z);
            }
        }

        // Vertical part (bottom rectangle)
        for (int x = center.getX(); x <= center.getX() + halfSize; x++) {
            for (int z = center.getZ(); z <= center.getZ() + halfSize; z++) {
                addRoomBlocks(x, z);
            }
        }

        // Add walls using the standard method
        addWallsForBlocks();
    }

    @Override
    public Point3D getConnectionPoint(Direction direction) {
        int halfSize = size / 2;
        // L-shaped rooms have different connection points based on their shape
        switch (direction) {
        case NORTH:
            return new Point3D(center.getX() - halfSize / 2, center.getY(), center.getZ() - halfSize);
        case SOUTH:
            return new Point3D(center.getX() + halfSize / 2, center.getY(), center.getZ() + halfSize);
        case EAST:
            return new Point3D(center.getX() + halfSize, center.getY(), center.getZ() + halfSize / 2);
        case WEST:
            return new Point3D(center.getX() - halfSize, center.getY(), center.getZ() - halfSize / 2);
        default:
            return center;
        }
    }

    /**
     * Override to provide more accurate connection points that ensure the
     * connection is actually within the room's floor area
     */
    @Override
    public boolean hasValidConnectionPoint(Direction direction) {
        Point3D connectionPoint = getConnectionPoint(direction);

        // Check if the connection point has a floor block
        Point3D floorPoint = new Point3D(connectionPoint.getX(), connectionPoint.getY() - 1, connectionPoint.getZ());

        // Verify the floor point is within our generated floor blocks
        return floorBlocks.contains(floorPoint);
    }

    /**
     * Get alternative connection points if the primary one isn't valid This helps
     * when the L-shape orientation doesn't align with requested direction
     */
    public Point3D getAlternativeConnectionPoint(Direction direction) {
        int halfSize = size / 2;

        switch (direction) {
        case NORTH:
            // Try different points along the north edge of horizontal arm
            for (int x = center.getX() - halfSize + 1; x <= center.getX() + halfSize - 1; x++) {
                Point3D candidate = new Point3D(x, center.getY(), center.getZ() - halfSize);
                Point3D floorCheck = new Point3D(x, center.getY() - 1, center.getZ() - halfSize + 1);
                if (floorBlocks.contains(floorCheck)) {
                    return candidate;
                }
            }
            break;

        case SOUTH:
            // Try different points along the south edge of vertical arm
            for (int x = center.getX() + 1; x <= center.getX() + halfSize - 1; x++) {
                Point3D candidate = new Point3D(x, center.getY(), center.getZ() + halfSize);
                Point3D floorCheck = new Point3D(x, center.getY() - 1, center.getZ() + halfSize - 1);
                if (floorBlocks.contains(floorCheck)) {
                    return candidate;
                }
            }
            break;

        case EAST:
            // Try different points along the east edge of vertical arm
            for (int z = center.getZ() + 1; z <= center.getZ() + halfSize - 1; z++) {
                Point3D candidate = new Point3D(center.getX() + halfSize, center.getY(), z);
                Point3D floorCheck = new Point3D(center.getX() + halfSize - 1, center.getY() - 1, z);
                if (floorBlocks.contains(floorCheck)) {
                    return candidate;
                }
            }
            break;

        case WEST:
            // Try different points along the west edge of horizontal arm
            for (int z = center.getZ() - halfSize + 1; z <= center.getZ() - 1; z++) {
                Point3D candidate = new Point3D(center.getX() - halfSize, center.getY(), z);
                Point3D floorCheck = new Point3D(center.getX() - halfSize + 1, center.getY() - 1, z);
                if (floorBlocks.contains(floorCheck)) {
                    return candidate;
                }
            }
            break;
        }

        // Fallback to original connection point
        return getConnectionPoint(direction);
    }

    @Override
    public RoomType getType() {
        return RoomType.L_SHAPED_ROOM;
    }
}