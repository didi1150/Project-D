package dev.core.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonWallBlock;

import java.util.*;

import static dev.core.game.dungeon.proceduralDungeon.RoomFirstDungeonGenerator3D.addCorridorAroundPoint;

public class ProceduralGenerationAlgorithms {

    public static Set<Vector3Int> simpleRandomWalk(Vector3Int startPosition, int walkLength, Random random)
    {
        Set<Vector3Int> path = new LinkedHashSet<>();

        path.add(startPosition);
        var previousPosition = startPosition;

        for (int i = 0; i < walkLength; i++)
        {
            var newPosition = Direction3D.getRandom2DCardinalDirection(random).apply(previousPosition);
            path.add(newPosition);
            previousPosition = newPosition;
        }
        return path;
    }

    public static Set<Vector3Int> simpleRandomWalkWithValidBlocks(Vector3Int startPosition, int walkLength, List<DungeonWallBlock> validBlocks, List<Direction3D> directions, Random random)
    {
        Set<Vector3Int> path = new LinkedHashSet<>();

        List<Vector3Int> validPositions = validBlocks.stream().map(DungeonBlock::getPos).toList();

        path.add(startPosition);
        var previousPosition = startPosition;

        for (int i = 0; i < walkLength; i++)
        {
            int index = random.nextInt(0, directions.size());
            Direction3D dir = directions.get(index);
            var newPosition = dir.apply(previousPosition);

            // 26 93 30

            int tries = 0;
            while (!validPositions.contains(newPosition)) {
                index = (index+1) % directions.size();
                dir = directions.get(index);
                newPosition = dir.apply(previousPosition);
                if (tries == 3) {
                    newPosition = previousPosition;
                    break;
                }
                tries++;
            }

            path.add(newPosition);
            previousPosition = newPosition;
        }
        return path;
    }

    public static Set<Vector3Int> simpleRandomWalkWithWidth(Vector3Int startPosition, BoundingBox room, int walkLength, int pathWidth, Random random)
    {
        Set<Vector3Int> path = new LinkedHashSet<>();

        path.add(startPosition);
        var previousPosition = startPosition;

        for (int i = 0; i < walkLength; i++)
        {
            Direction3D direction = Direction3D.getRandom2DCardinalDirection(random);
            var newPosition = direction.apply(previousPosition);
            int counter = 0;
            while (!room.contains(newPosition) && counter < 3) {
                direction = direction.rotateToRight();
                newPosition = direction.apply(previousPosition);
                counter++;
            }
            addCorridorAroundPoint(path, newPosition, pathWidth);
            previousPosition = newPosition;
        }
        return path;
    }

    public static void main(String[] args) {
        var currentPosition = new Vector3Int(0,0,0);
        Set<Vector3Int> floorPositions = new LinkedHashSet<>();
        for (int i = 0; i < 100; i++)
        {
            var path = ProceduralGenerationAlgorithms.simpleRandomWalkWithWidth(currentPosition, new BoundingBox(-10,0,-10,10,10,10), 15, 3, new Random(1773592002905L));
            floorPositions.addAll(path);
        }
    }

    public static List<Vector3Int> randomWalkCorridor(Vector3Int startPosition, int corridorLength)
    {
        Random random = new Random(System.currentTimeMillis());

        List<Vector3Int> corridor = new LinkedList<>();
        var direction = Direction3D.getRandom2DCardinalDirection(random);
        var currentPosition = startPosition;
        corridor.add(currentPosition);

        for (int i = 0; i < corridorLength; i++)
        {
            currentPosition = direction.apply(currentPosition);
            corridor.add(currentPosition);
        }
        return corridor;
    }

//    public static void main(String[] args) {
//        int minRoomWidth = Integer.parseInt(args[0]);
//        int minRoomHeight = Integer.parseInt(args[1]);
//        int minRoomLength = Integer.parseInt(args[2]);
//        int dungeonWidth = Integer.parseInt(args[3]);
//        int dungeonHeight = Integer.parseInt(args[4]);
//        int dungeonLength = Integer.parseInt(args[5]);
//        long seed = Long.parseLong(args[6]) == -1 ? System.currentTimeMillis() : Long.parseLong(args[6]);
//
//        System.out.println("Seed: " + seed);
//
//        Vector3Int startPoint = new Vector3Int(0, 0, 0);
//        BoundingBox space = new BoundingBox(startPoint, startPoint.add(dungeonWidth, dungeonHeight, dungeonLength));
//        long time = System.currentTimeMillis();
//        List<BoundingBox> rooms = ProceduralGenerationAlgorithms.binarySpacePartitioning3D(space, minRoomWidth, minRoomHeight, minRoomLength, new Random(seed));
//        long endTime = System.currentTimeMillis() - time;
//        long s = endTime / 1000;
//        long ms = endTime % 1000;
//        System.out.println("Generated " + rooms.size() + " rooms in " + s + " sec and " + ms + " ms");
//    }
    
    public static List<BoundingBox> binarySpacePartitioning3D(BoundingBox spaceToSplit, int minWidth, int minHeight, int minLength, Random random) {
        int multiplier = 4;
        int maxWidth = minWidth * multiplier;
        int maxHeight = minHeight + minHeight/2;
        int maxLength = minLength * multiplier;

        Queue<BoundingBox> roomsQueue = new LinkedList<>();
        List<BoundingBox> roomsList = new LinkedList<>();
        roomsQueue.add(spaceToSplit);

        int xSplit = 0;
        int ySplit = 0;
        int zSplit = 0;

        int extraSplits = 0;

        int maxRoomCount = (spaceToSplit.getDimensions().getX() / minWidth) * (spaceToSplit.getDimensions().getY()/minHeight);

        System.out.println("RoomLimit = " + maxRoomCount);

        while (!roomsQueue.isEmpty()) {
            var room = roomsQueue.poll();
            if (room.getDimensions().getX() >= minWidth && room.getDimensions().getY() >= minHeight && room.getDimensions().getZ() >= minLength) {
                if ((roomsList.size() + roomsQueue.size() + 1) >= maxRoomCount) {
                    roomsList.add(room);
//                    System.out.println("RoomLimit reached, adding room: " + room.getDimensions());
                    System.out.println("RoomLimit(" + roomsList.size() + ") reached with " + roomsQueue.size() + " Rooms left.");
                    break;
                }

                if (checkForWeirdSizedRoom(roomsQueue, room, random)) {
                    extraSplits++;
                    continue;
                }

                if (room.getDimensions().getX() <= maxWidth && room.getDimensions().getY() <= maxHeight && room.getDimensions().getZ() <= maxLength && random.nextFloat(0, 1) < 0.1) {
                    roomsList.add(room);
                    System.out.println("skipped room splitting with room size of " + room.getDimensions());
                    continue;
                }

                if (random.nextFloat(0,1) < 1/3f) {
                    if (room.getDimensions().getX() >= minWidth*2) {
                        splitOnXAxis(roomsQueue, room, random);
                        xSplit++;
                    } else if (room.getDimensions().getY() >= minHeight*2) {
                        splitOnYAxis(roomsQueue, room, random);
                        ySplit++;
                    } else if (room.getDimensions().getZ() >= minLength*2) {
                        splitOnZAxis(roomsQueue, room, random);
                        zSplit++;
                    } else {
                        roomsList.add(room);
                    }
                } else if (random.nextFloat(0,1) >= 2/3f) {
                    if (room.getDimensions().getY() >= minHeight*2) {
                        splitOnYAxis(roomsQueue, room, random);
                        ySplit++;
                    } else if (room.getDimensions().getZ() >= minLength*2) {
                        splitOnZAxis(roomsQueue, room, random);
                        zSplit++;
                    } else if (room.getDimensions().getX() >= minWidth*2) {
                        splitOnXAxis(roomsQueue, room, random);
                        xSplit++;
                    } else {
                        roomsList.add(room);
                    }
                } else {
                    if (room.getDimensions().getZ() >= minLength*2) {
                        splitOnZAxis(roomsQueue, room, random);
                        zSplit++;
                    } else if (room.getDimensions().getX() >= minWidth*2) {
                        splitOnXAxis(roomsQueue, room, random);
                        xSplit++;
                    } else if (room.getDimensions().getY() >= minHeight*2) {
                        splitOnYAxis(roomsQueue, room, random);
                        ySplit++;
                    } else {
                        roomsList.add(room);
                    }
                }
            }
        }

        while (!roomsQueue.isEmpty()) {
            var room = roomsQueue.poll();
            if (room.getDimensions().getX() >= minWidth && room.getDimensions().getY() >= minHeight && room.getDimensions().getZ() >= minLength) {
                if (checkForWeirdSizedRoom(roomsQueue, room, random)) {
                    extraSplits++;
                    continue;
                }

                roomsList.add(room);
                System.out.println("RoomLimit(" + roomsList.size() + ") reached with " + roomsQueue.size() + " Rooms left.");
            }
        }

        System.out.println("Split space " + xSplit + " times on the x-axis");
        System.out.println("Split space " + ySplit + " times on the y-axis");
        System.out.println("Split space " + zSplit + " times on the z-axis");

        return roomsList;
    }

    private static boolean checkForWeirdSizedRoom(Queue<BoundingBox> roomsQueue, BoundingBox room, Random random) {
        if (room.getDimensions().getX() >= (room.getDimensions().getZ() * 3)) {
            splitOnXAxis(roomsQueue, room, random);
//            xSplit++;
        } else if (room.getDimensions().getZ() >= (room.getDimensions().getX() * 3)) {
            splitOnZAxis(roomsQueue, room, random);
//            zSplit++;
        } else if (room.getDimensions().getY() > (Math.min(room.getDimensions().getX(), room.getDimensions().getZ()) * 2)) {
            splitOnYAxis(roomsQueue, room, random);
//            ySplit++;
        } else {
            return false;
        }
        return true;
    }

    private static void splitOnXAxis(Queue<BoundingBox> roomsQueue, BoundingBox room, Random random) {
        int[] values = getRandomValuePair(room.getDimensions().getX(), random);
        int xSplit1 = values[0];
        int xSplit2 = values[1];
        System.out.println("Splitting-X (" + room.getDimensions().getX() + ") with: " + xSplit1 +  " - " + xSplit2 + " -> " + (xSplit2 - xSplit1) + " & " + (xSplit1 + room.minX) +  " - " + (xSplit2 + room.minX));
        Vector3Int size = room.getDimensions();
        splitSpace(roomsQueue, room, new Vector3Int(xSplit1, size.getY(), size.getZ()), new Vector3Int(xSplit2 + 1, 0, 0));
    }

    private static void splitOnYAxis(Queue<BoundingBox> roomsQueue, BoundingBox room, Random random) {
        int[] values = getRandomValuePair(room.getDimensions().getY(), random);
        int ySplit1 = values[0];
        int ySplit2 = values[1];
        System.out.println("Splitting-Y (" + room.getDimensions().getY() + ") with: " + ySplit1 +  " - " + ySplit2 + " -> " + (ySplit2 - ySplit1) + " & " + (ySplit1 + room.minY) +  " - " + (ySplit2 + room.minY));
        Vector3Int size = room.getDimensions();
        splitSpace(roomsQueue, room, new Vector3Int(size.getX(), ySplit1, size.getZ()), new Vector3Int(0, ySplit2 + 1, 0)); //TODO maybe adjust value
    }

    private static void splitOnZAxis(Queue<BoundingBox> roomsQueue, BoundingBox room, Random random) {
        int[] values = getRandomValuePair(room.getDimensions().getZ(), random);
        int zSplit1 = values[0];
        int zSplit2 = values[1];
        System.out.println("Splitting-Z (" + room.getDimensions().getZ() + ") with: " + zSplit1 +  " - " + zSplit2 + " -> " + (zSplit2 - zSplit1) + " & " + (zSplit1 + room.minZ) +  " - " + (zSplit2 + room.minZ));
        Vector3Int size = room.getDimensions();
        splitSpace(roomsQueue, room, new Vector3Int(size.getX(), size.getY(), zSplit1), new Vector3Int(0, 0, zSplit2 + 1));
    }

    private static int[] getRandomValuePair(int max, Random random) {
        float f = 1/3f;
        float ran = random.nextFloat(f, 1 - f);
        int i1 = (int) (ran * max);
        int i2 = (int) ((random.nextFloat(0, 0.1f) + ran) * max);
        return new int[] {i1, i2};
    }

    private static void splitSpace(Queue<BoundingBox> roomsQueue, BoundingBox room, Vector3Int endRoom1, Vector3Int startRoom2) {
        BoundingBox room1 = new BoundingBox(room.getMinPoint(), room.getMinPoint().add(endRoom1));
        BoundingBox room2 = new BoundingBox(room.getMinPoint().add(startRoom2), room.getMaxPoint());
        roomsQueue.add(room1);
        roomsQueue.add(room2);
    }


    public static List<BoundingBox> binarySpacePartitioning(BoundingBox spaceToSplit, int minWidth, int minLength, Random random) {
        int multiplier = 4;
        int maxWidth = minWidth * multiplier;
        int maxLength = minLength * multiplier;

        Queue<BoundingBox> roomsQueue = new LinkedList<>();
        List<BoundingBox> roomsList = new LinkedList<>();
        roomsQueue.add(spaceToSplit);
        while (!roomsQueue.isEmpty()) {
            var room = roomsQueue.poll();
            if (room.getDimensions().getZ() >= minLength && room.getDimensions().getX() >= minWidth) {
                if (room.getDimensions().getZ() <= maxLength && room.getDimensions().getX() <= maxWidth && random.nextFloat(0, 1) < 0.1) {
                    roomsList.add(room);
                    System.out.println("skipped room splitting with room size of " + room.getDimensions());
                    continue;
                }
                if (random.nextFloat(0,1) > 0.5) {
                    if (room.getDimensions().getZ() >= minLength*2) {
                        splitHorizontally(minLength, roomsQueue, room, random);
                    } else if (room.getDimensions().getX() >= minWidth*2) {
                        splitVertically(minWidth, roomsQueue, room, random);
                    } else {
                        roomsList.add(room);
                    }
                } else {
                    if (room.getDimensions().getX() >= minWidth*2) {
                        splitVertically(minWidth, roomsQueue, room, random);
                    } else if (room.getDimensions().getZ() >= minLength*2) {
                        splitHorizontally(minLength, roomsQueue, room, random);
                    } else {
                        roomsList.add(room);
                    }
                }
            }
        }
        return roomsList;
    }

    private static void splitVertically(int minWidth,Queue<BoundingBox> roomsQueue, BoundingBox room, Random random) {
        int xSplit = random.nextInt(1, room.getDimensions().getX()); // could also limit with minWidth -> nextInt(minWidth, room.getDimensions().getX() - minWidth)
        Vector3Int size = room.getDimensions();
        BoundingBox room1 = new BoundingBox(room.getMinPoint(), room.getMinPoint().add(xSplit, size.getY(), size.getZ()));
        BoundingBox room2 = new BoundingBox(room.getMinPoint().add(xSplit+1, 0, 0), room.getMaxPoint());
        roomsQueue.add(room1);
        roomsQueue.add(room2);
    }

    private static void splitHorizontally(int minLength, Queue<BoundingBox> roomsQueue, BoundingBox room, Random random) {
        int zSplit = random.nextInt(1, room.getDimensions().getZ()); // could also limit with minLength -> nextInt(minLength, room.getDimensions().getY() - minLength)
        Vector3Int size = room.getDimensions();
        BoundingBox room1 = new BoundingBox(room.getMinPoint(), room.getMinPoint().add(size.getX(), size.getY(), zSplit));
        BoundingBox room2 = new BoundingBox(room.getMinPoint().add(0, 0, zSplit+1), room.getMaxPoint());
        roomsQueue.add(room1);
        roomsQueue.add(room2);
    }

}
