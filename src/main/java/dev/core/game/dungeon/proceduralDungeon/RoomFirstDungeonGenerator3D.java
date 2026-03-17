package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;
import lombok.Getter;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RoomFirstDungeonGenerator3D extends SimpleRandomWalkDungeonGenerator {

    public record RoomFirst3DParameters(SimpleRandomWalkParameters randomWalkParameters, int minRoomWidth, int minRoomLength, int dungeonWidth, int dungeonLength, int offset, boolean randomWalkRooms) {}

    protected int minRoomWidth = 5;
    protected int minRoomHeight = 5;
    protected int minRoomLength = 5;
    protected int dungeonWidth = 20;
    protected int dungeonHeight = 20;
    protected int dungeonLength = 20;
    protected int roomOffset = 1;
    protected boolean randomWalkRooms = false;

    protected int corridorWidth = 2;

    @Getter
    protected List<BoundingBox> rooms = new LinkedList<>();
    @Getter
    protected List<BoundingBox> notConnectedRooms = new LinkedList<>();
    @Getter
    protected BoundingBox startRoom;
    @Getter
    protected BoundingBox bossRoom;
    @Getter
    protected Set<Vector3Int> corridorFloor = new LinkedHashSet<>();
    @Getter
    protected Set<Vector3Int> stairFloor = new LinkedHashSet<>();

    public RoomFirstDungeonGenerator3D(Vector3Int startPosition, SimpleRandomWalkParameters randomWalkParameters, int minRoomWidth, int minRoomHeight, int minRoomLength, int dungeonWidth, int dungeonHeight, int dungeonLength, int roomOffset, boolean randomWalkRooms, int corridorWidth) {
        super(startPosition, randomWalkParameters);
        this.minRoomWidth = minRoomWidth;
        this.minRoomHeight = minRoomHeight;
        this.minRoomLength = minRoomLength;
        this.dungeonWidth = dungeonWidth;
        this.dungeonHeight = dungeonHeight;
        this.dungeonLength = dungeonLength;
        this.roomOffset = roomOffset;
        this.randomWalkRooms = randomWalkRooms;
        this.corridorWidth = corridorWidth;
    }

    public RoomFirstDungeonGenerator3D(SimpleRandomWalkParameters randomWalkParameters, int minRoomWidth, int minRoomHeight, int minRoomLength, int dungeonWidth, int dungeonHeight, int dungeonLength, int roomOffset, boolean randomWalkRooms, int corridorWidth) {
        super(randomWalkParameters);
        this.minRoomWidth = minRoomWidth;
        this.minRoomHeight = minRoomHeight;
        this.minRoomLength = minRoomLength;
        this.dungeonWidth = dungeonWidth;
        this.dungeonHeight = dungeonHeight;
        this.dungeonLength = dungeonLength;
        this.roomOffset = roomOffset;
        this.randomWalkRooms = randomWalkRooms;
        this.corridorWidth = corridorWidth;
    }

    public RoomFirstDungeonGenerator3D(int minRoomWidth, int minRoomHeight, int minRoomLength, int dungeonWidth, int dungeonHeight, int dungeonLength, int roomOffset, boolean randomWalkRooms, int corridorWidth) {
        this.minRoomWidth = minRoomWidth;
        this.minRoomHeight = minRoomHeight;
        this.minRoomLength = minRoomLength;
        this.dungeonWidth = dungeonWidth;
        this.dungeonHeight = dungeonHeight;
        this.dungeonLength = dungeonLength;
        this.roomOffset = roomOffset;
        this.randomWalkRooms = randomWalkRooms;
        this.corridorWidth = corridorWidth;
    }

    public RoomFirstDungeonGenerator3D(RoomFirst3DParameters roomFirstParameters) {
        super(roomFirstParameters.randomWalkParameters);
        this.minRoomWidth = roomFirstParameters.minRoomWidth;
        this.minRoomLength = roomFirstParameters.minRoomLength;
        this.dungeonWidth = roomFirstParameters.dungeonWidth;
        this.dungeonLength = roomFirstParameters.dungeonLength;
        this.roomOffset = roomFirstParameters.offset;
        this.randomWalkRooms = roomFirstParameters.randomWalkRooms;
    }

    public RoomFirstDungeonGenerator3D() {
    }

    @Override
    public BoundingBox getMaxBounds() {
        return new BoundingBox(startPosition, startPosition.add(dungeonWidth, dungeonHeight, dungeonLength));
    }

    @Override
    protected void runProceduralGeneration(Random random) {
        createRooms(random);
    }

    public void createRooms(Random random) {

        int roomCreatingAttempts = 0;

        do {
            rooms = ProceduralGenerationAlgorithms.binarySpacePartitioning3D(new BoundingBox(startPosition, startPosition.add(dungeonWidth, dungeonHeight, dungeonLength)), minRoomWidth, minRoomHeight, minRoomLength, random);
            roomCreatingAttempts++;
        } while (rooms.isEmpty() && roomCreatingAttempts < 10);

        if (rooms.isEmpty()) {
            System.err.println("Failed to generate valid rooms in " + roomCreatingAttempts + " attempts!");
            return;
        }

        System.out.println("Generated " + rooms.size() + " valid rooms (in " + roomCreatingAttempts + " attempt(s))");

        Map<Vector3Int, BoundingBox> roomCenterToRoomMap = rooms.stream().collect(Collectors.toMap(BoundingBox::get2DCenter, b -> b));

        Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap;
        if (randomWalkRooms) {
            roomCenterToRoomFloorMap = createRoomsRandomly(rooms, random);
        } else {
            roomCenterToRoomFloorMap = CreateSimpleRooms(rooms);
        }
        floorPositions.clear();
        for (var room : rooms) {
            floorPositions.addAll(roomCenterToRoomFloorMap.get(room.get2DCenter()));
        }

        List<Vector3Int> roomCenters = new LinkedList<>();
        for (var room : rooms) {
            roomCenters.add(room.get2DCenter());
        }
        roomCenters = roomCenters.stream().sorted(Comparator.comparing(center -> center.y)).collect(Collectors.toList());

        corridorFloor = connectRooms(rooms, roomCenters, roomCenterToRoomMap, roomCenterToRoomFloorMap, random);

        //TODO create extra paths between rooms on the same level

        stairFloor = createStairs(rooms, corridorFloor, roomCenters, roomCenterToRoomMap, roomCenterToRoomFloorMap, random);

//        floorPositions.addAll(corridorFloors);

        Set<Vector3Int> corridorPositions = corridorFloor.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth - 1)).collect(Collectors.toSet());
        Set<Vector3Int> stairPositions = stairFloor.stream().flatMap(vec -> getStairBox(vec, corridorWidth - 1)).collect(Collectors.toSet());
        corridorPositions.addAll(stairPositions);

        wallPositions = WallGenerator.createWalls(floorPositions, corridorFloor, stairFloor, corridorPositions, rooms, roomOffset, corridorWidth);

        corridorFloor.addAll(stairFloor);

        for (var room : rooms) {
            if (room.get2DFilledBoxPositions().stream().noneMatch(corridorFloor::contains)) {
                notConnectedRooms.add(room);
            }
        }

        List<BoundingBox> connectedRooms = new LinkedList<>(rooms);
        connectedRooms.removeAll(notConnectedRooms);

        int minCenterHeight = connectedRooms.stream().mapToInt(room -> room.get2DCenter().y).min().orElse(0);
        int maxCenterHeight = connectedRooms.stream().mapToInt(room -> room.get2DCenter().y).max().orElse(0);

        startRoom = connectedRooms.stream().filter(room -> room.get2DCenter().y == maxCenterHeight).min(Comparator.comparing(BoundingBox::getVolume)).get();
        bossRoom = connectedRooms.stream().filter(room -> room.get2DCenter().y == minCenterHeight).max(Comparator.comparing(BoundingBox::getVolume)).get();
        var bossRoom2 = connectedRooms.stream().filter(room -> room.get2DCenter().y == minCenterHeight).max(Comparator.comparing(room -> roomCenterToRoomFloorMap.get(room.get2DCenter()).size())).get();
        if (!bossRoom.equals(bossRoom2)) {
            System.out.println("Replaced bossRoom -> diff: " + roomCenterToRoomFloorMap.get(bossRoom.get2DCenter()).size() + " to " + roomCenterToRoomFloorMap.get(bossRoom2.get2DCenter()).size());
            bossRoom = bossRoom2;
        }
    }

    private Map<Vector3Int, Set<Vector3Int>> createRoomsRandomly(List<BoundingBox> rooms, Random random) {
        Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap = new HashMap<>();
        for (var room : rooms) {
            Set<Vector3Int> floor = new LinkedHashSet<>();
            var roomCenter = room.get2DCenter();
            int walkLength = (room.getDimensions().getX() + room.getDimensions().getZ()) / 2 - roomOffset;
//            walkLength = Math.max(room.getDimensions().getX(), room.getDimensions().getZ()) - roomOffset;
            SimpleRandomWalkParameters parameters = new SimpleRandomWalkParameters(walkLength, walkLength, startRandomlyEachIteration);
            var roomFloor = runRandomWalkWithWidth(parameters, roomCenter, shrinkRoomBoxWithOffset(room, roomOffset), random, corridorWidth);
            //TODO maybe make it so that the randomWalker already uses the correct bounds and checks if he is out of bounds
            for (var position : roomFloor) {
                if (position.getX() >= (room.minX + roomOffset) && position.getX() <= (room.maxX - roomOffset)
                        && position.getZ() >= (room.minZ + roomOffset) && position.getZ() <= (room.maxZ - roomOffset)
                        && position.getY() >= (room.minY) && position.getY() <= (room.maxY - (roomOffset * 2))
                ) {
                    floor.add(position);
                }
            }
            roomCenterToRoomFloorMap.put(roomCenter, floor);
        }
        return roomCenterToRoomFloorMap;
    }

    private BoundingBox shrinkRoomBoxWithOffset(BoundingBox room, int offset) {
        // doesn't shrink minY to preserve same 2D center
        return new BoundingBox(
                room.minX + offset, room.minY, room.minZ + offset,
                room.maxX - offset, room.maxY - offset, room.maxZ - offset
        );
    }

    private Set<Vector3Int> runRandomWalkWithWidth(SimpleRandomWalkParameters parameters, Vector3Int position, BoundingBox room, Random random, int pathWidth)
    {
        var currentPosition = position;
        Set<Vector3Int> floorPositions = new LinkedHashSet<>();
        for (int i = 0; i < parameters.iterations(); i++)
        {
            var path = ProceduralGenerationAlgorithms.simpleRandomWalkWithWidth(currentPosition, room, parameters.walkLength(), pathWidth, random);
            floorPositions.addAll(path);
            if (parameters.startRandomlyEachIteration())
                currentPosition = getRandomElement(floorPositions, random);
        }
        return floorPositions;
    }

    private Set<Vector3Int> connectRooms(List<BoundingBox> rooms, List<Vector3Int> roomCenters, Map<Vector3Int, BoundingBox> roomCenterToRoomMap, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap, Random random) {
        Set<Vector3Int> corridors = new LinkedHashSet<>();
        List<Vector3Int> connectedRoomCenters = new LinkedList<>();

        roomCenters = new LinkedList<>(roomCenters);

        var currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
        roomCenters.remove(currentRoomCenter);

        //TODO check if new path overlaps with a room -> take that room as the new start point (to reduce weird corridors)

        int allPaths = 0;

        int complexPaths = 0;
        int failedPaths = 0;

        while (!roomCenters.isEmpty()) {
            boolean validCorridor = false;

            List<Vector3Int> closestList = findClosestPointListTo(currentRoomCenter, roomCenters);

            if (closestList.isEmpty()) {
                currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
                roomCenters.remove(currentRoomCenter);
                continue;
            }

            while (!validCorridor) {
                validCorridor = true;

                Vector3Int closest = closestList.removeFirst();
//                if (closest == null) {
//                    currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
//                    roomCenters.remove(currentRoomCenter);
//                    break;
//                }

                Set<Vector3Int> newCorridor = create2DCorridor(currentRoomCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(currentRoomCenter),roomCenterToRoomMap.get(closest))) {
                    newCorridor = create2DCorridor(closest, currentRoomCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
                }
                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(currentRoomCenter),roomCenterToRoomMap.get(closest))) {
                    Vector3Int newCenter = findClosestPointTo(closest, connectedRoomCenters);
                    System.out.println("    Changed room for overlap (" + currentRoomCenter + " to " + closest + ") -> newStartCenter: " + newCenter);
                    if (newCenter == null) {
                        newCenter = currentRoomCenter;
                        System.out.println("        Couldn't find closer already connected room");
                    }
                    newCorridor = create2DCorridor(newCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
                    if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
                        newCorridor = create2DCorridor(closest, newCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
                    }
                    if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
                        System.err.println("Failed to compute perfect path between " + newCenter + " and " + closest + " , corridor still overlaps with some room -> Trying to create a complex path");
                        Set<Vector3Int> failedCorridor = new LinkedHashSet<>(newCorridor);
                        newCorridor = createComplex2DCorridor(newCenter, closest, failedCorridor, corridors, rooms, roomCenterToRoomFloorMap, roomCenterToRoomMap);

                        if (newCorridor.isEmpty()) {
                            newCorridor = createComplex2DCorridor(closest, newCenter, failedCorridor, corridors, rooms, roomCenterToRoomFloorMap, roomCenterToRoomMap);
                        }

                        if (newCorridor.isEmpty()) {
                            validCorridor = false;

                            if (closestList.isEmpty()) {
                                connectedRoomCenters.add(currentRoomCenter);
                                roomCenters.remove(closest);
                                currentRoomCenter = closest;
                                System.out.println("            Couldn't create a valid path for center: " + currentRoomCenter);
                                failedPaths++;
                                break;
                            }
                            continue;
                        } else {
                            complexPaths++;
                        }
                    }
                }

                connectedRoomCenters.add(currentRoomCenter);
                roomCenters.remove(closest);
                currentRoomCenter = closest;
                corridors.addAll(newCorridor);
                allPaths++;
            }

        }

//        while (!roomCenters.isEmpty()) {
//            Vector3Int closest = findClosestPointTo(currentRoomCenter, roomCenters);
//            if (closest == null) {
//                currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
//                roomCenters.remove(currentRoomCenter);
//                continue;
//            }
//            roomCenters.remove(closest);
//
//            boolean validCorridor = true;
//
//            Set<Vector3Int> newCorridor = create2DCorridor(currentRoomCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
//            if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(currentRoomCenter),roomCenterToRoomMap.get(closest))) {
//                newCorridor = create2DCorridor(closest, currentRoomCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
//            }
//            if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(currentRoomCenter),roomCenterToRoomMap.get(closest))) {
//
//                Vector3Int newCenter = findClosestPointTo(closest, connectedRoomCenters);
//                System.out.println("    Changed room for overlap (" + currentRoomCenter + " to " + closest + ") -> newStartCenter: " + newCenter);
//                if (newCenter == null) {
//                    newCenter = currentRoomCenter;
//                    System.out.println("        Couldn't find closer already connected room");
//                }
//                newCorridor = create2DCorridor(newCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
//                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
//                    newCorridor = create2DCorridor(closest, newCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
//                }
//                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
//                    System.err.println("Failed to compute perfect path between " + newCenter + " and " + closest + " , corridor still overlaps with some room -> Trying to create a complex path");
//                    Set<Vector3Int> failedCorridor = new LinkedHashSet<>(newCorridor);
//                    newCorridor = createComplex2DCorridor(newCenter, closest, failedCorridor, corridors, rooms, roomCenterToRoomFloorMap, roomCenterToRoomMap);
//
//                    if (newCorridor.isEmpty()) {
//                        newCorridor = createComplex2DCorridor(closest, newCenter, failedCorridor, corridors, rooms, roomCenterToRoomFloorMap, roomCenterToRoomMap);
//                    }
//
//                    if (newCorridor.isEmpty()) {
//                        failedPaths++;
//                        validCorridor = false;
//                    } else {
//                        complexPaths++;
//                    }
//                }
//            }
//
//            if (validCorridor) {
//                connectedRoomCenters.add(currentRoomCenter);
//            }
//
//            currentRoomCenter = closest;
//            corridors.addAll(newCorridor);
//            if (!newCorridor.isEmpty()) allPaths++;
//        }


        System.out.println("        Added " + complexPaths + " complexPaths (failed " + failedPaths + ") and overall " + allPaths + " paths");

        return corridors;
    }

    private Set<Vector3Int> createStairs(List<BoundingBox> rooms, Set<Vector3Int> corridorFloor, List<Vector3Int> roomCenters, Map<Vector3Int, BoundingBox> roomCenterToRoomMap, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap, Random random) {
        Set<Vector3Int> stairs = new LinkedHashSet<>();

        Map<Integer, List<Vector3Int>> roomCenterGroups = roomCenters.stream().collect(Collectors.toMap(Vector3Int::getY, v -> new LinkedList<>(List.of(v)), (l1, l2) -> {
            l1.addAll(l2);
            return l1;
        }));

        int stairsAdded = 0;
        int stairsFailed = 0;
        for (var height : roomCenterGroups.keySet().stream().sorted(Comparator.comparing(i -> i)).toList()) {
            List<Vector3Int> centers = roomCenterGroups.get(height);

            List<List<Vector3Int>> otherCentersList = getMapValueListGreaterThan(roomCenterGroups, height);

            while (!otherCentersList.isEmpty()) {
                List<Vector3Int> otherCenters = otherCentersList.removeFirst();
                Vector3Int otherCenter = otherCenters.get(random.nextInt(0, otherCenters.size()));
                Vector3Int currentCenter = findClosestAndOptimalPointTo(otherCenter, roomCenterToRoomMap.get(otherCenter), centers);

                System.out.println("fromCenter: " + currentCenter);
                System.out.println("toCenter: " + otherCenter);

                Set<Vector3Int> allCorridorFloor = new LinkedHashSet<>(corridorFloor);
                allCorridorFloor.addAll(stairs);

                Pair<Set<Vector3Int>, Set<Vector3Int>> pair = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), allCorridorFloor);

                if (pair == null || doesCorridorOverlapWithRooms(rooms, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomMap.get(otherCenter), pair.first())) {
                    System.out.println("    Changed stair-room for overlap");
                    Vector3Int newCenter = findClosestAndOptimalPointTo(currentCenter, roomCenterToRoomMap.get(currentCenter), otherCenters);
                    if (newCenter.equals(otherCenter)) {
                        System.out.println("Failed to compute better stair-room, already best");
                    } else {
                        otherCenter = newCenter;
                    }
                    pair = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), allCorridorFloor);
                    if (pair == null) {
                        System.out.println("                    Failed to create Stairs for room -> Staircase can't be placed from this room!");
                        if (otherCentersList.isEmpty()) {
                            stairsFailed++;
                        }
                        continue;
                    }
                }

                Set<Vector3Int> newStair = pair.first();
                Set<Vector3Int> newCorridor = pair.second();

                stairs.addAll(newStair);
                corridorFloor.addAll(newCorridor);
                stairsAdded++;
                break;
            }

//            List<Vector3Int> otherCenters = getMapValueGreaterThan(roomCenterGroups, height);
//            if (otherCenters == null) continue;
//            Vector3Int otherCenter = otherCenters.get(random.nextInt(0, otherCenters.size()));
//            Vector3Int currentCenter = findClosestAndOptimalPointTo(otherCenter, roomCenterToRoomMap.get(otherCenter), centers);
//
//            System.out.println("fromCenter: " + currentCenter);
//            System.out.println("toCenter: " + otherCenter);
//
//            Set<Vector3Int> newCorridor = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), stairs);
//
//            if (newCorridor == null || doesCorridorOverlapWithRooms(rooms, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomMap.get(otherCenter), newCorridor)) {
//                System.out.println("    Changed stair-room for overlap");
//                Vector3Int newCenter = findClosestAndOptimalPointTo(currentCenter, roomCenterToRoomMap.get(currentCenter), otherCenters);
//                if (newCenter.equals(otherCenter)) {
//                    System.out.println("Failed to compute better stair-room, already best");
//                } else {
//                    otherCenter = newCenter;
//                }
//                newCorridor = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), stairs);
//                if (newCorridor == null) {
//                    System.out.println("                    Failed to create Stairs for room -> Staircase can't be placed from this room!");
//                    stairsFailed++;
//                    continue;
//                }
//            }
//
//            stairs.addAll(newCorridor);
//            stairsAdded++;
        }
        System.out.println("        Added " + stairsAdded + " stairs (failed " + stairsFailed + ")");
        return stairs;
    }

    private Set<Vector3Int> createComplex2DCorridor(Vector3Int currentCenter, Vector3Int targetCenter, Set<Vector3Int> failedCorridor, Set<Vector3Int> corridors, List<BoundingBox> rooms, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap, Map<Vector3Int, BoundingBox> roomCenterToRoomMap) {
        BoundingBox currentRoom = roomCenterToRoomMap.get(currentCenter);
        BoundingBox targetRoom = roomCenterToRoomMap.get(targetCenter);

        List<BoundingBox> overlappingRooms = getOverlappingRoomsWithCorridor(rooms, failedCorridor, currentRoom, targetRoom);

        Vector3Int offset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);

        Set<Vector3Int> corridor = new LinkedHashSet<>();

        int directionsTried = 0;
        Direction3D currentDirection = Direction3D.SOUTH;
        while (directionsTried < 4) {
            Vector3Int currentPos;
            Direction3D lastDirection = currentDirection;
            corridor.clear();
            System.out.println("Testing direction: " + currentDirection);
            currentPos = getVecOnRoomEdgeFromCenter(currentCenter, currentRoom, roomCenterToRoomFloorMap.get(currentCenter), currentDirection);
            System.out.println("    currentPos: " + currentPos);
            // add offset to dont intersect with owm room
            int startDirectionOffset = offset.mul(currentDirection.toVector3Int()).sum();
            int roomEdgeOffset = getDistanceToRoomEdgeFromCenter(currentCenter, currentRoom, roomCenterToRoomFloorMap.get(currentCenter), currentDirection) * currentDirection.toVector3Int().sum();
            int offsetForCorridorSpace = corridorWidth + 1;

            int startDirectionDistance = startDirectionOffset - roomEdgeOffset + offsetForCorridorSpace;

            addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
            for (var i = 0; i < Math.max(offsetForCorridorSpace, startDirectionDistance); i++) {
                currentPos = currentDirection.apply(currentPos);
                addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
            }
            addCorridorAroundPoint(corridor, currentPos, corridorWidth);
//            System.out.println("    startDirectionDistance: " + (startDirectionDistance));
//            System.out.println("    currentPos: " + currentPos);

            Direction3D secondDir = Direction3D.getDirectionForVec(currentDirection.getInverseVec().mul(offset));
            if (secondDir != null) {
                int secondDirectionOffset = offset.mul(secondDir.toVector3Int()).sum();
//                roomEdgeOffset = getDistanceToRoomEdgeFromCenter(currentCenter, currentRoom, secondDir);
                for (var i = 0; i < Math.max(offsetForCorridorSpace, offsetForCorridorSpace + secondDirectionOffset); i++) {
                    currentPos = secondDir.apply(currentPos);
                    addCorridorAroundPoint(corridor, currentPos, corridorWidth);
                }
//                System.out.println("    secondDir: " + secondDir);
//                System.out.println("    offsetForCorridorSpace: " + (offsetForCorridorSpace));
//                System.out.println("    secondDirectionOffset: " + (secondDirectionOffset));
//                System.out.println("    currentPos: " + currentPos);
                lastDirection = secondDir;
            }

            //TODO maybe also update position in firstDirection if startDirectionOffset was negative -> couldnt go in the firstDirection but now possible
            if (startDirectionDistance < 0) {
                Direction3D thirdDir = currentDirection.opposite();
                int centerOffset = currentCenter.sub(currentPos).mul(thirdDir.getAbsoluteVec()).sum();
                int distance = startDirectionOffset*-1 + centerOffset + offsetForCorridorSpace;
                for (var i = 0; i < distance; i++) {
                    currentPos = thirdDir.apply(currentPos);
                    addCorridorAroundPoint(corridor, currentPos, corridorWidth);
                }
//                System.out.println("            thirdDir: " + thirdDir);
//                System.out.println("            distance: " + (distance));
//                System.out.println("            startDirectionOffset: " + (startDirectionOffset*-1));
//                System.out.println("            centerOffset: " + (centerOffset));
//                System.out.println("            roomEdgeOffset: " + (roomEdgeOffset));
//                System.out.println("            offsetForCorridorSpace: " + (offsetForCorridorSpace));
//                System.out.println("            currentPos: " + currentPos);
                lastDirection = thirdDir;
            }

//            if (!doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
                System.out.println("    Calculating corridor extension to target room");
                Set<Vector3Int> endCorridor = createCorridorFromStair(currentPos, targetCenter, lastDirection);
                corridor.addAll(endCorridor);
                if (doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
                    System.out.println("        Failed corridor extension to target room, adding new overlapping rooms to offset and trying again");
                    overlappingRooms.addAll(getOverlappingRoomsWithCorridor(rooms, corridor, currentRoom, targetRoom));
                    Vector3Int newOffset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);

//                        System.out.println("    oldOffset: " + offset + " -> " + "newOffset: " + newOffset);
                    if (newOffset.equals(offset)) {
                        System.out.println("        Failed corridor extension to target room -> something went wrong");
                    } else {
                        offset = newOffset;
                        continue;
                    }
                } else {
                    System.out.println(" -> Success for direction: " + currentDirection);
                    return corridor;
                }
//            }


//            if (startDirectionOffset >= 0) {
//                addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                for (var i = 0; i < corridorWidth + startDirectionOffset; i++) {
//                    currentPos = currentDirection.apply(currentPos);
//                    addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                }
//                System.out.println("    startDirectionOffset: " + (corridorWidth + startDirectionOffset));
//                System.out.println("    currentPos: " + currentPos);
//
//                Direction3D secondDir = Direction3D.getDirectionForVec(currentDirection.getInverseVec().mul(offset));
//                if (secondDir != null) {
//                    int secondDirectionOffset = offset.mul(secondDir.toVector3Int()).sum();
//                    for (var i = 0; i < corridorWidth + secondDirectionOffset; i++) {
//                        currentPos = secondDir.apply(currentPos);
//                        addCorridorAroundPoint(corridor, currentPos, corridorWidth);
//                    }
//                    System.out.println("    secondDir: " + secondDir);
//                    System.out.println("    secondDirectionOffset: " + (corridorWidth + secondDirectionOffset));
//                    System.out.println("    currentPos: " + currentPos);
//                }
//                if (!doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
//                    System.out.println("    Calculating corridor extension to target room");
//                    lastDirection = secondDir == null ? currentDirection : secondDir;
//                    Set<Vector3Int> endCorridor = createCorridorFromStair(currentPos, targetCenter, lastDirection);
//                    corridor.addAll(endCorridor);
//                    if (doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
//                        System.out.println("        Failed corridor extension to target room, adding new overlapping rooms to offset and trying again");
//                        overlappingRooms.addAll(getOverlappingRoomsWithCorridor(rooms, corridor, currentRoom, targetRoom));
//                        Vector3Int newOffset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);
//
////                        System.out.println("    oldOffset: " + offset + " -> " + "newOffset: " + newOffset);
//                        if (newOffset.equals(offset)) {
//                            System.out.println("        Failed corridor extension to target room -> something went wrong");
//                            break;
//                        }
//                        offset = newOffset;
//                        continue;
//                    }
//                    System.out.println(" -> Success for direction: " + currentDirection);
//                    return corridor;
//                }
//            } else {
//                addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                for (var i = 0; i < corridorWidth; i++) {
//                    currentPos = currentDirection.apply(currentPos);
//                    addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                }
//                System.out.println("    Calculating (simple) corridor extension to target room");
//                Set<Vector3Int> endCorridor = createCorridorFromStair(currentPos, targetCenter, currentDirection);
//                corridor.addAll(endCorridor);
//                if (doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
//                    System.out.println("        Failed corridor extension to target room, adding new overlapping rooms to offset and trying again");
//                    overlappingRooms.addAll(getOverlappingRoomsWithCorridor(rooms, corridor, currentRoom, targetRoom));
//                    Vector3Int newOffset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);
////                        System.out.println("    oldOffset: " + offset + " -> " + "newOffset: " + newOffset);
//                    if (newOffset.equals(offset)) {
//                        System.out.println("        Failed corridor extension to target room -> something went wrong");
//                        break;
//                    }
//                    offset = newOffset;
//                    continue;
//                }
//                System.out.println(" -> Success for direction: " + currentDirection);
//                return corridor;
//            }

            System.out.println(" -> Failed for direction: " + currentDirection);

            overlappingRooms = getOverlappingRoomsWithCorridor(rooms, failedCorridor, currentRoom, targetRoom);
            offset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);

            currentDirection = currentDirection.rotateToRight();
            directionsTried++;

            corridor.clear();
        }

        return corridor;

//        for (var direction : Direction3D.get2DCardinalDirections()) {
//            System.out.println("Testing direction: " + direction);
//            currentPos = getVecOnRoomEdgeFromCenter(currentCenter, currentRoom, direction);
//            System.out.println("currentPos: " + currentPos);
//            // add offset to dont intersect with owm room
//            int startDirectionOffset = offset.mul(direction.toVector3Int()).sum();
//            if (startDirectionOffset < 0) continue;
//            addStairCorridorAroundPoint(corridor, currentPos, direction, corridorWidth);
//            for (var i = 0; i < corridorWidth + startDirectionOffset; i++) {
//                currentPos = direction.apply(currentPos);
//                addStairCorridorAroundPoint(corridor, currentPos, direction, corridorWidth);
//            }
//            System.out.println("startDirectionOffset: " + (corridorWidth + startDirectionOffset));
//            System.out.println("currentPos: " + currentPos);
//
//            Direction3D secondDir = Direction3D.getDirectionForVec(direction.getInverseVec().mul(offset));
//            if (secondDir != null) {
//                int secondDirectionOffset = offset.mul(secondDir.toVector3Int()).sum();
//                for (var i = 0; i < corridorWidth + secondDirectionOffset; i++) {
//                    currentPos = secondDir.apply(currentPos);
//                    addCorridorAroundPoint(corridor, currentPos, corridorWidth);
//                }
//                System.out.println("secondDir: " + secondDir);
//                System.out.println("secondDirectionOffset: " + (corridorWidth + secondDirectionOffset));
//                System.out.println("currentPos: " + currentPos);
//            }
//
//            if (!doesCorridorOverlapWithRooms(rooms, corridor, currentRoom, targetRoom)) {
//                lastDirection = secondDir == null ? direction : secondDir;
//                break;
//            }
//
//            System.out.println("Failed for direction: " + direction);
//
//            corridor.clear();
//        }
//
//        if (lastDirection == null) {
//            throw new RuntimeException("No direction was found that could create a valid path!");
//        }
    }

    private Vector3Int getOverlappingRoomsOffsetVec(Vector3Int currentCenter, BoundingBox currentRoom, List<BoundingBox> overlappingRooms) {
        System.out.println("overlappingRooms: " + overlappingRooms);

        Vector3Int offset = Vector3Int.ZERO;
        Vector3Int ignoreHeightVec = new Vector3Int(1,0,1);

        BoundingBox mergedOverlappingRoom = BoundingBox.merge(overlappingRooms.toArray(BoundingBox[]::new));

        System.out.println("mergedOverlappingRoom: " + mergedOverlappingRoom + " -> center: " + mergedOverlappingRoom.get2DCenter() + " dim: " + mergedOverlappingRoom.getDimensions());

        Vector3Int centerDiff = mergedOverlappingRoom.get2DCenter().sub(currentCenter).mul(ignoreHeightVec);
        Vector3Int dimensionDiff = mergedOverlappingRoom.getDimensions().sub(currentRoom.getDimensions()).mul(ignoreHeightVec);
        int dimensionXOffest = 0;
//        if (dimensionDiff.x > 0) {
//            dimensionXOffest = (int) (Math.abs(mergedOverlappingRoom.getDimensions().mul(0.5).x) * Math.signum(centerDiff.x));
//        }
        int dimensionZOffest = 0;
//        if (dimensionDiff.z > 0) {
//            dimensionZOffest = (int) (Math.abs(mergedOverlappingRoom.getDimensions().mul(0.5).z) * Math.signum(centerDiff.z));
//        }

//        dimensionXOffest = (Math.max(mergedOverlappingRoom.getDimensions().x, currentRoom.getDimensions().x) / 2) * ((int) Math.signum(centerDiff.x));
//        dimensionZOffest = (Math.max(mergedOverlappingRoom.getDimensions().z, currentRoom.getDimensions().z) / 2) * ((int) Math.signum(centerDiff.z));
//        dimensionXOffest = (mergedOverlappingRoom.getDimensions().x / 2) * ((int) Math.signum(centerDiff.x));
//        dimensionZOffest = (mergedOverlappingRoom.getDimensions().z / 2) * ((int) Math.signum(centerDiff.z));

        dimensionXOffest = getDimensionOffsetValue(mergedOverlappingRoom.getDimensions().x, centerDiff.x);
        dimensionZOffest = getDimensionOffsetValue(mergedOverlappingRoom.getDimensions().z, centerDiff.z);

//        System.out.println("centerDiff: " + centerDiff);
//        System.out.println("dimensionDiff: " + dimensionDiff + " -> " + mergedOverlappingRoom.getDimensions().mul(0.5) + " => x: " + dimensionXOffest + " z: " + dimensionZOffest);
        offset = offset.add(centerDiff);
        offset = offset.add(new Vector3Int(dimensionXOffest, 0, dimensionZOffest));


//        for (var room : overlappingRooms) {
//            Vector3Int centerDiff = room.get2DCenter().sub(currentCenter).mul(ignoreHeightVec);
//            Vector3Int dimensionDiff = room.getDimensions().sub(currentRoom.getDimensions()).mul(ignoreHeightVec);
//            int dimensionXOffest = 0;
////            if (dimensionDiff.x > 0) {
////                dimensionXOffest = (int) (Math.abs(room.getDimensions().mul(0.5).x) * Math.signum(centerDiff.x));
////            }
//            int dimensionZOffest = 0;
////            if (dimensionDiff.z > 0) {
////                dimensionZOffest = (int) (Math.abs(room.getDimensions().mul(0.5).z) * Math.signum(centerDiff.z));
////            }
//
//            dimensionXOffest = (Math.max(room.getDimensions().x, currentRoom.getDimensions().x) / 2) * ((int) Math.signum(centerDiff.x));
//            dimensionZOffest = (Math.max(room.getDimensions().z, currentRoom.getDimensions().z) / 2) * ((int) Math.signum(centerDiff.z));
//
//            System.out.println("centerDiff: " + centerDiff);
//            System.out.println("dimensionDiff: " + dimensionDiff + " -> " + room.getDimensions().mul(0.5) + " => x: " + dimensionXOffest + " z: " + dimensionZOffest);
//            offset = offset.add(centerDiff);
//            offset = offset.add(new Vector3Int(dimensionXOffest, 0, dimensionZOffest));
//        }

        System.out.println("offset: " + offset);
        return offset;
    }

    private int getDimensionOffsetValue(int dimensionX, int centerX) {
        int sign = ((int) Math.signum(centerX));
        int offset = (dimensionX / 2) * sign;
        if (dimensionX % 2 != 0 && offset < 0) {
            offset--;
        }
        return offset;
    }

    private List<BoundingBox> getOverlappingRoomsWithCorridor(List<BoundingBox> rooms, Set<Vector3Int> corridor, BoundingBox ... ignoredRooms) {
        Set<Vector3Int> finalCorridor = corridor.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth)).collect(Collectors.toSet());
        List<BoundingBox> updatedRooms = new LinkedList<>(rooms);
        updatedRooms.removeAll(Arrays.asList(ignoredRooms));
        return updatedRooms.stream().filter(b -> finalCorridor.stream().anyMatch(b::contains)).collect(Collectors.toList());
    }

    private Pair<Set<Vector3Int>, Set<Vector3Int>> create3DCorridor(Vector3Int currentCenter, BoundingBox currentRoom, Set<Vector3Int> currentRoomFloor, Vector3Int targetCenter, BoundingBox targetRoom, Set<Vector3Int> corridors) {
        Set<Vector3Int> stairs = new LinkedHashSet<>();
        Set<Vector3Int> corridor = new LinkedHashSet<>();
        Vector3Int distanceVec = targetCenter.sub(currentCenter);
        int stairLength = distanceVec.getY();
        double distanceToTargetCenter = Double.MAX_VALUE;
        Direction3D optimalStartDirection = null;
        for (var direction : Direction3D.get2DCardinalDirections()) {
            Vector3Int startVec = getVecOnRoomEdgeFromCenter(currentCenter, currentRoom, currentRoomFloor, direction);
            Vector3Int endVec = direction.applyAndUp(startVec, stairLength);

            Set<Vector3Int> stairCorridor = createStairCorridor(currentCenter, currentRoom, currentRoomFloor, targetCenter, direction);

            if (doesCorridorOverlapWithRooms(rooms, stairCorridor, currentRoom, targetRoom)) continue; // TODO maybe not allow targetRoom to be overlapped with stair ?
            if (doesStairOverlapWithCorridors(corridors, stairCorridor)) continue;

            Set<Vector3Int> restCorridor = new LinkedHashSet<>();

            Vector3Int startPos = endVec;
            addStairCorridorAroundPoint(restCorridor, startPos, direction, corridorWidth);
            for (var i = 0; i < corridorWidth; i++) {
                startPos = direction.apply(startPos);
                addStairCorridorAroundPoint(restCorridor, startPos, direction, corridorWidth);
            }

            int endDistance = corridorWidth/2 + 1;
            if (corridorWidth % 2 == 0 && direction.toVector3Int().sum() > 0) endDistance--;
            endVec = direction.apply(endVec, endDistance);

            restCorridor.addAll(createCorridorFromStair(endVec, targetCenter, direction));

            if (doesCorridorOverlapWithRooms(rooms, restCorridor, currentRoom, targetRoom)) continue; //TODO check if corridor overlaps with the currentRoom, goes through it
            if (doesCorridorOverlapWithCorridors(corridors, restCorridor)) continue;

//            if (targetRoom.contains(endVec)) continue;
            double distance = endVec.distance(targetCenter);
            if (distance < distanceToTargetCenter) {
                distanceToTargetCenter = distance;
                optimalStartDirection = direction;

                stairs = stairCorridor;
                corridor = restCorridor;
            }
        }
        if (optimalStartDirection == null) return null;

        return new Pair<>(stairs, corridor);
    }

    private Set<Vector3Int> createCorridorFromStair(Vector3Int currentPos, Vector3Int targetCenter, Direction3D direction) {
        Set<Vector3Int> corridor = new LinkedHashSet<>();

        Vector3Int newDistanceVec = targetCenter.sub(currentPos);

//        System.out.println("    currentPos: " + currentPos);
//        System.out.println("    targetCenter: " + targetCenter);
//        System.out.println("    NewDistance: " + newDistanceVec);

        int distanceValue = newDistanceVec.mul(direction.getAbsoluteVec()).sum();
        int directionValue = direction.toVector3Int().sum();

        Direction3D firstDirection;
        Direction3D secondDirection;
        if ((directionValue < 0 && distanceValue < 0) || (directionValue > 0 && distanceValue > 0)) {
            firstDirection = direction;
            secondDirection = Direction3D.getDirectionForVec(newDistanceVec.mul(direction.getInverseVec()));
        } else {
            firstDirection = Direction3D.getDirectionForVec(newDistanceVec.mul(direction.getInverseVec()));
//            if (firstDirection == null) throw new RuntimeException("Something went wrong with calculating the next stair direction!");
            secondDirection = direction.opposite();
        }

        int firstDirectionCount = firstDirection == null ? 0 : Math.abs(newDistanceVec.mul(firstDirection.toVector3Int()).sum());
        int secondDirectionCount = secondDirection == null ? 0 : Math.abs(newDistanceVec.mul(secondDirection.toVector3Int()).sum());

        if (firstDirection != direction) {
            if (firstDirection == null) {
                firstDirection = secondDirection.rotateToRight();
            }
            firstDirectionCount = Math.max(corridorWidth + 1, firstDirectionCount);
        }

//        System.out.println("        firstDirection: " + firstDirection + " -> " + firstDirectionCount);
//        System.out.println("        secondDirection: " + secondDirection + " -> " + secondDirectionCount);
//
//        System.out.println("        currentPos: " + currentPos);

        for (int i = 0; i < firstDirectionCount; i++) {
            currentPos = firstDirection.apply(currentPos);
            addCorridorAroundPoint(corridor, currentPos, corridorWidth);
        }

//        System.out.println("        currentPos: " + currentPos);

        for (int i = 0; i < secondDirectionCount; i++) {
            currentPos = secondDirection.apply(currentPos);
            addCorridorAroundPoint(corridor, currentPos, corridorWidth);
        }

//        System.out.println("        currentPos: " + currentPos);

        return corridor;
    }

    private Set<Vector3Int> createStairCorridor(Vector3Int currentCenter, BoundingBox currentRoom, Set<Vector3Int> currentRoomFloor, Vector3Int targetCenter, Direction3D direction) {
        Set<Vector3Int> corridor = new LinkedHashSet<>();
        Vector3Int startPos = getVecOnRoomEdgeFromCenter(currentCenter, currentRoom, currentRoomFloor, direction);
        while (startPos.getY() != targetCenter.getY()) {
            addStairCorridorAroundPoint(corridor, startPos, direction, corridorWidth);
            startPos = direction.applyAndUp(startPos);
        }
//        addStairCorridorAroundPoint(corridor, startPos, direction, corridorWidth);
//        for (var i = 0; i < corridorWidth; i++) {
//            startPos = direction.apply(startPos);
//            addStairCorridorAroundPoint(corridor, startPos, direction, corridorWidth);
//        }
//        startPos = direction.apply(startPos);
//        addCorridorAroundPoint(corridor, startPos, corridorWidth);

        return corridor;
    }


    private Vector3Int getVecOnRoomEdgeFromCenter(Vector3Int center, BoundingBox room, Set<Vector3Int> roomFloor, Direction3D direction) {
        Vector3Int v1 = room.getDimensions().mul(direction.toVector3Int()).mul(0.5);
        Vector3Int v2 = direction.apply(center.add(v1));
        if (room.contains(v2)) {
            v2 = direction.apply(v2);
        }
        v2 = direction.apply(v2, -roomOffset);

        if (randomWalkRooms) {
            while (!isStairCorridorPositionConnectedToRoom(v2, direction, roomFloor) && !v2.equals(center)) {
                v2 = direction.opposite().apply(v2);
            }
        }

        return v2;
    }

    private int getDistanceToRoomEdgeFromCenter(Vector3Int center, BoundingBox room, Set<Vector3Int> roomFloor, Direction3D direction) {
        return getVecOnRoomEdgeFromCenter(center, room, roomFloor, direction).sub(center).sum();
    }

    private Vector3Int findClosestAndOptimalPointTo(Vector3Int currentCenter, BoundingBox currentRoom, List<Vector3Int> centers) {
        Vector3Int closest = null;
        double length = Float.MAX_VALUE;
        for (var position : centers) {
            double currentDistance = currentCenter.distance2D(position);
            if (currentRoom.contains2D(position)) {
                if (closest == null) closest = position;
                continue;
            }
            if (currentDistance < length) {
                length = currentDistance;
                closest = position;
            }
        }
        return closest;
    }

    private List<Vector3Int> getMapValueGreaterThan(Map<Integer, List<Vector3Int>> map, int key) {
        for (var k : map.keySet().stream().sorted(Comparator.comparing(i -> i)).toList()) {
            if (k > key) {
                return map.get(k);
            }
        }
        return null;
    }

    private List<List<Vector3Int>> getMapValueListGreaterThan(Map<Integer, List<Vector3Int>> map, int key) {
        List<List<Vector3Int>> list = new LinkedList<>();
        for (var k : map.keySet().stream().sorted(Comparator.comparing(i -> i)).toList()) {
            if (k > key) {
                list.add(map.get(k));
            }
        }
        return list;
    }

    private boolean doesCorridorOverlapWithAnything(Set<Vector3Int> corridor, Set<Vector3Int> corridors, List<BoundingBox> rooms, BoundingBox ... ignoredRooms) {
        return doesCorridorOverlapWithCorridors(corridors, corridor) || doesCorridorOverlapWithRooms(rooms, corridor, ignoredRooms);
    }

    private boolean doesCorridorOverlapWithRooms(List<BoundingBox> rooms, BoundingBox room1, BoundingBox room2, Set<Vector3Int> corridor) {
        return rooms.stream().filter(b -> !b.equals(room1) && !b.equals(room2)).anyMatch(b -> corridor.stream().anyMatch(b::contains));
    }

    private boolean doesCorridorOverlapWithRooms(List<BoundingBox> rooms, Set<Vector3Int> corridor, BoundingBox ... ignoredRooms) {
        Set<Vector3Int> finalCorridor = corridor.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth)).collect(Collectors.toSet());
        List<BoundingBox> updatedRooms = new LinkedList<>(rooms);
        updatedRooms.removeAll(Arrays.asList(ignoredRooms));
        return updatedRooms.stream().anyMatch(b -> finalCorridor.stream().anyMatch(b::contains));
    }

    private Stream<Vector3Int> getCorridorBox(Vector3Int vec, int corridorWidth) {
        List<Vector3Int> list = new LinkedList<>();
        int height = Math.max(2, corridorWidth) + 1;
        for (var i = 0; i <= height; i++) {
            list.add(vec.add(0,i,0));
        }
        return list.stream();
    }

    private boolean doesStairOverlapWithCorridors(Set<Vector3Int> corridors, Set<Vector3Int> stair) {
        corridors = corridors.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth)).collect(Collectors.toSet());
        stair = stair.stream().flatMap(vec -> getStairBox(vec, corridorWidth)).collect(Collectors.toSet());
        return corridors.stream().anyMatch(stair::contains);
    }

    private boolean doesCorridorOverlapWithCorridors(Set<Vector3Int> corridors, Set<Vector3Int> corridor) {
        //TODO currently removing same floor positions to allow for paths to cross -> do we want that?
        Set<Vector3Int> updatedCorridor = new LinkedHashSet<>(corridors);
        updatedCorridor.removeAll(corridor);

        updatedCorridor = updatedCorridor.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth)).collect(Collectors.toSet());
        corridor = corridor.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth)).collect(Collectors.toSet());
        return updatedCorridor.stream().anyMatch(corridor::contains);
    }

    private Stream<Vector3Int> getStairBox(Vector3Int vec, int corridorWidth) {
        List<Vector3Int> list = new LinkedList<>();
        int height = Math.max(3, corridorWidth + 1) + 1;
        for (var i = 0; i <= height; i++) {
            list.add(vec.add(0,i,0));
        }
        return list.stream();
    }

    private boolean doesVecOverlapWithRooms(List<BoundingBox> rooms, BoundingBox room, Vector3Int vec) {
        return rooms.stream().filter(b -> !b.equals(room)).anyMatch(b -> b.contains(vec));
    }


    private Set<Vector3Int> create2DCorridor(Vector3Int currentRoomCenter, Vector3Int destination, Map<Vector3Int, BoundingBox> roomCenterToRoomMap, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap) {
        //TODO improve the placement logic to also allow for complexer (and smarter) shapes

        if (currentRoomCenter.equals(new Vector3Int(7, 64, 7)) || destination.equals(new Vector3Int(7, 64, 7))){
            int i = 0;
        }

        Set<Vector3Int> corridor = new LinkedHashSet<>();
        var position = currentRoomCenter;
//        Direction3D direction = null;
//        while (position.getZ() != destination.getZ() && !isCorridorPositionConnectedToRoom(position, direction, roomCenterToRoomFloorMap.get(destination))) {
//            if (destination.getZ() > position.getZ()) {
//                direction = Direction3D.SOUTH;
//            } else {
//                direction = Direction3D.NORTH;
//            }
//            if (position.equals(currentRoomCenter)) {
//                position = getVecOnRoomEdgeFromCenter(currentRoomCenter, roomCenterToRoomMap.get(currentRoomCenter), roomCenterToRoomFloorMap.get(currentRoomCenter), direction);
//                continue;
//            }
//            position = direction.apply(position);
//            addStairCorridorAroundPoint(corridor, position, direction, corridorWidth);
//        }
//        if (!isCorridorPositionConnectedToRoom(position, direction, roomCenterToRoomFloorMap.get(destination))) {
//            addCorridorAroundPoint(corridor, position, corridorWidth);
//        }
//        while (position.getX() != destination.getX() && !isCorridorPositionConnectedToRoom(position, direction, roomCenterToRoomFloorMap.get(destination))) {
//            if (destination.getX() > position.getX()) {
//                direction = Direction3D.EAST;
//            } else {
//                direction = Direction3D.WEST;
//            }
//            if (position.equals(currentRoomCenter)) {
//                position = getVecOnRoomEdgeFromCenter(currentRoomCenter, roomCenterToRoomMap.get(currentRoomCenter), roomCenterToRoomFloorMap.get(currentRoomCenter), direction);
//                continue;
//            }
//            position = direction.apply(position);
//            addStairCorridorAroundPoint(corridor, position, direction, corridorWidth);
//        }
//        if (!isCorridorPositionConnectedToRoom(position, direction, roomCenterToRoomFloorMap.get(destination))) {
//            addCorridorAroundPoint(corridor, position, corridorWidth);
//        }



        Vector3Int distanceVec = destination.sub(position);

        Direction3D firstDirection;
        Direction3D secondDirection;
        if (Math.abs(distanceVec.x) > Math.abs(distanceVec.z)) {
            firstDirection = Direction3D.getDirectionForVec(distanceVec.mul(new Vector3Int(1,0,0)));
            secondDirection = Direction3D.getDirectionForVec(distanceVec.mul(new Vector3Int(0,0,1)));
        } else {
            firstDirection = Direction3D.getDirectionForVec(distanceVec.mul(new Vector3Int(0,0,1)));
            secondDirection = Direction3D.getDirectionForVec(distanceVec.mul(new Vector3Int(1,0,0)));
        }

        int firstDirectionCount = firstDirection == null ? 0 : Math.abs(distanceVec.mul(firstDirection.toVector3Int()).sum());
        int secondDirectionCount = secondDirection == null ? 0 : Math.abs(distanceVec.mul(secondDirection.toVector3Int()).sum());

        if (firstDirectionCount > 0) {
            position = getVecOnRoomEdgeFromCenter(currentRoomCenter, roomCenterToRoomMap.get(currentRoomCenter), roomCenterToRoomFloorMap.get(currentRoomCenter), firstDirection);
            firstDirectionCount -= Math.abs(position.sub(currentRoomCenter).sum());
        } else if (secondDirectionCount > 0) {
            position = getVecOnRoomEdgeFromCenter(currentRoomCenter, roomCenterToRoomMap.get(currentRoomCenter), roomCenterToRoomFloorMap.get(currentRoomCenter), secondDirection);
        }

        for (int i = 0; i < firstDirectionCount && !isCorridorPositionConnectedToRoom(position, firstDirection, roomCenterToRoomFloorMap.get(destination)); i++) {
            position = firstDirection.apply(position);
            addStairCorridorAroundPoint(corridor, position, firstDirection, corridorWidth);
        }

        if (firstDirectionCount > 0) {
            addCorridorAroundPoint(corridor, position, corridorWidth);
        }

        if (!isCorridorPositionConnectedToRoom(position, firstDirection, roomCenterToRoomFloorMap.get(destination))) {
            for (int i = 0; i < secondDirectionCount && !isCorridorPositionConnectedToRoom(position, secondDirection, roomCenterToRoomFloorMap.get(destination)); i++) {
                position = secondDirection.apply(position);
                addStairCorridorAroundPoint(corridor, position, secondDirection, corridorWidth);
            }

            if (secondDirectionCount > 0) {
                addCorridorAroundPoint(corridor, position, corridorWidth);
            }

            if (firstDirectionCount < 0) {
                firstDirectionCount *= -1;
                firstDirection = firstDirection.opposite();
                for (int i = 0; i < firstDirectionCount && !isCorridorPositionConnectedToRoom(position, firstDirection, roomCenterToRoomFloorMap.get(destination)); i++) {
                    position = firstDirection.apply(position);
                    addStairCorridorAroundPoint(corridor, position, firstDirection, corridorWidth);
                }
                addCorridorAroundPoint(corridor, position, corridorWidth);
            }
        }

        return corridor;
    }

    private boolean isCorridorPositionConnectedToRoom(Vector3Int position, Direction3D direction, Set<Vector3Int> roomFloor) {
        if (direction == null) return false;
        Set<Vector3Int> corridor = new LinkedHashSet<>();

        int corridorOffset = corridorWidth / 2 + 1;
        if (corridorWidth % 2 == 0 && direction.toVector3Int().sum() < 0) {
            corridorOffset--;
        }
        for (int i = 0; i < corridorOffset; i++) {
            position = direction.apply(position);
        }

        addStairCorridorAroundPoint(corridor, position, direction, corridorWidth);
        for (var pos : corridor) {
            if (!roomFloor.contains(pos)) return false;
        }
        return true;
    }

    private boolean isStairCorridorPositionConnectedToRoom(Vector3Int position, Direction3D direction, Set<Vector3Int> roomFloor) {
        if (direction == null) return false;
        Set<Vector3Int> corridor = new LinkedHashSet<>();
        addStairCorridorAroundPoint(corridor, position, direction, corridorWidth);
        for (var pos : corridor) {
            if (!roomFloor.contains(pos)) return false;
        }
        return true;
    }

    public static void addCorridorAroundPoint(Set<Vector3Int> corridor, Vector3Int position, int corridorWidth) {
        corridor.add(position);
        int i = (corridorWidth - 1) / 2;
        for (int x = -i; x < corridorWidth - i; x++) {
            for (int z = -i; z < corridorWidth - i; z++) {
                corridor.add(position.add(x, 0, z));
            }
        }
    }

    private void addStairCorridorAroundPoint(Set<Vector3Int> corridor, Vector3Int position, Direction3D direction, int corridorWidth) {
        int xOffset = direction.toVector3Int().x;
        int zOffset = direction.toVector3Int().z;
        int xOffsetStart = Math.max(xOffset, 0);
        int xOffsetEnd = Math.min(xOffset, 0);
        int zOffsetStart = Math.max(zOffset, 0);
        int zOffsetEnd = Math.min(zOffset, 0);

//        corridor.add(position);
        int i = (corridorWidth - 1) / 2;
//        for (int x = -i + xOffsetStart; x < corridorWidth - i + xOffsetEnd; x++) {
//            for (int z = -i + zOffsetStart; z < corridorWidth - i + zOffsetEnd; z++) {
//                corridor.add(position.add(x, 0, z));
//            }
//        }

        //add vec to left and right and the same in the direction
        List<Direction3D> directions = Direction3D.get2DCardinalDirections();
        directions.removeAll(List.of(direction, direction.opposite()));

        if (direction.toVector3Int().z != 0) {
            for (int x = -i; x < corridorWidth - i; x++) {
                corridor.add(position.add(x, 0, 0));
            }
        } else {
            for (int z = -i; z < corridorWidth - i; z++) {
                corridor.add(position.add(0, 0, z));
            }
        }
    }

    private Vector3Int findClosestPointTo(Vector3Int currentRoomCenter, List<Vector3Int> roomCenters) {
        Vector3Int closest = null;
        double length = Double.MAX_VALUE;
        for (var position : roomCenters) {
            if (position.getY() != currentRoomCenter.getY()) continue;
            double currentDistance = currentRoomCenter.distance(position);
            if (currentDistance < length) {
                length = currentDistance;
                closest = position;
            }
        }
        return closest;
    }

    private List<Vector3Int> findClosestPointListTo(Vector3Int currentRoomCenter, List<Vector3Int> roomCenters) {
        return roomCenters.stream().filter(vec -> vec.y == currentRoomCenter.y).sorted(Comparator.comparing(currentRoomCenter::distance)).collect(Collectors.toList());
    }

    private Map<Vector3Int, Set<Vector3Int>> CreateSimpleRooms(List<BoundingBox> roomList) {
        Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap = new HashMap<>();
        for (var room : roomList) {
            Set<Vector3Int> floor = new LinkedHashSet<>();
            for (int col = roomOffset; col <= room.getDimensions().getX() - roomOffset; col++) {
                for (int row = roomOffset; row <= room.getDimensions().getZ() - roomOffset; row++) {
                    Vector3Int position = room.getMinPoint().add(col, 0, row);
                    floor.add(position);
                }
            }
            roomCenterToRoomFloorMap.put(room.get2DCenter(), floor);
        }
        return roomCenterToRoomFloorMap;
    }


    public static void main(String[] args) {
        int minRoomWidth = Integer.parseInt(args[1]);
        int minRoomHeight = Integer.parseInt(args[2]);
        int minRoomLength = Integer.parseInt(args[3]);
        int dungeonWidth = Integer.parseInt(args[4]);
        int dungeonHeight = Integer.parseInt(args[5]);
        int dungeonLength = Integer.parseInt(args[6]);

        int roomOffset = Integer.parseInt(args[7]);
        boolean randomWalkRooms = Boolean.parseBoolean(args[8]);
        int corridorWidth = Integer.parseInt(args[9]);

        int iterations = Integer.parseInt(args[10]);
        int walkLength = Integer.parseInt(args[11]);
        boolean startRandomlyEachIteration = Boolean.parseBoolean(args[12]);

        minRoomWidth = 10;
        minRoomHeight = 10;
        minRoomLength = 10;
        dungeonWidth = 200;
        dungeonHeight = 10;
        dungeonLength = 200;

        roomOffset = 2;
        randomWalkRooms = true;
        corridorWidth = 2;

        iterations = 1;
        walkLength = 15;
        startRandomlyEachIteration = true;

        SimpleRandomWalkParameters parameters = new SimpleRandomWalkParameters(iterations, walkLength, startRandomlyEachIteration);

        long seed = Long.parseLong(args[13]) == -1 ? System.currentTimeMillis() : Long.parseLong(args[13]);

        // 1773179824818 -> fails perfect path

        // 1773239985657 -> also not perfect path

        // 1773171971272 -> current stair testing

        // 1773253816596 -> TODO problem with stair (corridor collides with own stair -> now with own room)

        // 1773526739382 -> TODO problem with stair (stair collides with target room)

        // dungeonTest create 20 10 20 200 10 200 2 true 4 10 50 true 1773592002905 -> TODO problem with complex path -> goes too far, through target room

        seed = 1773592002905L;


        Vector3Int startPoint = new Vector3Int(0, 64, 0);
        BoundingBox space = new BoundingBox(startPoint, startPoint.add(dungeonWidth, dungeonHeight, dungeonLength));

        RoomFirstDungeonGenerator3D dungeonGenerator = new RoomFirstDungeonGenerator3D(startPoint, parameters, minRoomWidth, minRoomHeight, minRoomLength, dungeonWidth, dungeonHeight, dungeonLength, roomOffset, randomWalkRooms, corridorWidth);

        long time = System.currentTimeMillis();

        dungeonGenerator.generateDungeon(seed);

        System.out.println("Dungeon Generation took " + ((System.currentTimeMillis() - time) / 1000d) + " secs");

//        BoundingBox startRoom = new BoundingBox(-5, 79, -24, -3, 82, -22);
//        BoundingBox targetRoom = new BoundingBox(10, 79, -18, 12, 82, -16);
//        BoundingBox room3 = new BoundingBox(-2, 79, -18, 2, 82, -14);
//        BoundingBox room4 = new BoundingBox(0, 79, -24, 6, 82, -20);

        /*
        BoundingBox startRoom = new BoundingBox(-6, 79, -42, -2, 82, -40);
        BoundingBox targetRoom = new BoundingBox(-5, 79, -58, -3, 82, -54);
        BoundingBox room3 = new BoundingBox(-10, 79, -49, -4, 82, -47);
        BoundingBox room4 = new BoundingBox(-13, 79, -57, -11, 82, -55);

        List<BoundingBox> rooms = new LinkedList<>(List.of(startRoom, targetRoom, room3, room4));
        Map<Vector3Int, BoundingBox> roomCenterToRoomMap = rooms.stream().collect(Collectors.toMap(BoundingBox::get2DCenter, b -> b));

        Set<Vector3Int> corridor = new LinkedHashSet<>();
        var position = startRoom.get2DCenter();
        var destination = targetRoom.get2DCenter();
        Direction3D direction = null;
        while (position.getZ() != destination.getZ()) {
            if (destination.getZ() > position.getZ()) {
                direction = Direction3D.SOUTH;
            } else {
                direction = Direction3D.NORTH;
            }
            position = direction.apply(position);
            dungeonGenerator.addCorridorAroundPoint(corridor, position, 1);
        }
        System.out.println("    position: " + position);
        while (position.getX() != destination.getX()) {
            if (destination.getX() > position.getX()) {
                direction = Direction3D.EAST;
            } else {
                direction = Direction3D.WEST;
            }
            position = direction.apply(position);
            dungeonGenerator.addCorridorAroundPoint(corridor, position, 1);
        }

        dungeonGenerator.createComplex2DCorridor(startRoom.get2DCenter(), targetRoom.get2DCenter(), corridor, new LinkedHashSet<>(), rooms, null, roomCenterToRoomMap);
        */
    }
}
