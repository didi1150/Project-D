package dev.core.game.dungeon;

public class QuadraticRoom extends DungeonRoom {

    public QuadraticRoom(String id, Point3D center, int size, int height) {
        super(id, center, size, height);
    }

    @Override
    protected void generateRoomStructure() {
        int halfSize = size / 2;

        // Generate floor blocks first
        for (int x = center.getX() - halfSize; x <= center.getX() + halfSize; x++) {
            for (int z = center.getZ() - halfSize; z <= center.getZ() + halfSize; z++) {
                addRoomBlocks(x, z);
            }
        }

        // Add walls using the standard method (consistent with SpawnRoom and
        // EndPortalRoom)
        addWallsForBlocks();
    }

    @Override
    public Point3D getConnectionPoint(Direction direction) {
        int halfSize = size / 2;
        switch (direction) {
        case NORTH:
            return new Point3D(center.getX(), center.getY(), center.getZ() - halfSize);
        case SOUTH:
            return new Point3D(center.getX(), center.getY(), center.getZ() + halfSize);
        case EAST:
            return new Point3D(center.getX() + halfSize, center.getY(), center.getZ());
        case WEST:
            return new Point3D(center.getX() - halfSize, center.getY(), center.getZ());
        default:
            return center;
        }
    }

    @Override
    public RoomType getType() {
        return RoomType.SQUARE_ROOM; // Updated to match the new RoomType enum
    }

}
