package dev.core.game.dungeon.proceduralDungeon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.util.DungeonRoom;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnLocation;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.settings.GameSettings;

class RoomFirstDungeonGenerator3DTest {

    @Test
    void corridorInsideStartRoomShouldBeRejected() throws Exception {
        RoomFirstDungeonGenerator3D generator = new RoomFirstDungeonGenerator3D();
        BoundingBox startRoom = new BoundingBox(0, 0, 0, 4, 2, 4);
        BoundingBox targetRoom = new BoundingBox(10, 0, 10, 14, 2, 14);
        Set<Vector3Int> corridor = Set.of(new Vector3Int(2, 1, 2));

        Method method = RoomFirstDungeonGenerator3D.class.getDeclaredMethod(
                "doesCorridorOverlapWithRooms",
                List.class,
                BoundingBox.class,
                BoundingBox.class,
                Set.class
        );
        method.setAccessible(true);

        boolean overlap = (boolean) method.invoke(generator, List.of(startRoom, targetRoom), startRoom, targetRoom, corridor);

        assertTrue(overlap);
    }

    @Test
    void startRoomShouldHaveNoSpawnLocations() throws Exception {
        RoomFirstDungeonGenerator3D generator = new RoomFirstDungeonGenerator3D();
        BoundingBox roomBox = new BoundingBox(0, 0, 0, 4, 0, 4);
        Set<Vector3Int> roomFloor = new LinkedHashSet<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                roomFloor.add(new Vector3Int(x, 0, z));
            }
        }
        DungeonRoom startRoom = new DungeonRoom(roomBox, roomFloor);
        Set<Vector3Int> possibleSpawnPositions = new LinkedHashSet<>(roomFloor);

        Field startRoomField = RoomFirstDungeonGenerator3D.class.getDeclaredField("startRoom");
        startRoomField.setAccessible(true);
        startRoomField.set(generator, roomBox);

        Method method = RoomFirstDungeonGenerator3D.class.getDeclaredMethod(
                "createRoomSpawnLocations",
                Set.class,
                Set.class,
                DungeonRoom.class,
                Random.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<SpawnLocation> spawnLocations = (List<SpawnLocation>) method.invoke(generator, roomFloor, possibleSpawnPositions, startRoom, new Random(42));

        assertEquals(0, spawnLocations.size());
    }

    @Test
    void roomSpawnCountScalesWithDungeonFloorButCapsAtTwenty() throws Exception {
        GameSettings.getCurrentSettings().setFloor(50);
        BoundingBox roomBox = new BoundingBox(0, 0, 0, 19, 0, 9);
        Set<Vector3Int> roomFloor = new LinkedHashSet<>();
        for (int x = 0; x <= 19; x++) {
            for (int z = 0; z <= 9; z++) {
                roomFloor.add(new Vector3Int(x, 0, z));
            }
        }
        DungeonRoom room = new DungeonRoom(roomBox, roomFloor);

        Method method = RoomFirstDungeonGenerator3D.class.getDeclaredMethod(
                "calculateRoomSpawnCount",
                DungeonRoom.class
        );
        method.setAccessible(true);

        RoomFirstDungeonGenerator3D generator = new RoomFirstDungeonGenerator3D();
        int spawnCount = (int) method.invoke(generator, room);
        assertEquals(20, spawnCount);
    }
}
