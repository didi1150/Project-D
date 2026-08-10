package dev.core.game.dungeon.proceduralDungeon.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import dev.core.game.dungeon.BoundingBox;
import lombok.Getter;

@Getter
public class DungeonRoom {

    private BoundingBox room;
    private Set<Vector3Int> roomFloor;
    private List<SpawnLocation> spawnPositions;
    private Set<DungeonRoom> connectedRooms = new LinkedHashSet<>();

    public DungeonRoom(BoundingBox room, Set<Vector3Int> roomFloor, Collection<SpawnLocation> spawnPositions, DungeonRoom ... connectedRooms) {
        this.room = room;
        this.roomFloor = roomFloor;
        this.spawnPositions = new LinkedList<>(spawnPositions);
        this.connectedRooms.addAll(List.of(connectedRooms));
    }

    public DungeonRoom(BoundingBox room, Set<Vector3Int> roomFloor, DungeonRoom ... connectedRooms) {
        this(room, roomFloor, new LinkedHashSet<>(), connectedRooms);
    }

    public static void addConnectionTo(DungeonRoom room1, DungeonRoom room2) {
        room1.connectedRooms.add(room2);
        room2.connectedRooms.add(room1);
    }

    public int getConnectedRoomsCount() {
        return connectedRooms.size();
    }

    public Set<DungeonRoom> getReachableRooms() {
        Set<DungeonRoom> rooms = new LinkedHashSet<>();
        for (var connectedRoom : connectedRooms) {
            rooms.addAll(connectedRoom.getReachableRooms(new LinkedList<>(List.of(this))));
        }
        return rooms;
    }

    private Set<DungeonRoom> getReachableRooms(List<DungeonRoom> alreadyReachedRooms) {
        Set<DungeonRoom> rooms = new LinkedHashSet<>(List.of(this));
        alreadyReachedRooms.add(this);
        for (var connectedRoom : connectedRooms) {
            if (alreadyReachedRooms.contains(connectedRoom)) continue;
            rooms.addAll(connectedRoom.getReachableRooms(alreadyReachedRooms));
        }
        return rooms;
    }

    public Vector3Int getRoomCenter2D() {
        return room.get2DCenter();
    }

    public int getMaxVolume() {
        return room.getVolume();
    }

    public int getRealSize() {
        return roomFloor.size();
    }

    public List<SpawnLocation> getSpawnPositions() {
        return new LinkedList<>(spawnPositions);
    }

    public void setSpawnPositions(Collection<SpawnLocation> spawnPositions) {
        this.spawnPositions = new LinkedList<>(spawnPositions);
    }

    @Override
    public String toString() {
        return "DungeonRoom{center = " + room.get2DCenter() + ", dim = " + room.getDimensions() + ", size = " + getRealSize() + ", connections = " + getConnectedRoomsCount() + "}";
    }
}
