package dev.core.game.dungeon;

public class LShapedRoom extends DungeonRoom {

    public LShapedRoom(String id, Point3D center, int size, int height) {
        super(id, center, size, height);
    }

    @Override
    protected void generateRoomStructure() {
        int halfSize = size / 2;

        // Generate two rectangles forming an L
        // Horizontal part
        for (int x = center.getX() - halfSize; x <= center.getX() + halfSize; x++) {
            for (int z = center.getZ() - halfSize; z <= center.getZ(); z++) {
                addRoomBlocks(x, z);
            }
        }

        // Vertical part
        for (int x = center.getX(); x <= center.getX() + halfSize; x++) {
            for (int z = center.getZ(); z <= center.getZ() + halfSize; z++) {
                addRoomBlocks(x, z);
            }
        }

        // Add walls
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

    @Override
    public RoomType getType() {
        return RoomType.L_SHAPE;
    }

}
