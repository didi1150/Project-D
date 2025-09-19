package dev.core.game.dungeon;

import java.util.HashSet;
import java.util.Set;

import dev.core.game.coords.Point3D;

public class RoundRoom extends DungeonRoom {

    public RoundRoom(String id, Point3D center, int size, int height) {
        super(id, center, size, height);
    }

    @Override
    protected void generateRoomStructure() {
        int radius = size / 2;

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                double distance = Math.sqrt(Math.pow(x - center.getX(), 2) + Math.pow(z - center.getZ(), 2));

                if (distance <= radius) {
                    addRoomBlocks(x, z);
                }
            }
        }

        // Add walls using the standard method
        addWallsForBlocks();
    }

    @Override
    public Point3D getConnectionPoint(Direction direction) {
        int radius = size / 2;
        switch (direction) {
        case NORTH:
            return new Point3D(center.getX(), center.getY(), center.getZ() - radius + 1);
        case SOUTH:
            return new Point3D(center.getX(), center.getY(), center.getZ() + radius - 1);
        case EAST:
            return new Point3D(center.getX() + radius - 1, center.getY(), center.getZ());
        case WEST:
            return new Point3D(center.getX() - radius + 1, center.getY(), center.getZ());
        default:
            return center;
        }
    }

    @Override
    public RoomType getType() {
        return RoomType.CIRCULAR_ROOM; // Updated to match the new RoomType enum
    }

}
