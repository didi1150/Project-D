package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonCeilingBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonWallBlock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WallGenerator {

    public static Set<Vector3Int> createWalls(Set<Vector3Int> floorPositions)
    {
        return findWallsInDirections(floorPositions, Direction3D.get2DCardinalDirections());
    }

    private static Set<Vector3Int> findWallsInDirections(Set<Vector3Int> floorPositions, List<Direction3D> directionList)
    {
        return findWallsInDirections(floorPositions, floorPositions, directionList);
    }

    private static Set<Vector3Int> findWallsInDirections(Set<Vector3Int> floorPositions, Set<Vector3Int> allPositions, List<Direction3D> directionList)
    {
        Set<Vector3Int> wallPositions = new LinkedHashSet<>();
        for (var position : floorPositions) {
            for (var direction : directionList) {
                var neighbourPosition = direction.apply(position);
                if (!allPositions.contains(neighbourPosition))
                    wallPositions.add(neighbourPosition);
            }
        }
        return wallPositions;
    }

    private static Set<Vector3Int> getExtendedRoomFloor(Set<Vector3Int> roomFloor, Set<Vector3Int> corridorFloor, List<BoundingBox> rooms) {
        Set<Vector3Int> extendedRoomFloor = new LinkedHashSet<>(roomFloor);
        for (var room : rooms) {
            Set<Vector3Int> floor = corridorFloor.stream()
                    .filter(room::contains)
                    .filter(vec -> vec.y == room.get2DCenter().y)
                    .filter(vec -> {
                        Vector3Int distance = vec.sub(room.getMinPoint());
                        boolean xPresent = false;
                        for (int x = 0; x < room.getDimensions().x; x++) {
                            Vector3Int pos = room.getMinPoint().add(x,0, distance.z);
                            if (!pos.equals(vec) && roomFloor.contains(pos)) {
                                xPresent = true;
                                break;
                            }
                        }
                        boolean zPresent = false;
                        for (int z = 0; z < room.getDimensions().z; z++) {
                            Vector3Int pos = room.getMinPoint().add(distance.x,0,z);
                            if (!pos.equals(vec) && roomFloor.contains(pos)) {
                                zPresent = true;
                                break;
                            }
                        }
                        return xPresent && zPresent;
                    })
                    .collect(Collectors.toSet());
            extendedRoomFloor.addAll(floor);
        }
        return extendedRoomFloor;
    }

    public static Set<DungeonCeilingBlock> createCeiling(Set<Vector3Int> roomFloor, Set<Vector3Int> corridorFloor, Set<Vector3Int> stairFloor, List<BoundingBox> rooms, int roomOffset, int corridorWidth) {
        var extendedRoomFloor = getExtendedRoomFloor(roomFloor, corridorFloor, rooms);

        Set<DungeonCeilingBlock> roomCeiling = findRoomCeiling(extendedRoomFloor, rooms, roomOffset);
        Set<DungeonCeilingBlock> corridorCeiling = findCorridorCeiling(corridorFloor, extendedRoomFloor, corridorWidth);
        corridorCeiling.addAll(findCorridorCeiling(stairFloor, extendedRoomFloor, corridorWidth + 1));
        roomCeiling.addAll(corridorCeiling);
        return roomCeiling;
    }

    private static Set<DungeonCeilingBlock> findRoomCeiling(Set<Vector3Int> roomFloor, List<BoundingBox> rooms, int roomOffset) {
        Set<DungeonCeilingBlock> wallPositions = new LinkedHashSet<>();
        for (var position : roomFloor) {
            int wallHeight = rooms.stream().filter(r -> r.contains(position)).map(r -> r.getDimensions().getY() - roomOffset).findFirst().orElse(0);
            wallPositions.add(new DungeonCeilingBlock(position.add(0, wallHeight + 1,0)));
        }
        return wallPositions;
    }

    private static Set<DungeonCeilingBlock> findCorridorCeiling(Set<Vector3Int> corridorFloor, Set<Vector3Int> roomFloor, int corridorWidth) {
        Set<DungeonCeilingBlock> wallPositions = new LinkedHashSet<>();
        int height = Math.max(2, corridorWidth);
        for (var position : corridorFloor) {
            if (!roomFloor.contains(position)) {
                wallPositions.add(new DungeonCeilingBlock(position.add(0, height + 1,0)));
            }
        }
        return wallPositions;
    }

    public static Set<DungeonWallBlock> createWalls(Set<Vector3Int> roomFloor, Set<Vector3Int> corridorFloor, Set<Vector3Int> stairFloor, Set<Vector3Int> corridorPositions, List<BoundingBox> rooms, int roomOffset, int corridorWidth) {
        Set<Vector3Int> combinedSet = new LinkedHashSet<>(roomFloor);
        combinedSet.addAll(corridorFloor);
        combinedSet.addAll(stairFloor);

        var extendedRoomFloor = getExtendedRoomFloor(roomFloor, corridorFloor, rooms);

        Set<DungeonWallBlock> roomWalls = findRoomWalls(extendedRoomFloor, combinedSet, corridorPositions, Direction3D.get2DCardinalDirections(), rooms, roomOffset, corridorWidth);
        Set<DungeonWallBlock> corridorWalls = findCorridorWalls(corridorFloor, extendedRoomFloor, combinedSet, Direction3D.get2DCardinalDirections(), corridorWidth, false);
        corridorWalls.addAll(findCorridorWalls(stairFloor, extendedRoomFloor, combinedSet, Direction3D.get2DCardinalDirections(), corridorWidth, true));
        roomWalls.addAll(corridorWalls);
        return roomWalls;
    }

    private static Set<DungeonWallBlock> findRoomWalls(Set<Vector3Int> roomFloor, Set<Vector3Int> allPositions, Set<Vector3Int> corridorPositions, List<Direction3D> directionList, List<BoundingBox> rooms, int roomOffset, int corridorWidth) {
        Set<DungeonWallBlock> wallPositions = new LinkedHashSet<>();
        for (var position : roomFloor) {
            int wallHeight = rooms.stream().filter(r -> r.contains(position)).map(r -> r.getDimensions().getY() - roomOffset).findFirst().orElse(0);
            for (var direction : directionList) {
                var neighbourPosition = direction.apply(position);
//                var neighbourPositionDown = Direction3D.DOWN.apply(neighbourPosition);
//                var neighbourPositionUP = Direction3D.UP.apply(neighbourPosition);
//                if (!allPositions.contains(neighbourPosition) && !allPositions.contains(neighbourPositionDown) && !allPositions.contains(neighbourPositionUP)){
//                    wallPositions.add(neighbourPosition);
//                    for (var i = 0; i < wallHeight; i++) {
//                        neighbourPosition = Direction3D.UP.apply(neighbourPosition);
//                        wallPositions.add(neighbourPosition);
//                    }
//                } else if (!roomFloor.contains(neighbourPosition)) {
//                    int stairHeight = corridorWidth; // add 1 for stair floor
//                    if (allPositions.contains(neighbourPositionUP)) stairHeight++;
//                    else if (allPositions.contains(neighbourPositionDown)) stairHeight--;
//                    neighbourPosition = Direction3D.UP.apply(neighbourPosition, stairHeight);
//                    int corridorWallHeight = rooms.stream().filter(r -> r.contains(position)).map(r -> r.getDimensions().getY() - roomOffset - corridorWidth - 1).findFirst().orElse(0);
//                    for (var i = 0; i < corridorWallHeight; i++) {
//                        neighbourPosition = Direction3D.UP.apply(neighbourPosition);
//                        wallPositions.add(neighbourPosition);
//                    }
//                }
                if (!roomFloor.contains(neighbourPosition)) {
                    if (!corridorPositions.contains(neighbourPosition)) wallPositions.add(new DungeonWallBlock(neighbourPosition, direction.opposite()));
                    for (var i = 0; i < wallHeight; i++) {
                        neighbourPosition = Direction3D.UP.apply(neighbourPosition);
                        if (!corridorPositions.contains(neighbourPosition)) wallPositions.add(new DungeonWallBlock(neighbourPosition, direction.opposite()));
                    }
                }
            }
        }
        return wallPositions;
    }

    private static Set<DungeonWallBlock> findCorridorWalls(Set<Vector3Int> corridorFloor, Set<Vector3Int> roomFloor, Set<Vector3Int> allPositions, List<Direction3D> directionList, int corridorWidth, boolean isStair) {
        Set<DungeonWallBlock> wallPositions = new LinkedHashSet<>();
        corridorWidth = isStair ? corridorWidth + 1 : corridorWidth;
        int height = Math.max(2, corridorWidth);
        for (var position : corridorFloor) {
            if (isStair) wallPositions.add(new DungeonWallBlock(Direction3D.DOWN.apply(position), null));
            for (var direction : directionList) {
                var neighbourPosition = direction.apply(position);
                var neighbourPositionDown = Direction3D.DOWN.apply(neighbourPosition);
                var neighbourPositionUP = Direction3D.UP.apply(neighbourPosition);
                if (!allPositions.contains(neighbourPosition) && !allPositions.contains(neighbourPositionDown)&& !allPositions.contains(neighbourPositionUP)) {
                    wallPositions.add(new DungeonWallBlock(neighbourPosition, direction.opposite()));
                    for (var i = 0; i < height; i++) {
                        neighbourPosition = Direction3D.UP.apply(neighbourPosition);
                        wallPositions.add(new DungeonWallBlock(neighbourPosition, direction.opposite()));
                    }
                }
            }
        }
        return wallPositions;
    }
}

