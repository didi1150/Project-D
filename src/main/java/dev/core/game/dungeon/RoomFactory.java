package dev.core.game.dungeon;

import java.util.Random;

public class RoomFactory {

    public static DungeonRoom createRoom(RoomType type, String id, Point3D center, int size, int height) {
        switch (type) {
        case QUADRATIC:
            return new QuadraticRoom(id, center, size, height);
        case L_SHAPE:
            return new LShapedRoom(id, center, size, height);
        case ROUND:
            return new RoundRoom(id, center, size, height);
        default:
            throw new IllegalArgumentException("Unknown room type: " + type);
        }
    }

    public static DungeonRoom createRandomRoom(String id, Point3D center, Random random) {
        RoomType[] types = RoomType.values();
        RoomType type = types[random.nextInt(types.length)];
        int size = 8 + random.nextInt(8); // Size between 8-15
        if (type == RoomType.L_SHAPE) {
            size *= 2; // If L_SHAPE then add size
        }
        int height = 5 + random.nextInt(10); // Height between 5 - 14

        return createRoom(type, id, center, size, height);
    }

}
