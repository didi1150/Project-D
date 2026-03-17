package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;

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

    public static Set<Vector3Int> createWalls(Set<Vector3Int> roomFloor, Set<Vector3Int> corridorFloor, Set<Vector3Int> stairFloor, Set<Vector3Int> corridorPositions, List<BoundingBox> rooms, int roomOffset, int corridorWidth) {
        Set<Vector3Int> combinedSet = new LinkedHashSet<>(roomFloor);
        combinedSet.addAll(corridorFloor);
        combinedSet.addAll(stairFloor);

        Set<Vector3Int> extendedRoomFloor = new LinkedHashSet<>(roomFloor);

        for (var room : rooms) {
//            Set<Vector3Int> floor = corridorFloor.stream().filter(room::contains).filter(vec -> vec.y == room.get2DCenter().y).collect(Collectors.toSet());
//            Set<Vector3Int> floor = corridorFloor.stream().filter(roomFloor::contains).collect(Collectors.toSet());
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

        Set<Vector3Int> roomWalls = findRoomWalls(extendedRoomFloor, combinedSet, corridorPositions, Direction3D.get2DCardinalDirections(), rooms, roomOffset, corridorWidth);
        Set<Vector3Int> corridorWalls = findCorridorWalls(corridorFloor, extendedRoomFloor, combinedSet, Direction3D.get2DCardinalDirections(), corridorWidth);
        corridorWalls.addAll(findCorridorWalls(stairFloor, extendedRoomFloor, combinedSet, Direction3D.get2DCardinalDirections(), corridorWidth + 1));
        roomWalls.addAll(corridorWalls);
        return roomWalls;
    }

    private static Set<Vector3Int> findRoomWalls(Set<Vector3Int> roomFloor, Set<Vector3Int> allPositions, Set<Vector3Int> corridorPositions, List<Direction3D> directionList, List<BoundingBox> rooms, int roomOffset, int corridorWidth) {
        Set<Vector3Int> wallPositions = new LinkedHashSet<>();
        for (var position : roomFloor) {
            int wallHeight = rooms.stream().filter(r -> r.contains(position)).map(r -> r.getDimensions().getY() - roomOffset - 1).findFirst().orElse(0);
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
                    for (var i = 0; i < wallHeight; i++) {
                        neighbourPosition = Direction3D.UP.apply(neighbourPosition);
                        if (!corridorPositions.contains(neighbourPosition)) wallPositions.add(neighbourPosition);
                    }
                }
            }
            wallPositions.add(position.add(0, wallHeight + 1,0)); // ceiling
        }
        return wallPositions;
    }

    private static Set<Vector3Int> findCorridorWalls(Set<Vector3Int> corridorFloor, Set<Vector3Int> roomFloor, Set<Vector3Int> allPositions, List<Direction3D> directionList, int corridorWidth) {
        Set<Vector3Int> wallPositions = new LinkedHashSet<>();
        int height = Math.max(2, corridorWidth); // add 1 for stair floor
        for (var position : corridorFloor) {
            for (var direction : directionList) {
                var neighbourPosition = direction.apply(position);
                var neighbourPositionDown = Direction3D.DOWN.apply(neighbourPosition);
                var neighbourPositionUP = Direction3D.UP.apply(neighbourPosition);
                if (!allPositions.contains(neighbourPosition) && !allPositions.contains(neighbourPositionDown)&& !allPositions.contains(neighbourPositionUP)) {
                    wallPositions.add(neighbourPosition);
                    for (var i = 0; i < height; i++) {
                        neighbourPosition = Direction3D.UP.apply(neighbourPosition);
                        wallPositions.add(neighbourPosition);
                    }
                }
            }
            if (!roomFloor.contains(position)) {
                wallPositions.add(position.add(0, height + 1,0)); // ceiling
            }
        }
        return wallPositions;
    }
}

