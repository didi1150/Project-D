package dev.core.game.dungeon;

import java.util.Random;

public class RoomFactory {

    public static DungeonRoom createRoom(RoomType type, String id, Point3D center, int size, int height) {
        switch (type) {
        case SQUARE_ROOM:
            return new QuadraticRoom(id, center, size, height);
        case L_SHAPED_ROOM:
            return new LShapedRoom(id, center, size, height);
        case CIRCULAR_ROOM:
            return new RoundRoom(id, center, size, height);
        case CROSS_ROOM:
            return new QuadraticRoom(id, center, size, height); // Fallback for now
        case SPAWN_ROOM:
            return new SpawnRoom(id, center, size);
        case END_PORTAL_ROOM:
            return new EndPortalRoom(id, center, size);
        case TREASURE_ROOM:
            return new QuadraticRoom(id, center, size, height); // Fallback for now
        case BOSS_ROOM:
            return new QuadraticRoom(id, center, size, height); // Fallback for now
        default:
            return new QuadraticRoom(id, center, size, height);
        }
    }

    public static DungeonRoom createRandomRoom(String id, Point3D center, Random random) {
        // Only use standard room types for random generation (not special rooms)
        RoomType[] standardTypes = { RoomType.SQUARE_ROOM, RoomType.L_SHAPED_ROOM, RoomType.CIRCULAR_ROOM };
        RoomType type = standardTypes[random.nextInt(standardTypes.length)];

        int size = 8 + random.nextInt(8); // Size between 8-15
        if (type == RoomType.L_SHAPED_ROOM) {
            size *= 2; // If L_SHAPE then add size
        }
        int height = 5 + random.nextInt(10); // Height between 5 - 14

        return createRoom(type, id, center, size, height);
    }

}
