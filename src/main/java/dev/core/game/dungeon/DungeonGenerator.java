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

        // Generate first room
        DungeonRoom startRoom = RoomFactory.createRandomRoom("room_" + (++roomCounter), startCenter, random);
        rooms.add(startRoom);

        // Generate additional rooms
        Queue<DungeonRoom> roomsToExpand = new LinkedList<>();
        roomsToExpand.add(startRoom);

        while (rooms.size() < roomCount && !roomsToExpand.isEmpty()) {
            DungeonRoom currentRoom = roomsToExpand.poll();

            // Try to add 1-3 connections from this room
            int connectionsToAdd = random.nextInt(3) + 1;
            List<Direction> availableDirections = new ArrayList<>();

            for (Direction dir : Direction.values()) {
                if (!currentRoom.getConnections().containsKey(dir)) {
                    availableDirections.add(dir);
                }
            }

            Collections.shuffle(availableDirections, random);

            for (int i = 0; i < Math.min(connectionsToAdd, availableDirections.size())
                    && rooms.size() < roomCount; i++) {
                Direction dir = availableDirections.get(i);
                Point3D newCenter = calculateNewRoomCenter(currentRoom, dir);

                // Check if position is valid (no overlap with existing rooms)
                if (isValidPosition(newCenter)) {
                    DungeonRoom newRoom = RoomFactory.createRandomRoom("room_" + (++roomCounter), newCenter, random);

                    // Create tunnel connection
                    DungeonTunnel tunnel = TunnelFactory.createRandomTunnel("tunnel_" + (++tunnelCounter), currentRoom,
                            newRoom, dir, random);

                    // Add room connection
                    currentRoom.addConnection(newRoom, dir);

                    // Clear room entrances for the tunnel
                    tunnel.clearRoomEntrances();

                    rooms.add(newRoom);
                    tunnels.add(tunnel);
                    roomsToExpand.add(newRoom);
                }
            }
        }

        return new Dungeon(new ArrayList<>(rooms), new ArrayList<>(tunnels));
    }

    private Point3D calculateNewRoomCenter(DungeonRoom currentRoom, Direction direction) {
        int distance = currentRoom.getSize() + 15 + random.nextInt(10); // Spacing between rooms
        return direction.apply(currentRoom.getCenter(), distance);
    }

    private boolean isValidPosition(Point3D center) {
        int minDistance = 20; // Minimum distance between room centers

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
