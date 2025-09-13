package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DungeonGenerator {

    private final Random random;
    private final List<DungeonRoom> rooms;
    private final List<DungeonTunnel> tunnels;
    private int roomCounter;
    private int tunnelCounter;
    private int maxTunnelDistance = 40;

    public DungeonGenerator(long seed) {
        this.random = new Random(seed);
        this.rooms = new ArrayList<>();
        this.tunnels = new ArrayList<>();
        this.roomCounter = 0;
        this.tunnelCounter = 0;
    }

    public void setMaxTunnelDistance(int maxTunnelDistance) {
        this.maxTunnelDistance = maxTunnelDistance;
    }

    public Dungeon generateDungeon(int roomCount, Point3D startCenter) {
        rooms.clear();
        tunnels.clear();
        roomCounter = 0;
        tunnelCounter = 0;

        // Step 1: Generate spawn room
        SpawnRoom spawnRoom = new SpawnRoom("spawn_room", startCenter, 14);
        spawnRoom.setBoundingBox(createRoomBoundingBox(spawnRoom));
        rooms.add(spawnRoom);
        roomCounter++;
        System.out.println("Generated spawn room");

        // Step 2: Create the main path to end room (guaranteed connectivity)
        List<DungeonRoom> mainPath = generateMainPath(spawnRoom, roomCount);
        System.out.println("Generated main path with " + mainPath.size() + " rooms");

        // Step 3: Add branches and dead ends to remaining connection points
        addBranchesAndDeadEnds(mainPath, roomCount);

        System.out.println("Final dungeon: " + rooms.size() + " rooms, " + tunnels.size() + " tunnels");
        return new Dungeon(new ArrayList<>(rooms), new ArrayList<>(tunnels));
    }

    private List<DungeonRoom> generateMainPath(SpawnRoom spawnRoom, int totalRoomCount) {
        List<DungeonRoom> mainPath = new ArrayList<>();
        mainPath.add(spawnRoom);

        // Calculate how many rooms should be on the main path (roughly 60-80% of total)
        int mainPathRooms = Math.max(2, (int) (totalRoomCount * 0.6)) - 1; // Subtract 1 for end room

        DungeonRoom currentRoom = spawnRoom;

        // Generate main path rooms
        for (int i = 0; i < mainPathRooms; i++) {
            DungeonRoom newRoom = generateNextRoomForMainPath(currentRoom, i);
            if (newRoom == null) {
                System.out.println("Failed to generate main path room " + (i + 1));
                break;
            }

            Direction connectionDir = findBestConnectionDirection(currentRoom, newRoom);
            DungeonTunnel tunnel = createTunnel(currentRoom, newRoom, connectionDir);

            if (tunnel == null) {
                System.out.println("Failed to create main path tunnel");
                break;
            }

            rooms.add(newRoom);
            tunnels.add(tunnel);
            mainPath.add(newRoom);
            currentRoom = newRoom;

            System.out.println("Main path room " + rooms.size() + " generated");
        }

        // Generate end room and connect to last main path room
        EndPortalRoom endRoom = generateEndRoom(currentRoom);
        Direction endConnectionDir = findBestConnectionDirection(currentRoom, endRoom);
        DungeonTunnel endTunnel = createTunnel(currentRoom, endRoom, endConnectionDir);

        rooms.add(endRoom);
        tunnels.add(endTunnel);
        mainPath.add(endRoom);

        return mainPath;
    }

    private void addBranchesAndDeadEnds(List<DungeonRoom> mainPath, int totalRoomCount) {
        int roomsToAdd = totalRoomCount - rooms.size();
        int roomsAdded = 0;

        // Shuffle main path to randomize which rooms get branches
        List<DungeonRoom> branchCandidates = new ArrayList<>(mainPath);
        // Remove end room from candidates (it shouldn't have branches)
        branchCandidates
                .removeIf(room -> room.getType() == RoomType.END_PORTAL_ROOM || room.getType() == RoomType.SPAWN_ROOM);
        Collections.shuffle(branchCandidates, random);

        for (DungeonRoom branchRoot : branchCandidates) {
            if (roomsAdded >= roomsToAdd) {
                break;
            }

            // Each room can have multiple branches, but limit to available directions
            List<Direction> availableDirections = getAvailableDirectionsForRoom(branchRoot);

            // Randomly decide how many branches this room should have (0-2)
            int branchCount = Math.min(availableDirections.size(), random.nextInt(3)); // 0, 1, or 2 branches

            if (branchCount == 0) {
                continue;
            }

            Collections.shuffle(availableDirections, random);

            for (int b = 0; b < branchCount && roomsAdded < roomsToAdd; b++) {
                Direction branchDir = availableDirections.get(b);

                // Generate a branch (can be 1-3 rooms deep)
                int branchLength = 1 + random.nextInt(Math.min(3, roomsToAdd - roomsAdded));
                List<DungeonRoom> branch = generateBranch(branchRoot, branchDir, branchLength);

                roomsAdded += branch.size();
                System.out.println("Added branch of length " + branch.size() + " from room " + branchRoot.getId());
            }
        }
    }

    private List<DungeonRoom> generateBranch(DungeonRoom branchRoot, Direction startDirection, int maxLength) {
        List<DungeonRoom> branch = new ArrayList<>();
        DungeonRoom currentRoom = branchRoot;
        Direction currentDirection = startDirection;

        for (int depth = 0; depth < maxLength; depth++) {
            DungeonRoom newRoom = generateBranchRoom(currentRoom, currentDirection, depth);
            if (newRoom == null) {
                System.out.println("Failed to generate branch room at depth " + depth);
                break;
            }

            DungeonTunnel tunnel = createTunnel(currentRoom, newRoom, currentDirection);
            if (tunnel == null) {
                System.out.println("Failed to create branch tunnel at depth " + depth);
                break;
            }

            rooms.add(newRoom);
            tunnels.add(tunnel);
            branch.add(newRoom);

            // For next iteration, try to continue the branch in a different direction
            if (depth < maxLength - 1) {
                List<Direction> nextDirections = getAvailableDirectionsForRoom(newRoom);
                if (nextDirections.isEmpty()) {
                    System.out.println("Branch ends at depth " + (depth + 1) + " - no available directions");
                    break; // Dead end
                }

                // Prefer continuing in the same general direction, but allow turns
                currentDirection = chooseBranchDirection(currentDirection, nextDirections);
                currentRoom = newRoom;
            }
        }

        return branch;
    }

    private Direction chooseBranchDirection(Direction previousDir, List<Direction> available) {
        // Prefer continuing straight, but allow 90-degree turns
        List<Direction> preferred = new ArrayList<>();
        List<Direction> acceptable = new ArrayList<>();

        for (Direction dir : available) {
            if (dir == previousDir) {
                preferred.add(dir); // Continue straight
            } else if (dir != previousDir.opposite()) {
                acceptable.add(dir); // 90-degree turn (not backtracking)
            }
        }

        // 70% chance to go straight if possible, otherwise turn
        if (!preferred.isEmpty() && random.nextDouble() < 0.7) {
            return preferred.get(0);
        } else if (!acceptable.isEmpty()) {
            return acceptable.get(random.nextInt(acceptable.size()));
        } else {
            // Fallback: any available direction
            return available.get(random.nextInt(available.size()));
        }
    }

    private DungeonRoom generateNextRoomForMainPath(DungeonRoom currentRoom, int attemptNumber) {
        List<Direction> availableDirections = getAvailableDirectionsForRoom(currentRoom);

        for (Direction dir : availableDirections) {
            Point3D newRoomCenter = calculateNewRoomCenter(currentRoom, dir);

            // Estimate tunnel width that will be used (you may want to make this more
            // precise)
            int estimatedTunnelWidth = 3 + random.nextInt(3); // Width between 3-5

            // Main path rooms - use the enhanced room creation method that considers
            // connections
            DungeonRoom newRoom = RoomFactory.createRoomWithConnectionDirection("main_room_" + (roomCounter + 1),
                    newRoomCenter, random, dir.opposite(), // The new room needs to connect back to current room
                    attemptNumber > 5, estimatedTunnelWidth);

            if (isValidRoomPlacement(newRoom) && canRoomConnect(newRoom, dir.opposite())) {
                newRoom.setBoundingBox(createRoomBoundingBox(newRoom));
                roomCounter++;
                return newRoom;
            }
        }

        return null;
    }

    private DungeonRoom generateBranchRoom(DungeonRoom currentRoom, Direction direction, int depth) {
        Point3D newRoomCenter = calculateNewRoomCenter(currentRoom, direction);

        // Branch rooms can be smaller and more varied
        boolean favorSmaller = depth > 0; // Deeper branch rooms tend to be smaller
        String roomId = "branch_room_" + (roomCounter + 1);

        // Estimate tunnel width that will be used
        int estimatedTunnelWidth = 3 + random.nextInt(3); // Width between 3-5

        // Use enhanced room creation that considers connection requirements
        DungeonRoom newRoom = RoomFactory.createRoomWithConnectionDirection(roomId, newRoomCenter, random,
                direction.opposite(), // New room needs to connect back to current room
                favorSmaller, estimatedTunnelWidth);

        if (isValidRoomPlacement(newRoom) && canRoomConnect(newRoom, direction.opposite())) {
            newRoom.setBoundingBox(createRoomBoundingBox(newRoom));
            roomCounter++;
            return newRoom;
        }

        return null;
    }

    private DungeonTunnel createTunnel(DungeonRoom from, DungeonRoom to, Direction direction) {
        // Validate that both rooms can actually connect in the specified direction
        if (!canRoomConnect(from, direction) || !canRoomConnect(to, direction.opposite())) {
            System.out.println("Cannot create tunnel: room connection validation failed");
            System.out.println("From room " + from.getId() + " (" + from.getType() + ") in direction " + direction);
            System.out
                    .println("To room " + to.getId() + " (" + to.getType() + ") in direction " + direction.opposite());
            return null;
        }

        // If connecting to EndPortalRoom, set the connection direction to avoid altar
        // blocking
        if (to instanceof EndPortalRoom) {
            ((EndPortalRoom) to).setConnectionDirection(direction.opposite());
        }

        // Calculate tunnel width based on room sizes to ensure compatibility
        int maxAllowableWidth = (int) Math.floor(Math.min(from.getSize(), to.getSize()) / 1.5);
        int tunnelWidth = Math.min(3 + random.nextInt(3), maxAllowableWidth); // Ensure width constraint

        DungeonTunnel tunnel = TunnelFactory.createAlignedTunnelWithWidth("tunnel_" + (++tunnelCounter), from, to,
                direction, random, maxTunnelDistance, tunnelWidth);

        if (tunnel != null) {
            from.addConnection(to, direction);
            tunnel.clearRoomEntrances();
            tunnel.setBoundingBox(createTunnelBoundingBox(tunnel));
        } else {
            tunnelCounter--; // Revert counter if tunnel creation failed
        }

        return tunnel;
    }

    /**
     * Check if a room can connect in the specified direction This is especially
     * important for L-shaped rooms with limited connection points
     */
    private boolean canRoomConnect(DungeonRoom room, Direction direction) {
        if (room instanceof LShapedRoom) {
            LShapedRoom lRoom = (LShapedRoom) room;
            return lRoom.isArmDirection(direction);
        }
        // Other room types can connect in any direction
        return true;
    }

    // Enhanced room validation for more complex layouts
    private boolean isValidRoomPlacement(DungeonRoom newRoom) {
        BoundingBox newBox = createRoomBoundingBox(newRoom);

        // Check against all existing rooms with appropriate separation
        for (DungeonRoom existing : rooms) {
            BoundingBox existingBox = existing.getBoundingBox();

            // Smaller separation for branch rooms to allow denser layouts
            int minSeparation = (newRoom.getSize() <= 8) ? 4 : 6;

            if (!newBox.hasMinimumSeparationFrom(existingBox, minSeparation)) {
                return false;
            }
        }

        // Check against tunnels
        for (DungeonTunnel tunnel : tunnels) {
            BoundingBox tunnelBox = tunnel.getBoundingBox();
            if (tunnelBox != null && !newBox.hasMinimumSeparationFrom(tunnelBox, 3)) {
                return false;
            }
        }

        return true;
    }

    private boolean canHaveMoreConnections(DungeonRoom room) {
        int maxConnections = getMaxConnectionsForRoomType(room.getType());
        return room.getConnections().size() < maxConnections;
    }

    private int getMaxConnectionsForRoomType(RoomType type) {
        switch (type) {
        case SPAWN_ROOM:
            return 3; // Can branch out from spawn
        case L_SHAPED_ROOM:
            return 2; // Limited to exactly 2 connections (one per arm)
        case CIRCULAR_ROOM:
        case SQUARE_ROOM:
            return 4; // Can connect in all directions
        case END_PORTAL_ROOM:
            return 1; // Should only have one entrance
        default:
            return 2;
        }
    }

    private List<Direction> getAvailableDirectionsForRoom(DungeonRoom room) {
        List<Direction> available = new ArrayList<>();

        switch (room.getType()) {
        case SPAWN_ROOM:
        case END_PORTAL_ROOM:
            // These rooms can connect in any direction that isn't already used
            for (Direction dir : Direction.values()) {
                if (!room.getConnections().containsKey(dir)) {
                    available.add(dir);
                }
            }
            break;

        case L_SHAPED_ROOM:
            // L-shaped rooms have exactly two possible connection directions (their arms)
            LShapedRoom lRoom = (LShapedRoom) room;
            for (Direction armDir : lRoom.getAvailableConnectionDirections()) {
                if (!room.getConnections().containsKey(armDir)) {
                    available.add(armDir);
                }
            }
            break;

        default:
            // Square and circular rooms can connect in all directions
            for (Direction dir : Direction.values()) {
                if (!room.getConnections().containsKey(dir)) {
                    available.add(dir);
                }
            }
            break;
        }

        Collections.shuffle(available, random);
        return available;
    }

    private Point3D calculateNewRoomCenter(DungeonRoom currentRoom, Direction direction) {
        // Get current room's connection point
        Point3D connectionPoint = currentRoom.getConnectionPoint(direction);

        // Calculate distance based on room sizes and constraints
        int distance = calculateOptimalDistance(currentRoom);

        // Move in the specified direction
        Point3D newConnectionPoint = direction.apply(connectionPoint, distance);

        // Estimate new room size to calculate center offset
        int estimatedSize = 8 + random.nextInt(4); // 8-12
        int offset = estimatedSize / 2;

        // Calculate center by moving back from the connection point
        Point3D centerOffset = direction.opposite().apply(new Point3D(0, 0, 0), offset);

        return new Point3D(newConnectionPoint.getX() - centerOffset.getX(), currentRoom.getCenter().getY(), // Keep same
                                                                                                            // Y level
                newConnectionPoint.getZ() - centerOffset.getZ());
    }

    private int calculateOptimalDistance(DungeonRoom currentRoom) {
        // Base distance that ensures rooms don't overlap
        int baseDistance = Math.max(currentRoom.getSize() / 2 + 8, 15);

        // Add variation but respect max tunnel distance
        int variation = random.nextInt(8) - 4; // -4 to +4
        int distance = baseDistance + variation;

        // Ensure we don't exceed max tunnel distance
        return Math.min(distance, maxTunnelDistance - 5);
    }

    private Direction findBestConnectionDirection(DungeonRoom from, DungeonRoom to) {
        Point3D fromCenter = from.getCenter();
        Point3D toCenter = to.getCenter();

        int deltaX = toCenter.getX() - fromCenter.getX();
        int deltaZ = toCenter.getZ() - fromCenter.getZ();

        // Choose direction based on larger delta
        Direction preferredDirection;
        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            preferredDirection = deltaX > 0 ? Direction.EAST : Direction.WEST;
        } else {
            preferredDirection = deltaZ > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // Check if both rooms can connect in this direction
        if (canRoomConnect(from, preferredDirection) && canRoomConnect(to, preferredDirection.opposite())) {
            return preferredDirection;
        }

        // If preferred direction doesn't work, try other directions
        // This is especially important for L-shaped rooms
        List<Direction> fromAvailable = getAvailableDirectionsForRoom(from);
        List<Direction> toAvailable = getAvailableDirectionsForRoom(to);

        // Find a direction that works for both rooms
        for (Direction fromDir : fromAvailable) {
            Direction toDir = fromDir.opposite();
            if (toAvailable.contains(toDir)) {
                return fromDir;
            }
        }

        // Fallback to preferred direction if nothing else works
        return preferredDirection;
    }

    private EndPortalRoom generateEndRoom(DungeonRoom lastRoom) {
        // Place end room in a direction that doesn't conflict with existing connections
        List<Direction> availableDirections = getAvailableDirectionsForRoom(lastRoom);

        Direction endDirection = availableDirections.isEmpty() ? Direction.NORTH : availableDirections.get(0);

        Point3D endCenter = calculateNewRoomCenter(lastRoom, endDirection);

        // Move end room further away for dramatic effect
        endCenter = endDirection.apply(endCenter, 20);

        EndPortalRoom endRoom = new EndPortalRoom("end_portal_room", endCenter, 18);
        endRoom.setBoundingBox(createRoomBoundingBox(endRoom));

        return endRoom;
    }

    private BoundingBox createRoomBoundingBox(DungeonRoom room) {
        Point3D center = room.getCenter();
        int halfSize = room.getSize() / 2;
        int height = room.getHeight();

        if (room.getType() == RoomType.L_SHAPED_ROOM) {
            return createLShapedRoomBoundingBox((LShapedRoom) room);
        } else if (room.getType() == RoomType.CIRCULAR_ROOM) {
            return createCircularRoomBoundingBox((RoundRoom) room);
        } else {
            Point3D min = new Point3D(center.getX() - halfSize, center.getY() - 1, center.getZ() - halfSize);
            Point3D max = new Point3D(center.getX() + halfSize, center.getY() + height, center.getZ() + halfSize);
            return new BoundingBox(min, max);
        }
    }

    private BoundingBox createLShapedRoomBoundingBox(LShapedRoom room) {
        Point3D center = room.getCenter();
        int halfSize = room.getSize() / 2;
        int height = room.getHeight();

        Point3D min = new Point3D(center.getX() - halfSize, center.getY() - 1, center.getZ() - halfSize);
        Point3D max = new Point3D(center.getX() + halfSize, center.getY() + height, center.getZ() + halfSize);
        return new BoundingBox(min, max);
    }

    private BoundingBox createCircularRoomBoundingBox(RoundRoom room) {
        Point3D center = room.getCenter();
        int radius = room.getSize() / 2;
        int height = room.getHeight();

        Point3D min = new Point3D(center.getX() - radius, center.getY() - 1, center.getZ() - radius);
        Point3D max = new Point3D(center.getX() + radius, center.getY() + height, center.getZ() + radius);
        return new BoundingBox(min, max);
    }

    private BoundingBox createTunnelBoundingBox(DungeonTunnel tunnel) {
        if (tunnel instanceof LShapedTunnel) {
            LShapedTunnel lTunnel = (LShapedTunnel) tunnel;
            List<BoundingBox> segments = lTunnel.getBoundingBoxes();

            if (segments.isEmpty()) {
                return tunnel.getBoundingBox();
            }

            BoundingBox first = segments.get(0);
            int minX = first.minX, minY = first.minY, minZ = first.minZ;
            int maxX = first.maxX, maxY = first.maxY, maxZ = first.maxZ;

            for (BoundingBox segment : segments) {
                minX = Math.min(minX, segment.minX);
                minY = Math.min(minY, segment.minY);
                minZ = Math.min(minZ, segment.minZ);
                maxX = Math.max(maxX, segment.maxX);
                maxY = Math.max(maxY, segment.maxY);
                maxZ = Math.max(maxZ, segment.maxZ);
            }

            return new BoundingBox(new Point3D(minX, minY, minZ), new Point3D(maxX, maxY, maxZ));
        } else {
            return tunnel.getEnhancedBoundingBox();
        }
    }
}