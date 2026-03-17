package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;

import java.util.*;

public class RoomFirstDungeonGenerator extends SimpleRandomWalkDungeonGenerator {

    public record RoomFirstParameters(SimpleRandomWalkParameters randomWalkParameters, int minRoomWidth, int minRoomLength, int dungeonWidth, int dungeonLength, int offset, boolean randomWalkRooms) {}

    protected int minRoomWidth = 4;
    protected int minRoomLength = 4;
    protected int dungeonWidth = 20;
    protected int dungeonLength = 20;
    protected int dungeonHeight = 5;
    protected int offset = 1;
    protected boolean randomWalkRooms = false;

    protected int corridorWidth = 4;

    protected List<BoundingBox> rooms = new LinkedList<>();

    public RoomFirstDungeonGenerator(Vector3Int startPosition, SimpleRandomWalkParameters randomWalkParameters, int minRoomWidth, int minRoomLength, int dungeonWidth, int dungeonLength, int offset, boolean randomWalkRooms) {
        super(startPosition, randomWalkParameters);
        this.minRoomWidth = minRoomWidth;
        this.minRoomLength = minRoomLength;
        this.dungeonWidth = dungeonWidth;
        this.dungeonLength = dungeonLength;
        this.offset = offset;
        this.randomWalkRooms = randomWalkRooms;
    }

    public RoomFirstDungeonGenerator(SimpleRandomWalkParameters randomWalkParameters, int minRoomWidth, int minRoomLength, int dungeonWidth, int dungeonLength, int offset, boolean randomWalkRooms) {
        super(randomWalkParameters);
        this.minRoomWidth = minRoomWidth;
        this.minRoomLength = minRoomLength;
        this.dungeonWidth = dungeonWidth;
        this.dungeonLength = dungeonLength;
        this.offset = offset;
        this.randomWalkRooms = randomWalkRooms;
    }

    public RoomFirstDungeonGenerator(int minRoomWidth, int minRoomLength, int dungeonWidth, int dungeonLength, int offset, boolean randomWalkRooms) {
        this.minRoomWidth = minRoomWidth;
        this.minRoomLength = minRoomLength;
        this.dungeonWidth = dungeonWidth;
        this.dungeonLength = dungeonLength;
        this.offset = offset;
        this.randomWalkRooms = randomWalkRooms;
    }

    public RoomFirstDungeonGenerator(RoomFirstParameters roomFirstParameters) {
        super(roomFirstParameters.randomWalkParameters);
        this.minRoomWidth = roomFirstParameters.minRoomWidth;
        this.minRoomLength = roomFirstParameters.minRoomLength;
        this.dungeonWidth = roomFirstParameters.dungeonWidth;
        this.dungeonLength = roomFirstParameters.dungeonLength;
        this.offset = roomFirstParameters.offset;
        this.randomWalkRooms = roomFirstParameters.randomWalkRooms;
    }

    public RoomFirstDungeonGenerator() {
    }

    public List<BoundingBox> getRooms() {
        return rooms;
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
        rooms = ProceduralGenerationAlgorithms.binarySpacePartitioning(new BoundingBox(startPosition, startPosition.add(dungeonWidth, 0, dungeonLength)), minRoomWidth, minRoomLength, random);

        if (randomWalkRooms) {
            floorPositions = createRoomsRandomly(rooms, random);
        } else {
            floorPositions = CreateSimpleRooms(rooms);
        }

        List<Vector3Int> roomCenters = new LinkedList<>();
        for (var room : rooms) {
            roomCenters.add(room.get2DCenter());
        }

        Set<Vector3Int> corridors = connectRooms(rooms, roomCenters, random);
        floorPositions.addAll(corridors);

        wallPositions = WallGenerator.createWalls(floorPositions);
    }

    private Set<Vector3Int> createRoomsRandomly(List<BoundingBox> rooms, Random random) {
        Set<Vector3Int> floor = new LinkedHashSet<>();
        for (var room : rooms) {
            var roomCenter = room.get2DCenter();
            int walkLength = (room.getDimensions().getX() + room.getDimensions().getZ()) / 2;
//            walkLength = Math.max(room.getDimensions().getX(), room.getDimensions().getZ());
            SimpleRandomWalkParameters parameters = new SimpleRandomWalkParameters(iterations, walkLength, startRandomlyEachIteration);
            var roomFloor = runRandomWalkWithWidth(parameters, roomCenter, random, 3);
            for (var position : roomFloor) {
                if (position.getX() >= (room.minX + offset) && position.getX() <= (room.maxX - offset)
                        && position.getZ() >= (room.minZ + offset) && position.getZ() <= (room.maxZ - offset)) {
                    floor.add(position);
                }
            }
        }
        return floor;
    }

    private Set<Vector3Int> runRandomWalkWithWidth(SimpleRandomWalkParameters parameters, Vector3Int position, Random random, int pathWidth)
    {
        Set<Vector3Int> floorPositions = new LinkedHashSet<>();
        for (var pos : runRandomWalk(parameters, position, random)) {
            addCorridorAroundPoint(floorPositions, pos, pathWidth);
        }
        return floorPositions;
    }

    private Set<Vector3Int> connectRooms(List<BoundingBox> rooms, List<Vector3Int> roomCenters, Random random) {
        Set<Vector3Int> corridors = new LinkedHashSet<>();
        List<Vector3Int> connectedRoomCenters = new LinkedList<>();
        var currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
        roomCenters.remove(currentRoomCenter);

        while (!roomCenters.isEmpty()) {
            Vector3Int closest = findClosestPointTo(currentRoomCenter, roomCenters);
            roomCenters.remove(closest);
            Set<Vector3Int> newCorridor = createCorridor(currentRoomCenter, closest);
            if (doesCorridorOverLapWithRooms(rooms, currentRoomCenter, closest, newCorridor)) {
                System.out.println("    Changed room for overlap");
                currentRoomCenter = findClosestPointTo(closest, connectedRoomCenters);
                newCorridor = createCorridor(currentRoomCenter, closest);
            } else {
                connectedRoomCenters.add(currentRoomCenter);
            }
            currentRoomCenter = closest;
            corridors.addAll(newCorridor);
        }

        return corridors;
    }

    private boolean doesCorridorOverLapWithRooms(List<BoundingBox> rooms, Vector3Int currentRoomCenter, Vector3Int closestRoomCenter, Set<Vector3Int> corridor) {
        return rooms.stream().filter(b -> !b.get2DCenter().equals(currentRoomCenter) && !b.get2DCenter().equals(closestRoomCenter)).anyMatch(b -> b.get2DFilledBoxPositions().stream().anyMatch(corridor::contains));
    }


    private Set<Vector3Int> createCorridor(Vector3Int currentRoomCenter, Vector3Int destination) {
        Set<Vector3Int> corridor = new LinkedHashSet<>();
        var position = currentRoomCenter;
        while (position.getZ() != destination.getZ()) {
            if (destination.getZ() > position.getZ()) {
                position = Direction3D.SOUTH.apply(position);
            } else {
                position = Direction3D.NORTH.apply(position);
            }
            addCorridorAroundPoint(corridor, position, corridorWidth);
        }
        while (position.getX() != destination.getX()) {
            if (destination.getX() > position.getX()) {
                position = Direction3D.EAST.apply(position);
            } else {
                position = Direction3D.WEST.apply(position);
            }
            addCorridorAroundPoint(corridor, position, corridorWidth);
        }
        return corridor;
    }

    private void addCorridorAroundPoint(Set<Vector3Int> corridor, Vector3Int position, int corridorWidth) {
        corridor.add(position);
        int i = (corridorWidth - 1) / 2;
        for (int x = -i; x < corridorWidth - i; x++) {
            for (int z = -i; z < corridorWidth - i; z++) {
                corridor.add(position.add(x, 0, z));
            }
        }
    }


    private Vector3Int findClosestPointTo(Vector3Int currentRoomCenter, List<Vector3Int> roomCenters) {
        Vector3Int closest = Vector3Int.ZERO;
        double length = Float.MAX_VALUE;
        for (var position : roomCenters) {
            double currentDistance = currentRoomCenter.distance(position);
            if (currentDistance < length) {
                length = currentDistance;
                closest = position;
            }
        }
        return closest;
    }

    private Set<Vector3Int> CreateSimpleRooms(List<BoundingBox> roomList) {
        Set<Vector3Int> floor = new LinkedHashSet<>();
        for (var room : roomList) {
            for (int col = offset; col < room.getDimensions().getX() - offset; col++) {
                for (int row = offset; row < room.getDimensions().getZ() - offset; row++) {
                    Vector3Int position = room.getMinPoint().add(col, 0, row);
                    floor.add(position);
                }
            }
        }
        return floor;
    }
}
