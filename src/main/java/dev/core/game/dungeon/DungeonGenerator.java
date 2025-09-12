package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class DungeonGenerator {

    private final Random random;
    private final List<DungeonRoom> rooms;
    private final List<DungeonTunnel> tunnels;
    private int roomCounter;
    private int tunnelCounter;

    public DungeonGenerator(long seed) {
        this.random = new Random(seed);
        this.rooms = new ArrayList<>();
        this.tunnels = new ArrayList<>();
        this.roomCounter = 0;
        this.tunnelCounter = 0;
    }

    public Dungeon generateDungeon(int roomCount, Point3D startCenter) {
        rooms.clear();
        tunnels.clear();
        roomCounter = 0;
        tunnelCounter = 0;

        // Generate spawn room first
        SpawnRoom spawnRoom = new SpawnRoom("spawn_room", startCenter, 14); // Fixed size for spawn room
        rooms.add(spawnRoom);
        roomCounter++;
        System.out.println("Generated room 1/" + roomCount + ": " + spawnRoom.getId());

        // Generate regular rooms (leaving space for end room)
        Queue<DungeonRoom> roomsToExpand = new LinkedList<>();
        roomsToExpand.add(spawnRoom);

        int regularRoomsNeeded = Math.max(1, roomCount - 2); // Account for spawn and end rooms

        while (rooms.size() < regularRoomsNeeded + 1 && !roomsToExpand.isEmpty()) { // +1 for spawn room
            DungeonRoom currentRoom = roomsToExpand.poll();

            // Try to add 1-3 connections from this room
            int connectionsToAdd = random.nextInt(3) + 1;
            if (currentRoom.getType() == RoomType.SPAWN_ROOM) {
                connectionsToAdd = 1;
            }
            List<Direction> availableDirections = new ArrayList<>();

            for (Direction dir : Direction.values()) {
                if (!currentRoom.getConnections().containsKey(dir)) {
                    availableDirections.add(dir);
                }
            }

            Collections.shuffle(availableDirections, random);

            for (int i = 0; i < Math.min(connectionsToAdd, availableDirections.size())
                    && rooms.size() < regularRoomsNeeded + 1; i++) {
                Direction dir = availableDirections.get(i);

                Point3D newCenter = calculateNewRoomCenter(currentRoom, dir);

                // Check if position is valid (no overlap with existing rooms)
                DungeonRoom newRoom = RoomFactory.createRandomRoom("room_" + (++roomCounter), newCenter, random);
                int maxRoomTries = 30;
                int currentRoomTires = 0;
                while (!isValidPosition(newRoom) && currentRoomTires < maxRoomTries) {
                    newCenter = calculateNewRoomCenter(currentRoom, dir);
                    newRoom = RoomFactory.createRandomRoom("room_" + (++roomCounter), newCenter, random);
                    currentRoomTires++;
                }
                if (!isValidPosition(newRoom)) {
                    continue;
                }
                // Create tunnel connection
                DungeonTunnel tunnel = TunnelFactory.createRandomTunnel("tunnel_" + (++tunnelCounter), currentRoom,
                        newRoom, dir, random);
                int maxTries = 30;
                int currentTry = 0;
                while (!isValidTunnel(tunnel) && currentTry < maxTries) {
                    tunnel = TunnelFactory.createRandomTunnel("tunnel_" + (++tunnelCounter), currentRoom, newRoom, dir,
                            random);
                    currentTry++;
                }
                if (!isValidTunnel(tunnel)) {
                    continue;
                }
                // Add room connection
                currentRoom.addConnection(newRoom, dir);

                // Clear room entrances for the tunnel
                tunnel.clearRoomEntrances();

                rooms.add(newRoom);
                tunnels.add(tunnel);
                roomsToExpand.add(newRoom);

                // Progress print
                System.out.println("Generated room " + rooms.size() + "/" + roomCount + ": " + newRoom.getId()
                        + " (tunnels: " + tunnels.size() + ")");
            }
        }

        // Generate end portal room
        EndPortalRoom endRoom = generateEndPortalRoom(startCenter);
        rooms.add(endRoom);

        System.out.println("Generated end room: " + endRoom.getId());

        // Connect end room to the dungeon network
        connectEndRoomToDungeon(endRoom);

        System.out.println("Connected end room to dungeon. Total tunnels: " + tunnels.size());

        return new Dungeon(new ArrayList<>(rooms), new ArrayList<>(tunnels));
    }

    private boolean isValidTunnel(DungeonTunnel tunnel) {
        BoundingBox tunnelBox = tunnel.getBoundingBox();

        // Check against other tunnels
        for (DungeonTunnel existingTunnel : tunnels) {
            if (tunnelBox.intersects(existingTunnel.getBoundingBox())) {
                return false;
            }
        }

//        // Check against rooms (using room bounding boxes)
//        int maxChecks = Math.max(1, rooms.size() / 2);
//        int checks = 0;
//        for (DungeonRoom room : rooms) {
//            if (checks++ >= maxChecks) {
//                break;
//            }
//            if (tunnelBox.intersects(room.getBoundingBox())
//                    && (!tunnel.getEndConnection().getRoom().getId().equals(room.getId())
//                            || !tunnel.getStartConnection().getRoom().getId().equals(room.getId()))) {
//                return false;
//            }
//        }

        return true;
    }

    private EndPortalRoom generateEndPortalRoom(Point3D spawnCenter) {
        Point3D endCenter = findOptimalEndRoomPosition(spawnCenter);
        System.out.println("End Center: " + endCenter);
        System.out.println("Spawn Center: " + spawnCenter);
        return new EndPortalRoom("end_portal_room", endCenter, 18); // Large size for end room
    }

    private Point3D findOptimalEndRoomPosition(Point3D spawnCenter) {
        // Try to place end room at maximum distance from spawn
        int maxAttempts = 50;
        Point3D bestPosition = null;
        double maxDistance = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generate positions in a wide radius around spawn
            int distance = 80 + random.nextInt(40); // 80-120 blocks from spawn
            double angle = random.nextDouble() * 2 * Math.PI;

            int x = (int) (spawnCenter.getX() + distance * Math.cos(angle));
            int z = (int) (spawnCenter.getZ() + distance * Math.sin(angle));
            Point3D candidate = new Point3D(x, spawnCenter.getY(), z);

            if (isValidEndRoomPosition(candidate)) {
                double distanceFromSpawn = Math.sqrt(Math.pow(candidate.getX() - spawnCenter.getX(), 2)
                        + Math.pow(candidate.getZ() - spawnCenter.getZ(), 2));

                if (distanceFromSpawn > maxDistance) {
                    maxDistance = distanceFromSpawn;
                    bestPosition = candidate;
                }
            }
        }

        // Fallback if no valid position found
        if (bestPosition == null) {
            bestPosition = new Point3D(spawnCenter.getX() + 100, spawnCenter.getY(), spawnCenter.getZ() + 100);
        }

        return bestPosition;
    }

    private boolean isValidEndRoomPosition(Point3D center) {
        int minDistance = 35; // Larger minimum distance for end room

        for (DungeonRoom room : rooms) {

            if (room.getCenter() == null) {
                throw new IllegalStateException("Room " + room.getId() + " has null center!");
            }

            double distance = Math.sqrt(Math.pow(center.getX() - room.getCenter().getX(), 2)
                    + Math.pow(center.getZ() - room.getCenter().getZ(), 2));

            if (distance < minDistance) {
                return false;
            }
        }

        return true;
    }

    private void connectEndRoomToDungeon(EndPortalRoom endRoom) {
        // Find the best room to connect to (furthest from spawn but not too far from
        // end room)
        DungeonRoom bestConnection = findBestConnectionForEndRoom(endRoom);

        if (bestConnection != null) {
            // Find available direction from the connection room
            Direction connectionDir = findAvailableDirection(bestConnection, endRoom.getCenter());

            if (connectionDir != null) {
                // Create tunnel to end room
                DungeonTunnel endTunnel = TunnelFactory.createRandomTunnel("tunnel_to_end", bestConnection, endRoom,
                        connectionDir, random);

                // Add connection
                bestConnection.addConnection(endRoom, connectionDir);
                endTunnel.clearRoomEntrances();
                tunnels.add(endTunnel);
            } else {
                // Fallback: create a longer tunnel from any room
                createFallbackEndConnection(endRoom);
            }
        } else {
            // Emergency fallback: connect to last regular room
            if (rooms.size() > 1) {
                DungeonRoom lastRoom = rooms.get(rooms.size() - 2); // -2 because end room was just added
                createDirectConnection(lastRoom, endRoom);
            }
        }
    }

    private DungeonRoom findBestConnectionForEndRoom(EndPortalRoom endRoom) {
        DungeonRoom bestRoom = null;
        double bestScore = -1;

        // Skip spawn room (index 0) and end room (last)
        for (int i = 1; i < rooms.size() - 1; i++) {
            DungeonRoom room = rooms.get(i);

            // Skip if room already has maximum connections or is spawn room
            if (room.getConnections().size() >= 4 || room.getType() == RoomType.SPAWN_ROOM) {
                continue;
            }

            // Calculate distance from end room
            double distanceToEnd = Math.sqrt(Math.pow(room.getCenter().getX() - endRoom.getCenter().getX(), 2)
                    + Math.pow(room.getCenter().getZ() - endRoom.getCenter().getZ(), 2));

            // Calculate distance from spawn room
            DungeonRoom spawnRoom = rooms.get(0);
            double distanceFromSpawn = Math.sqrt(Math.pow(room.getCenter().getX() - spawnRoom.getCenter().getX(), 2)
                    + Math.pow(room.getCenter().getZ() - spawnRoom.getCenter().getZ(), 2));

            // Score based on: closer to end room = better, further from spawn = better
            // But not too close to end room (minimum 40 blocks)
            if (distanceToEnd > 40 && distanceToEnd < 100) {
                double score = distanceFromSpawn / (distanceToEnd + 1);

                if (score > bestScore) {
                    bestScore = score;
                    bestRoom = room;
                }
            }
        }

        return bestRoom;
    }

    private Direction findAvailableDirection(DungeonRoom fromRoom, Point3D toCenter) {
        // Find the best direction toward the target
        Point3D fromCenter = fromRoom.getCenter();
        int deltaX = toCenter.getX() - fromCenter.getX();
        int deltaZ = toCenter.getZ() - fromCenter.getZ();

        // Prioritize directions based on delta
        List<Direction> prioritizedDirections = new ArrayList<>();

        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            // Horizontal movement is primary
            prioritizedDirections.add(deltaX > 0 ? Direction.EAST : Direction.WEST);
            prioritizedDirections.add(deltaZ > 0 ? Direction.SOUTH : Direction.NORTH);
        } else {
            // Vertical movement is primary
            prioritizedDirections.add(deltaZ > 0 ? Direction.SOUTH : Direction.NORTH);
            prioritizedDirections.add(deltaX > 0 ? Direction.EAST : Direction.WEST);
        }

        // Add remaining directions
        for (Direction dir : Direction.values()) {
            if (!prioritizedDirections.contains(dir)) {
                prioritizedDirections.add(dir);
            }
        }

        // Return first available direction
        for (Direction dir : prioritizedDirections) {
            if (!fromRoom.getConnections().containsKey(dir)) {
                return dir;
            }
        }

        return null; // No available direction
    }

    private void createFallbackEndConnection(EndPortalRoom endRoom) {
        // Find any room with available connections
        for (int i = rooms.size() - 2; i >= 1; i--) { // Skip spawn room and end room
            DungeonRoom room = rooms.get(i);

            for (Direction dir : Direction.values()) {
                if (!room.getConnections().containsKey(dir)) {
                    createDirectConnection(room, endRoom);
                    return;
                }
            }
        }
    }

    private void createDirectConnection(DungeonRoom fromRoom, EndPortalRoom endRoom) {
        // Find best direction for connection
        Direction bestDir = Direction.NORTH; // Default

        for (Direction dir : Direction.values()) {
            if (!fromRoom.getConnections().containsKey(dir)) {
                bestDir = dir;
                break;
            }
        }

        // Create long tunnel connection
        DungeonTunnel endTunnel = TunnelFactory.createRandomTunnel("tunnel_to_end_fallback", fromRoom, endRoom, bestDir,
                random);

        fromRoom.addConnection(endRoom, bestDir);
        endTunnel.clearRoomEntrances();
        tunnels.add(endTunnel);
    }

    private Point3D calculateNewRoomCenter(DungeonRoom currentRoom, Direction direction) {
        int distance = currentRoom.getSize() + (int) (15 + random.nextInt(10) * Math.log(roomCounter)); // Spacing
                                                                                                        // between rooms
        return direction.apply(currentRoom.getCenter(), distance);
    }

    private boolean isValidPosition(DungeonRoom currentRoom) {
        Point3D center = currentRoom.getCenter();

        int minDistance = currentRoom.getSize() / 2 - 1; // Minimum distance between room centers
        for (DungeonRoom room : rooms) {

            double distance = Math.sqrt(Math.pow(center.getX() - room.getCenter().getX(), 2)
                    + Math.pow(center.getZ() - room.getCenter().getZ(), 2));

            if (distance < minDistance) {
                return false;
            }
        }

        return true;
    }

}
