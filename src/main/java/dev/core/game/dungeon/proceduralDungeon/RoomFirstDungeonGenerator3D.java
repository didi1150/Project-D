package dev.core.game.dungeon.proceduralDungeon;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.DungeonRoom;
import dev.core.game.dungeon.proceduralDungeon.util.DungeonSpawnManager;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnLocation;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonCeilingBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonDecorationBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonFloorBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonStairBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonWallBlock;
import dev.core.game.settings.GameSettings;

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

    protected List<BoundingBox> rooms = new LinkedList<>();
    protected List<DungeonRoom> dungeonRooms = new LinkedList<>();
    protected List<BoundingBox> notConnectedRooms = new LinkedList<>();
    protected BoundingBox startRoom;
    protected BoundingBox bossRoom;

    protected Set<Vector3Int> corridorFloor = new LinkedHashSet<>();
    protected Set<Vector3Int> stairFloor = new LinkedHashSet<>();

    protected Set<DungeonFloorBlock> floorBlocks = new LinkedHashSet<>();
    protected Set<DungeonStairBlock> stairBlocks = new LinkedHashSet<>();
    protected Set<DungeonFloorBlock> corridorBlocks = new LinkedHashSet<>();
    protected Set<DungeonWallBlock> wallBlocks = new LinkedHashSet<>();
    protected Set<DungeonCeilingBlock> ceilingBlocks = new LinkedHashSet<>();

    protected Set<Vector3Int> possibleSpawnLocations = new LinkedHashSet<>();
    protected Set<Vector3Int> safeSpawnLocations = new LinkedHashSet<>();
    protected List<Vector3Int> randomizedSpawnLocations = new LinkedList<>();
    protected List<Set<DungeonDecorationBlock>> decorationBlocks = new LinkedList<>();

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

//        this.setInDebugMode(true); //TODO for testing
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

    public List<BoundingBox> getRooms() {
        return new LinkedList<>(rooms);
    }

    public List<DungeonRoom> getDungeonRooms() {
        return new LinkedList<>(dungeonRooms);
    }

    public List<BoundingBox> getNotConnectedRooms() {
        return new LinkedList<>(notConnectedRooms);
    }

    public BoundingBox getStartRoom() {
        return startRoom;
    }

    public BoundingBox getBossRoom() {
        return bossRoom;
    }

    public Set<Vector3Int> getCorridorFloor() {
        return new LinkedHashSet<>(corridorFloor);
    }

    public Set<Vector3Int> getStairFloor() {
        return new LinkedHashSet<>(stairFloor);
    }

    public Set<DungeonFloorBlock> getFloorBlocks() {
        return new LinkedHashSet<>(floorBlocks);
    }

    public Set<DungeonStairBlock> getStairBlocks() {
        return new LinkedHashSet<>(stairBlocks);
    }

    public Set<DungeonFloorBlock> getCorridorBlocks() {
        return new LinkedHashSet<>(corridorBlocks);
    }

    public Set<DungeonWallBlock> getWallBlocks() {
        return new LinkedHashSet<>(wallBlocks);
    }

    public Set<DungeonCeilingBlock> getCeilingBlocks() {
        return new LinkedHashSet<>(ceilingBlocks);
    }

    public Set<Vector3Int> getPossibleSpawnLocations() {
        return new LinkedHashSet<>(possibleSpawnLocations);
    }

    public Set<Vector3Int> getSafeSpawnLocations() {
        return new LinkedHashSet<>(safeSpawnLocations);
    }

    public List<Vector3Int> getRandomizedSpawnLocations() {
        return new LinkedList<>(randomizedSpawnLocations);
    }

    public List<Set<DungeonDecorationBlock>> getDecorationBlocks() {
        return new LinkedList<>(decorationBlocks);
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
        int generationAttempts = 0;
        while (generationAttempts < 10) {
            generationAttempts++;

            int roomCreatingAttempts = 0;

            do {
                rooms = ProceduralGenerationAlgorithms.binarySpacePartitioning3D(new BoundingBox(startPosition, startPosition.add(dungeonWidth, dungeonHeight, dungeonLength)), minRoomWidth, minRoomHeight, minRoomLength, random, this);
                roomCreatingAttempts++;
            } while (rooms.isEmpty() && roomCreatingAttempts < 10);

            if (rooms.isEmpty()) {
                printError("Failed to generate valid rooms in " + roomCreatingAttempts + " attempts!");
                continue;
            }

            printInfo("Generated " + rooms.size() + " valid rooms (in " + roomCreatingAttempts + " attempt(s))");

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

            floorBlocks = floorPositions.stream().map(DungeonFloorBlock::new).collect(Collectors.toCollection(LinkedHashSet::new));

            dungeonRooms = rooms.stream().map(room -> new DungeonRoom(room, roomCenterToRoomFloorMap.get(room.get2DCenter()), Collections.emptyList())).collect(Collectors.toList());
            Map<Vector3Int, DungeonRoom> roomCenterToDungeonRoomMap = dungeonRooms.stream().collect(Collectors.toMap(d -> d.getRoom().get2DCenter(), d -> d));

            List<Vector3Int> roomCenters = new LinkedList<>();
            for (var room : rooms) {
                roomCenters.add(room.get2DCenter());
            }
            roomCenters = roomCenters.stream().sorted(Comparator.comparing(center -> center.y)).collect(Collectors.toList());

            corridorFloor = connectRooms(rooms, roomCenterToDungeonRoomMap, roomCenters, roomCenterToRoomMap, roomCenterToRoomFloorMap, random);

            //TODO create extra paths between rooms on the same level

            stairBlocks = createStairs(rooms, roomCenterToDungeonRoomMap, corridorFloor, roomCenters, roomCenterToRoomMap, roomCenterToRoomFloorMap, random);
            stairFloor = stairBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));

            Set<Vector3Int> correctCorridorFloor = new LinkedHashSet<>(corridorFloor);
            correctCorridorFloor.removeAll(stairFloor);
            corridorBlocks = correctCorridorFloor.stream().map(DungeonFloorBlock::new).collect(Collectors.toCollection(LinkedHashSet::new));

//        floorPositions.addAll(corridorFloors);

            Set<Vector3Int> corridorPositions = corridorFloor.stream().flatMap(vec -> getCorridorBox(vec, corridorWidth)).collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Vector3Int> stairPositions = stairFloor.stream().flatMap(vec -> getStairBox(vec, corridorWidth)).collect(Collectors.toCollection(LinkedHashSet::new));
            corridorPositions.addAll(stairPositions);

            wallBlocks = WallGenerator.createWalls(floorPositions, corridorFloor, stairFloor, corridorPositions, rooms, roomOffset, corridorWidth);
            ceilingBlocks = WallGenerator.createCeiling(floorPositions, corridorFloor, stairFloor, rooms, roomOffset, corridorWidth);

            wallPositions = wallBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));
            wallPositions.addAll(ceilingBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new)));

            corridorFloor.addAll(stairFloor);

//        for (var room : rooms) {
//            if (room.get2DFilledBoxPositions().stream().noneMatch(corridorFloor::contains)) {
//                notConnectedRooms.add(room);
//            }
//        }

            List<DungeonRoom> connectedDungeonRooms = dungeonRooms.stream().filter(d -> d.getConnectedRoomsCount() > 0).toList();
            List<DungeonRoom> notConnectedDungeonRooms = dungeonRooms.stream().filter(d -> d.getConnectedRoomsCount() == 0).toList();

            notConnectedRooms = notConnectedDungeonRooms.stream().map(DungeonRoom::getRoom).collect(Collectors.toList());
            printWarning("Not connected Rooms: " + notConnectedRooms.size());

            printInfo("Most connections to one room: " + dungeonRooms.stream().max(Comparator.comparing(DungeonRoom::getConnectedRoomsCount)).get());
            printInfo("Least connections to one room: " + dungeonRooms.stream().min(Comparator.comparing(DungeonRoom::getConnectedRoomsCount)).get());

            List<BoundingBox> connectedRooms = new LinkedList<>(rooms);
            connectedRooms.removeAll(notConnectedRooms);

            int minCenterHeight = connectedRooms.stream().mapToInt(room -> room.get2DCenter().y).min().orElse(0);
            int maxCenterHeight = connectedRooms.stream().mapToInt(room -> room.get2DCenter().y).max().orElse(0);

            startRoom = connectedRooms.stream().filter(room -> room.get2DCenter().y == maxCenterHeight).min(Comparator.comparing(BoundingBox::getVolume)).get();
            int minConnectionCount = connectedDungeonRooms.stream().filter(d -> d.getRoomCenter2D().y == maxCenterHeight).mapToInt(DungeonRoom::getConnectedRoomsCount).min().getAsInt();
            var startRoom2 = connectedDungeonRooms.stream().filter(d -> d.getRoomCenter2D().y == maxCenterHeight).filter(d -> d.getConnectedRoomsCount() == minConnectionCount).min(Comparator.comparing(DungeonRoom::getRealSize)).map(DungeonRoom::getRoom).get();
            if (!startRoom.equals(startRoom2)) {
                printInfo("Replaced startRoom -> diff: " + roomCenterToDungeonRoomMap.get(startRoom.get2DCenter()) + " to " + roomCenterToDungeonRoomMap.get(startRoom2.get2DCenter()));
                startRoom = startRoom2;
            }

            bossRoom = connectedRooms.stream().filter(room -> room.get2DCenter().y == minCenterHeight).max(Comparator.comparing(BoundingBox::getVolume)).get();
            var bossRoom2 = connectedRooms.stream().filter(room -> room.get2DCenter().y == minCenterHeight).max(Comparator.comparing(room -> roomCenterToRoomFloorMap.get(room.get2DCenter()).size())).get();
            if (!bossRoom.equals(bossRoom2)) {
                printInfo("Replaced bossRoom -> diff: " + roomCenterToRoomFloorMap.get(bossRoom.get2DCenter()).size() + " to " + roomCenterToRoomFloorMap.get(bossRoom2.get2DCenter()).size());
                bossRoom = bossRoom2;
            }

            Set<DungeonRoom> reachableRoomsFromStart = roomCenterToDungeonRoomMap.get(startRoom.get2DCenter()).getReachableRooms();
            if (!reachableRoomsFromStart.contains(roomCenterToDungeonRoomMap.get(bossRoom.get2DCenter()))) {
                printError("Dungeon Generation Failure: Bossroom isn't reachable from Start Room. -> retrying ...");
                printDebugInfo(reachableRoomsFromStart.size() + " reachable rooms -> " + reachableRoomsFromStart);
                continue;
            } else {

                createDecoration(dungeonRooms, random);

                possibleSpawnLocations = createPossibleSpawnLocations(floorPositions, corridorFloor, stairFloor);
                safeSpawnLocations = createSafeSpawnLocations(possibleSpawnLocations, getAllOccupiedPositions());
                randomizedSpawnLocations = createRandomizedSpawnLocations(safeSpawnLocations, random);
                for (DungeonRoom dungeonRoom : dungeonRooms) {
                    dungeonRoom.setSpawnPositions(createRoomSpawnLocations(dungeonRoom.getRoomFloor(), safeSpawnLocations, dungeonRoom, random));
                }

                DungeonSpawnManager.getInstance().reset();
                dungeonRooms.stream()
                        .flatMap(room -> room.getSpawnPositions().stream())
                        .forEach(DungeonSpawnManager.getInstance()::registerSpawnLocation);

                if (generationAttempts > 1) {
                    printInfo("Generated Dungeon (in " + generationAttempts + " attempt(s))");
                }
                break;
            }
        }
        if (generationAttempts == 10) {
            printError("Dungeon Generation Failure: Didn't manage to create a valid Dungeon in " + generationAttempts + " attempt(s)");
        }
    }

    private Set<Vector3Int> createPossibleSpawnLocations(Set<Vector3Int> roomFloor, Set<Vector3Int> corridorFloor, Set<Vector3Int> stairFloor) {
        return Stream.concat(roomFloor.stream(), corridorFloor.stream())
                .filter(pos -> !stairFloor.contains(pos))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<SpawnLocation> createRoomSpawnLocations(Set<Vector3Int> roomFloor, Set<Vector3Int> possibleSpawnPositions, DungeonRoom room, Random random) {
        if (startRoom != null && room.getRoom().equals(startRoom)) {
            return Collections.emptyList();
        }

        List<Vector3Int> spawnCandidates = possibleSpawnPositions.stream()
                .filter(roomFloor::contains)
                .filter(pos -> roomFloor.contains(pos.add(1, 0, 0)))
                .filter(pos -> roomFloor.contains(pos.add(-1, 0, 0)))
                .filter(pos -> roomFloor.contains(pos.add(0, 0, 1)))
                .filter(pos -> roomFloor.contains(pos.add(0, 0, -1)))
                .collect(Collectors.toCollection(LinkedList::new));

        int spawnCount = Math.min(spawnCandidates.size(), calculateRoomSpawnCount(room));
        List<Vector3Int> selectedPositions = selectSpreadOutSpawnPositions(spawnCandidates, spawnCount, random);

        return selectedPositions.stream()
                .map(pos -> createSpawnLocation(pos, room, random))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    private int calculateRoomSpawnCount(DungeonRoom room) {
        int floorLevel = GameSettings.getCurrentSettings().getFloor();
        int areaFactor = room.getRealSize() / 20;
        int floorFactor = floorLevel / 2;
        int targetCount = 5 + areaFactor + floorFactor;
        return Math.max(5, Math.min(20, targetCount));
    }

    private List<Vector3Int> selectSpreadOutSpawnPositions(List<Vector3Int> candidates, int spawnCount, Random random) {
        List<Vector3Int> shuffled = new LinkedList<>(candidates);
        Collections.shuffle(shuffled, random);
        List<Vector3Int> selected = new LinkedList<>();
        int minimumSeparation = 4;

        for (Vector3Int candidate : shuffled) {
            if (selected.size() >= spawnCount) {
                break;
            }
            boolean tooClose = selected.stream().anyMatch(existing ->
                    Math.abs(candidate.getX() - existing.getX())
                            + Math.abs(candidate.getY() - existing.getY())
                            + Math.abs(candidate.getZ() - existing.getZ()) < minimumSeparation);
            if (!tooClose) {
                selected.add(candidate);
            }
        }

        for (Vector3Int candidate : shuffled) {
            if (selected.size() >= spawnCount) {
                break;
            }
            if (!selected.contains(candidate)) {
                selected.add(candidate);
            }
        }

        return selected;
    }

    private SpawnLocation createSpawnLocation(Vector3Int pos, DungeonRoom room, Random random) {
        SpawnTier tier = determineSpawnTier(room);
        double baseChance = switch (tier) {
            case BASIC -> 0.8;
            case ADVANCED -> 0.6;
            case ELITE -> 0.4;
        };
        double spawnChance = Math.max(0.1, Math.min(1.0, baseChance + (random.nextDouble() - 0.5) * 0.2));
        int maxEnemyLevel = tier.getMinLevel() + Math.max(0, room.getRealSize() / 40);
        // ~15% of ELITE spawn spots are mini-boss spots (which are also elite).
        boolean miniBoss = tier == SpawnTier.ELITE && random.nextDouble() < 0.15;
        boolean isElite = miniBoss || (tier == SpawnTier.ELITE && random.nextDouble() < 0.2);
        return new SpawnLocation(pos.convertToPoint3D(), tier, spawnChance, maxEnemyLevel, isElite, miniBoss);
    }

    private SpawnTier determineSpawnTier(DungeonRoom room) {
        int size = room.getRealSize();
        if (size >= 120) {
            return SpawnTier.ELITE;
        }
        if (size >= 60) {
            return SpawnTier.ADVANCED;
        }
        return SpawnTier.BASIC;
    }

    private Set<Vector3Int> createSafeSpawnLocations(Set<Vector3Int> possibleSpawnLocations, Set<Vector3Int> occupiedPositions) {
        return possibleSpawnLocations.stream()
                .filter(pos -> isSafeSpawnPosition(pos, occupiedPositions))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Vector3Int> createRandomizedSpawnLocations(Set<Vector3Int> safeSpawnLocations, Random random) {
        List<Vector3Int> spawnLocations = new LinkedList<>(safeSpawnLocations);
        Collections.shuffle(spawnLocations, random);
        return spawnLocations;
    }

    private boolean isSafeSpawnPosition(Vector3Int pos, Set<Vector3Int> occupiedPositions) {
        return !occupiedPositions.contains(pos.add(0, 1, 0)) && !occupiedPositions.contains(pos.add(0, 2, 0));
    }

    private Set<Vector3Int> getAllOccupiedPositions() {
        Set<Vector3Int> occupiedPositions = new LinkedHashSet<>();
        occupiedPositions.addAll(floorBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet()));
        occupiedPositions.addAll(corridorBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet()));
        occupiedPositions.addAll(stairBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet()));
        occupiedPositions.addAll(wallBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet()));
        occupiedPositions.addAll(ceilingBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet()));
        occupiedPositions.addAll(decorationBlocks.stream().flatMap(Set::stream).map(DungeonBlock::getPos).collect(Collectors.toSet()));
        return occupiedPositions;
    }

    private void createDecoration(List<DungeonRoom> dungeonRooms, Random random) {
        decorationBlocks = new LinkedList<>();

        Set<DungeonBlock> allBlocks = new LinkedHashSet<>(wallBlocks);
        allBlocks.addAll(floorBlocks);
        allBlocks.addAll(stairBlocks);
        allBlocks.addAll(corridorBlocks);
        allBlocks.addAll(ceilingBlocks);
        Set<Vector3Int> allPositions = allBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toSet());

        DecorationGenerator decorationGenerator = new DecorationGenerator(this);

        int numberVines = dungeonRooms.size() * 3;
        int vineLength = (minRoomLength + minRoomHeight + minRoomWidth) / 3 - roomOffset;
        decorationBlocks.addAll(decorationGenerator.generateVines(wallBlocks, allPositions, numberVines, vineLength, random));

        float individualFloorVegetationChance = 0.05F;
        Set<DungeonFloorBlock> floor = new LinkedHashSet<>(floorBlocks);
        floor.addAll(corridorBlocks);
        decorationBlocks.addAll(decorationGenerator.generateIndividualFloorVegetation(floor, allPositions, individualFloorVegetationChance, random));

        float hangingCeilingVegetationChance = 0.05F;
//        int hangingVegetationMaxLength = ((minRoomHeight - roomOffset)*2)/3;
        decorationBlocks.addAll(decorationGenerator.generateHangingCeilingVegetation(ceilingBlocks, allPositions, hangingCeilingVegetationChance, dungeonHeight, random));


        float cornerVegetationChance = 0.2F;
        decorationBlocks.addAll(decorationGenerator.generateCornerVegetation(floorBlocks, floor, wallBlocks, allPositions, cornerVegetationChance, random));


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

    private Set<Vector3Int> connectRooms(List<BoundingBox> rooms, Map<Vector3Int, DungeonRoom> roomCenterToDungeonRoomMap, List<Vector3Int> roomCenters, Map<Vector3Int, BoundingBox> roomCenterToRoomMap, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap, Random random) {
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
            Set<Vector3Int> newCorridor = new LinkedHashSet<>();

            LinkedList<Vector3Int> closestList = new LinkedList<>(findClosestPointListTo(currentRoomCenter, roomCenters));

            if (closestList.isEmpty()) {
                currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
                roomCenters.remove(currentRoomCenter);
                continue;
            }

            Vector3Int closest = null;
            while (!validCorridor && !closestList.isEmpty()) {
                validCorridor = true;

                closest = closestList.removeFirst();
//                if (closest == null) {
//                    currentRoomCenter = roomCenters.get(random.nextInt(0, roomCenters.size()));
//                    roomCenters.remove(currentRoomCenter);
//                    break;
//                }

                newCorridor = create2DCorridor(currentRoomCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(currentRoomCenter),roomCenterToRoomMap.get(closest))) {
                    newCorridor = create2DCorridor(closest, currentRoomCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
                }
                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(currentRoomCenter),roomCenterToRoomMap.get(closest))) {
                    Vector3Int newCenter = findClosestPointTo(closest, connectedRoomCenters);
                    printDebugInfo("    Changed room for overlap (" + currentRoomCenter + " to " + closest + ") -> newStartCenter: " + newCenter);
                    if (newCenter == null) {
                        newCenter = currentRoomCenter;
                        printDebugInfo("        Couldn't find closer already connected room");
                    }
                    newCorridor = create2DCorridor(newCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
                    if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
                        newCorridor = create2DCorridor(closest, newCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
                    }
                    if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
                        printDebugWarning("Failed to compute perfect path between " + newCenter + " and " + closest + " , corridor still overlaps with some room -> Trying to create a complex path");
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
                                printDebugWarning("            Couldn't create a valid path for center: " + currentRoomCenter);
                                failedPaths++;
                                break;
                            }
                            continue;
                        } else {
                            complexPaths++;
                        }
                    }
                }

                if (newCorridor.isEmpty()) {
                    validCorridor = false;
                    if (closestList.isEmpty()) {
                        connectedRoomCenters.add(currentRoomCenter);
                        roomCenters.remove(closest);
                        currentRoomCenter = closest;
                        printDebugWarning("            Couldn't create a valid path for center: " + currentRoomCenter);
                        failedPaths++;
                        break;
                    }
                    continue;
                }

                DungeonRoom dungeonRoomStart = roomCenterToDungeonRoomMap.get(currentRoomCenter);
                DungeonRoom dungeonRoomEnd = roomCenterToDungeonRoomMap.get(closest);
                DungeonRoom.addConnectionTo(dungeonRoomStart, dungeonRoomEnd);

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
//                printDebugInfo("    Changed room for overlap (" + currentRoomCenter + " to " + closest + ") -> newStartCenter: " + newCenter);
//                if (newCenter == null) {
//                    newCenter = currentRoomCenter;
//                    printDebugInfo("        Couldn't find closer already connected room");
//                }
//                newCorridor = create2DCorridor(newCenter, closest, roomCenterToRoomMap, roomCenterToRoomFloorMap);
//                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
//                    newCorridor = create2DCorridor(closest, newCenter, roomCenterToRoomMap, roomCenterToRoomFloorMap); // test both directions/startPoints
//                }
//                if (doesCorridorOverlapWithAnything(newCorridor, corridors, rooms, roomCenterToRoomMap.get(newCenter),roomCenterToRoomMap.get(closest))) {
//                    printDebugError("Failed to compute perfect path between " + newCenter + " and " + closest + " , corridor still overlaps with some room -> Trying to create a complex path");
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


        printInfo("        Added " + complexPaths + " complexPaths (failed " + failedPaths + ") and overall " + allPaths + " paths");

        return corridors;
    }

    private Set<DungeonStairBlock> createStairs(List<BoundingBox> rooms, Map<Vector3Int, DungeonRoom> roomCenterToDungeonRoomMap, Set<Vector3Int> corridorFloor, List<Vector3Int> roomCenters, Map<Vector3Int, BoundingBox> roomCenterToRoomMap, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap, Random random) {
        Set<Vector3Int> stairs = new LinkedHashSet<>();
        Set<DungeonStairBlock> stairBlocks = new LinkedHashSet<>();

        Map<Integer, List<Vector3Int>> roomCenterGroups = roomCenters.stream().collect(Collectors.toMap(Vector3Int::getY, v -> new LinkedList<>(List.of(v)), (l1, l2) -> {
            l1.addAll(l2);
            return l1;
        }));

        int stairsAdded = 0;
        int stairsFailed = 0;
        for (var height : roomCenterGroups.keySet().stream().sorted(Comparator.comparing(i -> i)).toList()) {
            List<Vector3Int> centers = roomCenterGroups.get(height);

            LinkedList<List<Vector3Int>> otherCentersList = new LinkedList<>(getMapValueListGreaterThan(roomCenterGroups, height));

            while (!otherCentersList.isEmpty()) {
                List<Vector3Int> otherCenters = otherCentersList.removeFirst();
                Vector3Int otherCenter = otherCenters.get(random.nextInt(0, otherCenters.size()));
                Vector3Int currentCenter = findClosestAndOptimalPointTo(otherCenter, roomCenterToRoomMap.get(otherCenter), centers);

                printDebugInfo("fromCenter: " + currentCenter);
                printDebugInfo("toCenter: " + otherCenter);

                Set<Vector3Int> allCorridorFloor = new LinkedHashSet<>(corridorFloor);
                allCorridorFloor.addAll(stairs);

                Triplet<Set<Vector3Int>, Set<Vector3Int>, Direction3D> triplet = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), allCorridorFloor);

                if (triplet == null || doesCorridorOverlapWithRooms(rooms, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomMap.get(otherCenter), triplet.first())) {
                    printDebugInfo("    Changed stair-room for overlap");
                    Vector3Int newCenter = findClosestAndOptimalPointTo(currentCenter, roomCenterToRoomMap.get(currentCenter), otherCenters);
                    if (newCenter.equals(otherCenter)) {
                        printDebugInfo("Failed to compute better stair-room, already best");
                    } else {
                        otherCenter = newCenter;
                    }
                    triplet = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), allCorridorFloor);
                    if (triplet == null) {
                        printDebugWarning("                    Failed to create Stairs for room -> Staircase can't be placed from this room!");
                        if (otherCentersList.isEmpty()) {
                            stairsFailed++;
                        }
                        continue;
                    }
                }


                DungeonRoom dungeonRoomStart = roomCenterToDungeonRoomMap.get(currentCenter);
                DungeonRoom dungeonRoomEnd = roomCenterToDungeonRoomMap.get(otherCenter);
                DungeonRoom.addConnectionTo(dungeonRoomStart, dungeonRoomEnd);


                LinkedList<Vector3Int> newStair = new LinkedList<>(triplet.first());
                LinkedList<Vector3Int> newCorridor = new LinkedList<>(triplet.second());

                Direction3D stairDirection = triplet.third();

                // first few positions of the corridor are part of the stair
                for (int i = 0; i < corridorWidth; i++) {
                    if (!newCorridor.isEmpty()) {
                        stairBlocks.add(new DungeonStairBlock(newCorridor.removeFirst(), stairDirection));
                    }
                }
                newCorridor = new LinkedList<>(triplet.second());

                // first few positions of the stair are still at the floor level -> not actual stairs
                for (int i = 0; i < corridorWidth; i++) {
                    if (!newStair.isEmpty()) {
                        newCorridor.addFirst(newStair.removeFirst());
                    }
                }

                for (var pos : newStair) {
                    stairBlocks.add(new DungeonStairBlock(pos, stairDirection));
                }

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
//            printDebugInfo("fromCenter: " + currentCenter);
//            printDebugInfo("toCenter: " + otherCenter);
//
//            Set<Vector3Int> newCorridor = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), stairs);
//
//            if (newCorridor == null || doesCorridorOverlapWithRooms(rooms, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomMap.get(otherCenter), newCorridor)) {
//                printDebugInfo("    Changed stair-room for overlap");
//                Vector3Int newCenter = findClosestAndOptimalPointTo(currentCenter, roomCenterToRoomMap.get(currentCenter), otherCenters);
//                if (newCenter.equals(otherCenter)) {
//                    printDebugInfo("Failed to compute better stair-room, already best");
//                } else {
//                    otherCenter = newCenter;
//                }
//                newCorridor = create3DCorridor(currentCenter, roomCenterToRoomMap.get(currentCenter), roomCenterToRoomFloorMap.get(currentCenter), otherCenter, roomCenterToRoomMap.get(otherCenter), stairs);
//                if (newCorridor == null) {
//                    printDebugWarning("                    Failed to create Stairs for room -> Staircase can't be placed from this room!");
//                    stairsFailed++;
//                    continue;
//                }
//            }
//
//            stairs.addAll(newCorridor);
//            stairsAdded++;
        }
        printInfo("        Added " + stairsAdded + " stairs (failed " + stairsFailed + ")");
        return stairBlocks;
    }

    private Set<Vector3Int> createComplex2DCorridor(Vector3Int currentCenter, Vector3Int targetCenter, Set<Vector3Int> failedCorridor, Set<Vector3Int> corridors, List<BoundingBox> rooms, Map<Vector3Int, Set<Vector3Int>> roomCenterToRoomFloorMap, Map<Vector3Int, BoundingBox> roomCenterToRoomMap) {
        BoundingBox currentRoom = roomCenterToRoomMap.get(currentCenter);
        BoundingBox targetRoom = roomCenterToRoomMap.get(targetCenter);

        List<BoundingBox> overlappingRooms = getOverlappingRoomsWithCorridor(rooms, failedCorridor, currentRoom, targetRoom);

        if (overlappingRooms.isEmpty()) {
            printError("    Path only overlaps with other corridors -> failed to compute complex path");
            return new LinkedHashSet<>();
        }

        Vector3Int offset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);

        Set<Vector3Int> corridor = new LinkedHashSet<>();

        int directionsTried = 0;
        Direction3D currentDirection = Direction3D.SOUTH;
        while (directionsTried < 4) {
            Vector3Int currentPos;
            Direction3D lastDirection = currentDirection;
            corridor.clear();
            printDebugInfo("Testing direction: " + currentDirection);
            currentPos = getVecOnRoomEdgeFromCenter(currentCenter, currentRoom, roomCenterToRoomFloorMap.get(currentCenter), currentDirection);
            printDebugInfo("    currentPos: " + currentPos);
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
//            printDebugInfo("    startDirectionDistance: " + (startDirectionDistance));
//            printDebugInfo("    currentPos: " + currentPos);

            Direction3D secondDir = Direction3D.getDirectionForVec(currentDirection.getInverseVec().mul(offset));
            if (secondDir != null) {
                int secondDirectionOffset = offset.mul(secondDir.toVector3Int()).sum();
//                roomEdgeOffset = getDistanceToRoomEdgeFromCenter(currentCenter, currentRoom, secondDir);
                for (var i = 0; i < Math.max(offsetForCorridorSpace, offsetForCorridorSpace + secondDirectionOffset); i++) {
                    currentPos = secondDir.apply(currentPos);
                    addCorridorAroundPoint(corridor, currentPos, corridorWidth);
                }
//                printDebugInfo("    secondDir: " + secondDir);
//                printDebugInfo("    offsetForCorridorSpace: " + (offsetForCorridorSpace));
//                printDebugInfo("    secondDirectionOffset: " + (secondDirectionOffset));
//                printDebugInfo("    currentPos: " + currentPos);
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
//                printDebugInfo("            thirdDir: " + thirdDir);
//                printDebugInfo("            distance: " + (distance));
//                printDebugInfo("            startDirectionOffset: " + (startDirectionOffset*-1));
//                printDebugInfo("            centerOffset: " + (centerOffset));
//                printDebugInfo("            roomEdgeOffset: " + (roomEdgeOffset));
//                printDebugInfo("            offsetForCorridorSpace: " + (offsetForCorridorSpace));
//                printDebugInfo("            currentPos: " + currentPos);
                lastDirection = thirdDir;
            }

//            if (!doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
                printDebugInfo("    Calculating corridor extension to target room");
                Set<Vector3Int> endCorridor = createCorridorFromStair(currentPos, targetCenter, lastDirection);
                corridor.addAll(endCorridor);
                if (doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
                    printDebugWarning("        Failed corridor extension to target room, adding new overlapping rooms to offset and trying again");
                    overlappingRooms.addAll(getOverlappingRoomsWithCorridor(rooms, corridor, currentRoom, targetRoom));
                    Vector3Int newOffset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);

//                        printDebugInfo("    oldOffset: " + offset + " -> " + "newOffset: " + newOffset);
                    if (newOffset.equals(offset)) {
                        printDebugWarning("        Failed corridor extension to target room -> something went wrong");
                    } else {
                        offset = newOffset;
                        continue;
                    }
                } else {
                    printDebugInfo(" -> Success for direction: " + currentDirection);
                    return corridor;
                }
//            }


//            if (startDirectionOffset >= 0) {
//                addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                for (var i = 0; i < corridorWidth + startDirectionOffset; i++) {
//                    currentPos = currentDirection.apply(currentPos);
//                    addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                }
//                printDebugInfo("    startDirectionOffset: " + (corridorWidth + startDirectionOffset));
//                printDebugInfo("    currentPos: " + currentPos);
//
//                Direction3D secondDir = Direction3D.getDirectionForVec(currentDirection.getInverseVec().mul(offset));
//                if (secondDir != null) {
//                    int secondDirectionOffset = offset.mul(secondDir.toVector3Int()).sum();
//                    for (var i = 0; i < corridorWidth + secondDirectionOffset; i++) {
//                        currentPos = secondDir.apply(currentPos);
//                        addCorridorAroundPoint(corridor, currentPos, corridorWidth);
//                    }
//                    printDebugInfo("    secondDir: " + secondDir);
//                    printDebugInfo("    secondDirectionOffset: " + (corridorWidth + secondDirectionOffset));
//                    printDebugInfo("    currentPos: " + currentPos);
//                }
//                if (!doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
//                    printDebugInfo("    Calculating corridor extension to target room");
//                    lastDirection = secondDir == null ? currentDirection : secondDir;
//                    Set<Vector3Int> endCorridor = createCorridorFromStair(currentPos, targetCenter, lastDirection);
//                    corridor.addAll(endCorridor);
//                    if (doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
//                        printDebugWarning("        Failed corridor extension to target room, adding new overlapping rooms to offset and trying again");
//                        overlappingRooms.addAll(getOverlappingRoomsWithCorridor(rooms, corridor, currentRoom, targetRoom));
//                        Vector3Int newOffset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);
////
//                        printDebugInfo("    oldOffset: " + offset + " -> " + "newOffset: " + newOffset);
//                        if (newOffset.equals(offset)) {
//                            printDebugWarning("        Failed corridor extension to target room -> something went wrong");
//                            break;
//                        }
//                        offset = newOffset;
//                        continue;
//                    }
//                    printDebugInfo(" -> Success for direction: " + currentDirection);
//                    return corridor;
//                }
//            } else {
//                addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                for (var i = 0; i < corridorWidth; i++) {
//                    currentPos = currentDirection.apply(currentPos);
//                    addStairCorridorAroundPoint(corridor, currentPos, currentDirection, corridorWidth);
//                }
//                printDebugInfo("    Calculating (simple) corridor extension to target room");
//                Set<Vector3Int> endCorridor = createCorridorFromStair(currentPos, targetCenter, currentDirection);
//                corridor.addAll(endCorridor);
//                if (doesCorridorOverlapWithAnything(corridor, corridors, rooms, currentRoom, targetRoom)) {
//                    printDebugWarning("        Failed corridor extension to target room, adding new overlapping rooms to offset and trying again");
//                    overlappingRooms.addAll(getOverlappingRoomsWithCorridor(rooms, corridor, currentRoom, targetRoom));
//                    Vector3Int newOffset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);
////                        printDebugInfo("    oldOffset: " + offset + " -> " + "newOffset: " + newOffset);
//                    if (newOffset.equals(offset)) {
//                        printDebugWarning("        Failed corridor extension to target room -> something went wrong");
//                        break;
//                    }
//                    offset = newOffset;
//                    continue;
//                }
//                printDebugInfo(" -> Success for direction: " + currentDirection);
//                return corridor;
//            }

            printDebugInfo(" -> Failed for direction: " + currentDirection);

            overlappingRooms = getOverlappingRoomsWithCorridor(rooms, failedCorridor, currentRoom, targetRoom);
            offset = getOverlappingRoomsOffsetVec(currentCenter, currentRoom, overlappingRooms);

            currentDirection = currentDirection.rotateToRight();
            directionsTried++;

            corridor.clear();
        }

        return corridor;

//        for (var direction : Direction3D.get2DCardinalDirections()) {
//            printDebugInfo("Testing direction: " + direction);
//            currentPos = getVecOnRoomEdgeFromCenter(currentCenter, currentRoom, direction);
//            printDebugInfo("currentPos: " + currentPos);
//            // add offset to dont intersect with owm room
//            int startDirectionOffset = offset.mul(direction.toVector3Int()).sum();
//            if (startDirectionOffset < 0) continue;
//            addStairCorridorAroundPoint(corridor, currentPos, direction, corridorWidth);
//            for (var i = 0; i < corridorWidth + startDirectionOffset; i++) {
//                currentPos = direction.apply(currentPos);
//                addStairCorridorAroundPoint(corridor, currentPos, direction, corridorWidth);
//            }
//            printDebugInfo("startDirectionOffset: " + (corridorWidth + startDirectionOffset));
//            printDebugInfo("currentPos: " + currentPos);
//
//            Direction3D secondDir = Direction3D.getDirectionForVec(direction.getInverseVec().mul(offset));
//            if (secondDir != null) {
//                int secondDirectionOffset = offset.mul(secondDir.toVector3Int()).sum();
//                for (var i = 0; i < corridorWidth + secondDirectionOffset; i++) {
//                    currentPos = secondDir.apply(currentPos);
//                    addCorridorAroundPoint(corridor, currentPos, corridorWidth);
//                }
//                printDebugInfo("secondDir: " + secondDir);
//                printDebugInfo("secondDirectionOffset: " + (corridorWidth + secondDirectionOffset));
//                printDebugInfo("currentPos: " + currentPos);
//            }
//
//            if (!doesCorridorOverlapWithRooms(rooms, corridor, currentRoom, targetRoom)) {
//                lastDirection = secondDir == null ? direction : secondDir;
//                break;
//            }
//
//            printDebugInfo("Failed for direction: " + direction);
//
//            corridor.clear();
//        }
//
//        if (lastDirection == null) {
//            throw new RuntimeException("No direction was found that could create a valid path!");
//        }
    }

    private Vector3Int getOverlappingRoomsOffsetVec(Vector3Int currentCenter, BoundingBox currentRoom, List<BoundingBox> overlappingRooms) {
        printDebugInfo("overlappingRooms: " + overlappingRooms);

        Vector3Int offset = Vector3Int.ZERO;
        Vector3Int ignoreHeightVec = new Vector3Int(1,0,1);

        BoundingBox mergedOverlappingRoom = BoundingBox.merge(overlappingRooms.toArray(BoundingBox[]::new));

        printDebugInfo("mergedOverlappingRoom: " + mergedOverlappingRoom + " -> center: " + mergedOverlappingRoom.get2DCenter() + " dim: " + mergedOverlappingRoom.getDimensions());

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

//        printDebugInfo("centerDiff: " + centerDiff);
//        printDebugInfo("dimensionDiff: " + dimensionDiff + " -> " + mergedOverlappingRoom.getDimensions().mul(0.5) + " => x: " + dimensionXOffest + " z: " + dimensionZOffest);
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
//            printDebugInfo("centerDiff: " + centerDiff);
//            printDebugInfo("dimensionDiff: " + dimensionDiff + " -> " + room.getDimensions().mul(0.5) + " => x: " + dimensionXOffest + " z: " + dimensionZOffest);
//            offset = offset.add(centerDiff);
//            offset = offset.add(new Vector3Int(dimensionXOffest, 0, dimensionZOffest));
//        }

        printDebugInfo("offset: " + offset);
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
        Set<Vector3Int> finalCorridor = expandPositionsForCorridor(corridor, corridorWidth, false);
        List<BoundingBox> updatedRooms = new LinkedList<>(rooms);
        updatedRooms.removeAll(Arrays.asList(ignoredRooms));
        return updatedRooms.stream().filter(b -> finalCorridor.stream().anyMatch(b::contains)).collect(Collectors.toList());
    }

    private Triplet<Set<Vector3Int>, Set<Vector3Int>, Direction3D> create3DCorridor(Vector3Int currentCenter, BoundingBox currentRoom, Set<Vector3Int> currentRoomFloor, Vector3Int targetCenter, BoundingBox targetRoom, Set<Vector3Int> corridors) {
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

            if (doesCorridorOverlapWithRooms(rooms, stairCorridor, currentRoom)) {
                printDebugInfo("\tnext dir(" + direction + "): stair overlaps with another room");
                continue; // TODO maybe not allow targetRoom to be overlapped with stair ?
            }
            if (doesStairOverlapWithCorridors(corridors, stairCorridor)) {
                printDebugInfo("\tnext dir(" + direction + "): stair overlaps with another corridor");
                continue;
            }

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

            if (doesCorridorOverlapWithRooms(rooms, restCorridor,currentRoom, targetRoom)) {
                printDebugInfo("\tnext dir(" + direction + "): stair-corridor overlaps with another room");
                continue; //TODO check if corridor overlaps with the currentRoom, goes through it
            }

            if (doesCorridorOverlapWithRooms(rooms, restCorridor, targetRoom)) {
                if (doesCorridorOverlapWithRoomFloor(currentRoom, currentRoomFloor, restCorridor)) {
                    printDebugInfo("\tnext dir(" + direction + "): stair-corridor overlaps with current room");
                    continue;
                }
            }

            if (doesCorridorOverlapWithCorridors(corridors, restCorridor)) {
                printDebugInfo("\tnext dir(" + direction + "): stair-corridor overlaps with another corridor");
                continue;
            }

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

        return new Triplet<>(stairs, corridor, optimalStartDirection);
    }

    private Set<Vector3Int> createCorridorFromStair(Vector3Int currentPos, Vector3Int targetCenter, Direction3D direction) {
        Set<Vector3Int> corridor = new LinkedHashSet<>();

        Vector3Int newDistanceVec = targetCenter.sub(currentPos);

//        printDebugInfo("    currentPos: " + currentPos);
//        printDebugInfo("    targetCenter: " + targetCenter);
//        printDebugInfo("    NewDistance: " + newDistanceVec);

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

//        printDebugInfo("        firstDirection: " + firstDirection + " -> " + firstDirectionCount);
//        printDebugInfo("        secondDirection: " + secondDirection + " -> " + secondDirectionCount);
//
//        printDebugInfo("        currentPos: " + currentPos);

        for (int i = 0; i < firstDirectionCount; i++) {
            currentPos = firstDirection.apply(currentPos);
            addCorridorAroundPoint(corridor, currentPos, corridorWidth);
        }

//        printDebugInfo("        currentPos: " + currentPos);

        for (int i = 0; i < secondDirectionCount; i++) {
            currentPos = secondDirection.apply(currentPos);
            addCorridorAroundPoint(corridor, currentPos, corridorWidth);
        }

//        printDebugInfo("        currentPos: " + currentPos);

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
        return rooms.stream()
                .anyMatch(room -> corridor.stream().anyMatch(vec -> isRoomInteriorOverlap(room, vec))
                        && (!room.equals(room1) || !room.equals(room2) || corridor.stream().anyMatch(vec -> isRoomInteriorOverlap(room, vec))));
    }

    private boolean doesCorridorOverlapWithRooms(List<BoundingBox> rooms, Set<Vector3Int> corridor, BoundingBox ... ignoredRooms) {
        Set<Vector3Int> finalCorridor = expandPositionsForCorridor(corridor, corridorWidth, false);
        List<BoundingBox> updatedRooms = new LinkedList<>(rooms);
        updatedRooms.removeAll(Arrays.asList(ignoredRooms));
        return updatedRooms.stream().anyMatch(room -> finalCorridor.stream().anyMatch(vec -> isRoomInteriorOverlap(room, vec)));
    }

    private boolean doesCorridorOverlapWithRoomFloor(BoundingBox room, Set<Vector3Int> roomFloor, Set<Vector3Int> corridor) {
        for (Vector3Int pos : roomFloor) {
            for (int i = 0; i < room.getDimensions().y; i++) {
                if (corridor.contains(pos.add(0,i,0)))
                    return true;
            }
        }
        return false;
    }

    private boolean isRoomInteriorOverlap(BoundingBox room, Vector3Int vec) {
        if (!room.contains(vec)) {
            return false;
        }
        return !isRoomBoundaryPoint(room, vec);
    }

    private boolean isRoomBoundaryPoint(BoundingBox room, Vector3Int vec) {
        return vec.getX() == room.minX || vec.getX() == room.maxX
                || vec.getY() == room.minY || vec.getY() == room.maxY
                || vec.getZ() == room.minZ || vec.getZ() == room.maxZ;
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
        Set<Vector3Int> expandedCorridors = expandPositionsForCorridor(corridors, corridorWidth, false);
        Set<Vector3Int> expandedStair = expandPositionsForCorridor(stair, corridorWidth, true);
        return expandedCorridors.stream().anyMatch(expandedStair::contains);
    }

    private boolean doesCorridorOverlapWithCorridors(Set<Vector3Int> corridors, Set<Vector3Int> corridor) {
        //TODO currently removing same floor positions to allow for paths to cross -> do we want that?
        Set<Vector3Int> updatedCorridor = new LinkedHashSet<>(corridors);
        updatedCorridor.removeAll(corridor);

        Set<Vector3Int> expandedUpdated = expandPositionsForCorridor(updatedCorridor, corridorWidth, false);
        Set<Vector3Int> expandedCorridor = expandPositionsForCorridor(corridor, corridorWidth, false);
        return expandedUpdated.stream().anyMatch(expandedCorridor::contains);
    }

    private Stream<Vector3Int> getStairBox(Vector3Int vec, int corridorWidth) {
        List<Vector3Int> list = new LinkedList<>();
        int height = Math.max(3, corridorWidth + 1) + 1;
        for (var i = 0; i <= height; i++) {
            list.add(vec.add(0, i, 0));
        }
        return list.stream();
    }

    private List<Vector3Int> getCorridorBoxList(Vector3Int vec, int corridorWidth) {
        List<Vector3Int> list = new LinkedList<>();
        int height = Math.max(2, corridorWidth) + 1;
        for (var i = 0; i <= height; i++) {
            list.add(vec.add(0, i, 0));
        }
        return list;
    }

    private List<Vector3Int> getStairBoxList(Vector3Int vec, int corridorWidth) {
        List<Vector3Int> list = new LinkedList<>();
        int height = Math.max(3, corridorWidth + 1) + 1;
        for (var i = 0; i <= height; i++) {
            list.add(vec.add(0, i, 0));
        }
        return list;
    }

    private Set<Vector3Int> expandPositionsForCorridor(Set<Vector3Int> positions, int corridorWidth, boolean stair) {
        Set<Vector3Int> expanded = new LinkedHashSet<>();
        if (positions == null || positions.isEmpty()) return expanded;
        if (stair) {
            for (var p : positions) {
                expanded.addAll(getStairBoxList(p, corridorWidth));
            }
        } else {
            for (var p : positions) {
                expanded.addAll(getCorridorBoxList(p, corridorWidth));
            }
        }
        return expanded;
    }

    private boolean doesVecOverlapWithRooms(List<BoundingBox> rooms, BoundingBox room, Vector3Int vec) {
        return rooms.stream().filter(b -> !b.equals(room)).anyMatch(b -> isRoomInteriorOverlap(b, vec));
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
        dungeonWidth = 50;
        dungeonHeight = 30;
        dungeonLength = 50;

        roomOffset = 1;
        randomWalkRooms = true;
        corridorWidth = 3;

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

        seed = 1774023553026L;


        Vector3Int startPoint = new Vector3Int(0, 64, 0);
        BoundingBox space = new BoundingBox(startPoint, startPoint.add(dungeonWidth, dungeonHeight, dungeonLength));

        RoomFirstDungeonGenerator3D dungeonGenerator = new RoomFirstDungeonGenerator3D(startPoint, parameters, minRoomWidth, minRoomHeight, minRoomLength, dungeonWidth, dungeonHeight, dungeonLength, roomOffset, randomWalkRooms, corridorWidth);

        dungeonGenerator.generateDungeon(seed);

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
        printDebugInfo("    position: " + position);
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
