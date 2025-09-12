package dev.core.game.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DungeonTunnel {

    protected final String id;
    protected final TunnelConnection startConnection;
    protected final TunnelConnection endConnection;
    protected final int width;
    protected final int height;
    protected final Set<Point3D> floorBlocks;
    protected final Set<Point3D> wallBlocks;
    protected final Set<Point3D> roofBlocks;
    protected final Set<Point3D> airBlocks;
    protected final List<Point3D> centerPath;
    protected final BoundingBox boundingBox;

    public DungeonTunnel(String id, TunnelConnection startConnection, TunnelConnection endConnection, int width) {
        this.id = id;
        this.startConnection = startConnection;
        this.endConnection = endConnection;
        this.width = Math.max(1, width); // Ensure minimum width of 1
        // Height is the minimum of the two connected rooms
        this.height = Math.min(startConnection.getRoomHeight(), endConnection.getRoomHeight());
        this.floorBlocks = new HashSet<>();
        this.wallBlocks = new HashSet<>();
        this.roofBlocks = new HashSet<>();
        this.airBlocks = new HashSet<>();
        this.centerPath = new ArrayList<>();

        Point3D start = startConnection.getConnectionPoint();
        Point3D end = endConnection.getConnectionPoint();
        boundingBox = new BoundingBox(start, end);

        generateTunnel();
        createConnectionFillers();
    }

    protected abstract void generateTunnel();

    protected abstract TunnelType getType();

    protected void generateTunnelStructure(List<Point3D> pathPoints) {
        this.centerPath.addAll(pathPoints);

        for (Point3D pathPoint : pathPoints) {
            createTunnelCrossSection(pathPoint);
        }

        // Add walls along the tunnel sides
        addTunnelWalls();
    }

    private void createTunnelCrossSection(Point3D centerPoint) {
        Direction tunnelDirection = calculateTunnelDirection(centerPoint);
        Direction[] perpendiculars = getPerpendicularDirections(tunnelDirection);

        int halfWidth = (int) Math.ceil(width / 2.0);

        // Create floor, air, and roof for the cross-section
        for (int w = -halfWidth; w <= halfWidth; w++) {
            Point3D crossPoint = perpendiculars[0].apply(centerPoint, w);

            // Floor
            Point3D floorPos = new Point3D(crossPoint.getX(), centerPoint.getY() - 1, crossPoint.getZ());
            floorBlocks.add(floorPos);

            // Air space
            for (int h = 0; h < height; h++) {
                Point3D airPos = new Point3D(crossPoint.getX(), centerPoint.getY() + h, crossPoint.getZ());
                airBlocks.add(airPos);
            }

            // Roof
            Point3D roofPos = new Point3D(crossPoint.getX(), centerPoint.getY() + height, crossPoint.getZ());
            roofBlocks.add(roofPos);
        }
    }

    private void addTunnelWalls() {
        Set<Point3D> potentialWalls = new HashSet<>();

        for (Point3D airBlock : airBlocks) {
            // Check all horizontal directions for potential wall positions
            for (Direction dir : Direction.values()) {
                Point3D adjacent = dir.apply(airBlock, 1);

                // If this adjacent position is not part of the tunnel air space, it should be a
                // wall
                if (!airBlocks.contains(adjacent) && !isRoomAirSpace(adjacent)) {
                    potentialWalls.add(adjacent);
                }
            }
        }

        wallBlocks.addAll(potentialWalls);
    }

    private boolean isRoomAirSpace(Point3D point) {
        return startConnection.getRoom().getAirBlocks().contains(point)
                || endConnection.getRoom().getAirBlocks().contains(point);
    }

    private Direction calculateTunnelDirection(Point3D point) {
        int index = centerPath.indexOf(point);

        if (index < centerPath.size() - 1) {
            Point3D next = centerPath.get(index + 1);
            int deltaX = next.getX() - point.getX();
            int deltaZ = next.getZ() - point.getZ();

            if (Math.abs(deltaX) > Math.abs(deltaZ)) {
                return deltaX > 0 ? Direction.EAST : Direction.WEST;
            } else {
                return deltaZ > 0 ? Direction.SOUTH : Direction.NORTH;
            }
        }

        // Default fallback
        return Direction.NORTH;
    }

    private void createConnectionFillers() {
        createConnectionFiller(startConnection);
        createConnectionFiller(endConnection);
    }

    private void createConnectionFiller(TunnelConnection connection) {
        if (!connection.needsFillerWalls()) {
            return;
        }

        Point3D connectionPoint = connection.getConnectionPoint();
        Direction direction = connection.getDirection();
        int fillerHeight = connection.getFillerWallHeight();

        // Create filler walls from tunnel height to room height
        int halfWidth = (int) Math.ceil(width / 2.0);
        Direction[] perpendiculars = getPerpendicularDirections(direction);

        for (int w = -halfWidth; w <= halfWidth; w++) {
            Point3D crossPoint = perpendiculars[0].apply(connectionPoint, w);

            for (int h = height; h < fillerHeight; h++) {
                Point3D fillerPos = new Point3D(crossPoint.getX(), connectionPoint.getY() + h, crossPoint.getZ());
                wallBlocks.add(fillerPos);
            }
        }
    }

    protected Direction[] getPerpendicularDirections(Direction dir) {
        switch (dir) {
        case NORTH:
        case SOUTH:
            return new Direction[] { Direction.EAST, Direction.WEST };
        case EAST:
        case WEST:
            return new Direction[] { Direction.NORTH, Direction.SOUTH };
        default:
            return new Direction[] { Direction.EAST, Direction.WEST };
        }
    }

    public void clearRoomEntrances() {
        clearRoomEntrance(startConnection);
        clearRoomEntrance(endConnection);
    }

    private void clearRoomEntrance(TunnelConnection connection) {
        DungeonRoom room = connection.getRoom();
        Point3D connectionPoint = connection.getConnectionPoint();
        Direction direction = connection.getDirection();

        // Calculate the exact entrance area that needs to be cleared
        Direction[] perpendiculars = getPerpendicularDirections(direction);
        int halfWidth = width / 2;

        Set<Point3D> wallsToRemove = new HashSet<>();
        Set<Point3D> airToAdd = new HashSet<>();

        for (int w = -halfWidth; w <= halfWidth; w++) {
            Point3D entrancePoint = perpendiculars[0].apply(connectionPoint, w);

            // Only clear if this point is actually accessible in the room
            if (isValidEntrancePoint(room, entrancePoint, direction)) {
                for (int h = 0; h < height; h++) {
                    Point3D wallPos = new Point3D(entrancePoint.getX(), connectionPoint.getY() + h,
                            entrancePoint.getZ());
                    wallsToRemove.add(wallPos);
                    airToAdd.add(wallPos);
                }
            }
        }

        // Apply changes to room
        room.getWallBlocks().removeAll(wallsToRemove);
        room.getAirBlocks().addAll(airToAdd);
    }

    private boolean isValidEntrancePoint(DungeonRoom room, Point3D entrancePoint, Direction direction) {
        // Check if the entrance point connects to a valid floor position in the room
        Point3D floorCheck = new Point3D(entrancePoint.getX(), room.getCenter().getY() - 1, entrancePoint.getZ());

        if (!room.getFloorBlocks().contains(floorCheck)) {
            return false;
        }

        // For L-shaped and round rooms, ensure the entrance aligns with the room
        // geometry
        if (room.getType() == RoomType.L_SHAPED_ROOM || room.getType() == RoomType.CIRCULAR_ROOM) {
            // Move one step into the room to check if it's a valid interior space
            Point3D interiorCheck = direction.opposite().apply(entrancePoint, 1);
            Point3D interiorFloor = new Point3D(interiorCheck.getX(), room.getCenter().getY() - 1,
                    interiorCheck.getZ());
            return room.getFloorBlocks().contains(interiorFloor);
        }

        return true;
    }

    // Getters
    public String getId() {
        return id;
    }

    public TunnelConnection getStartConnection() {
        return startConnection;
    }

    public TunnelConnection getEndConnection() {
        return endConnection;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Set<Point3D> getFloorBlocks() {
        return new HashSet<>(floorBlocks);
    }

    public Set<Point3D> getWallBlocks() {
        return new HashSet<>(wallBlocks);
    }

    public Set<Point3D> getRoofBlocks() {
        return new HashSet<>(roofBlocks);
    }

    public Set<Point3D> getAirBlocks() {
        return new HashSet<>(airBlocks);
    }

    public List<Point3D> getCenterPath() {
        return new ArrayList<>(centerPath);
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

}
