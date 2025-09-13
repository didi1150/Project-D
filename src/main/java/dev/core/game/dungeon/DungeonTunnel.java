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
    protected BoundingBox boundingBox;

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

        generateTunnel();
        createConnectionFillers();
    }

    public void setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
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

    /**
     * Improved room entrance clearing that prevents holes in exterior walls
     */
    public void clearRoomEntrances() {
        clearRoomEntrance(startConnection);
        clearRoomEntrance(endConnection);
    }

    private void clearRoomEntrance(TunnelConnection connection) {
        System.out.println("Clearing entrance for: " + connection.getRoom().getId());

        DungeonRoom room = connection.getRoom();
        Point3D connectionPoint = connection.getConnectionPoint();
        Direction direction = connection.getDirection();

        // Verify that our tunnel path actually reaches this connection point
        if (!centerPath.contains(connectionPoint)) {
            System.err.println("ERROR: Tunnel path doesn't include connection point: " + connectionPoint);
            System.err.println("Path start: " + centerPath.get(0));
            System.err.println("Path end: " + centerPath.get(centerPath.size() - 1));
            // Add the connection point to the path if missing
            if (connection == startConnection && !centerPath.get(0).equals(connectionPoint)) {
                centerPath.add(0, connectionPoint);
            } else if (connection == endConnection && !centerPath.get(centerPath.size() - 1).equals(connectionPoint)) {
                centerPath.add(connectionPoint);
            }
        }

        Direction[] perpendiculars = getPerpendicularDirections(direction);
        int halfWidth = width / 2;

        Set<Point3D> wallsToRemove = new HashSet<>();
        Set<Point3D> airToAdd = new HashSet<>();

        // Clear entrance area based on tunnel width
        for (int w = -halfWidth; w <= halfWidth; w++) {
            Point3D entrancePoint = perpendiculars[0].apply(connectionPoint, w);

            // Validate this is a proper entrance point
            if (isValidEntrancePoint(room, entrancePoint, direction)) {
                for (int h = 0; h < height; h++) {
                    Point3D wallPos = new Point3D(entrancePoint.getX(), connectionPoint.getY() + h,
                            entrancePoint.getZ());

                    if (room.getWallBlocks().contains(wallPos)) {
                        wallsToRemove.add(wallPos);
                        airToAdd.add(wallPos);
                    }
                }
            }
        }

        room.removeWallsForAirBlocks(wallsToRemove, airToAdd);
        System.out.println("Cleared " + wallsToRemove.size() + " wall blocks for entrance");
    }

    private boolean isValidEntrancePoint(DungeonRoom room, Point3D entrancePoint, Direction direction) {
        Point3D roomCenter = room.getCenter();

        // Check if there's a floor block at this entrance position
        Point3D floorCheck = new Point3D(entrancePoint.getX(), roomCenter.getY() - 1, entrancePoint.getZ());
        if (!room.getFloorBlocks().contains(floorCheck)) {
            return false;
        }

        // Check if there's interior space behind the entrance
        Point3D interiorCheck = direction.opposite().apply(entrancePoint, 1);
        Point3D interiorFloor = new Point3D(interiorCheck.getX(), roomCenter.getY() - 1, interiorCheck.getZ());

        return room.getFloorBlocks().contains(interiorFloor);
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

    public BoundingBox getEnhancedBoundingBox() {
        if (centerPath.isEmpty()) {
            return boundingBox; // Fallback to original
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        int halfWidth = (int) Math.ceil(width / 2.0);

        // For each point in the tunnel path, calculate the area it occupies
        for (Point3D pathPoint : centerPath) {
            Direction tunnelDirection = calculateTunnelDirection(pathPoint);
            Direction[] perpendiculars = getPerpendicularDirections(tunnelDirection);

            // Calculate the bounds for this cross-section
            for (int w = -halfWidth; w <= halfWidth; w++) {
                Point3D crossPoint = perpendiculars[0].apply(pathPoint, w);

                minX = Math.min(minX, crossPoint.getX());
                maxX = Math.max(maxX, crossPoint.getX());
                minZ = Math.min(minZ, crossPoint.getZ());
                maxZ = Math.max(maxZ, crossPoint.getZ());
            }

            // Account for tunnel height
            minY = Math.min(minY, pathPoint.getY() - 1); // Floor
            maxY = Math.max(maxY, pathPoint.getY() + height); // Roof
        }

        Point3D min = new Point3D(minX, minY, minZ);
        Point3D max = new Point3D(maxX, maxY, maxZ);
        return new BoundingBox(min, max);
    }
}