package dev.core.game.dungeon;

public class LShapedRoom extends DungeonRoom {

    // Internal orientation to determine arm directions
    public enum LOrientation {
        NORTH_EAST, // Arms extend North and East
        NORTH_WEST, // Arms extend North and West
        SOUTH_EAST, // Arms extend South and East
        SOUTH_WEST // Arms extend South and West
    }

    private final LOrientation orientation;
    private final Direction arm1Direction;
    private final Direction arm2Direction;

    public LShapedRoom(String id, Point3D center, int size, int height) {
        this(id, center, size, height, LOrientation.NORTH_EAST); // Default orientation
    }

    public LShapedRoom(String id, Point3D center, int size, int height, LOrientation orientation) {
        super(id, center, size, height);
        this.orientation = orientation;

        // Set arm directions based on orientation
        switch (orientation) {
        case NORTH_EAST:
            this.arm1Direction = Direction.NORTH;
            this.arm2Direction = Direction.EAST;
            break;
        case NORTH_WEST:
            this.arm1Direction = Direction.NORTH;
            this.arm2Direction = Direction.WEST;
            break;
        case SOUTH_EAST:
            this.arm1Direction = Direction.SOUTH;
            this.arm2Direction = Direction.EAST;
            break;
        case SOUTH_WEST:
            this.arm1Direction = Direction.SOUTH;
            this.arm2Direction = Direction.WEST;
            break;
        default:
            this.arm1Direction = Direction.NORTH;
            this.arm2Direction = Direction.EAST;
        }
        generateLShape();
        generateRoof();
        generateSpawnLocationsAndDecorations();
    }

    @Override
    protected void generateRoomStructure() {
    }

    private void generateLShape() {
        int halfSize = size / 2;
        Point3D centerPoint = center;

        // Generate the L-shape as 3/4 of a square room based on orientation
        switch (orientation) {
        case NORTH_EAST:
            // Fill most of the square, excluding the southwest quarter
            for (int x = centerPoint.getX() - halfSize; x <= centerPoint.getX() + halfSize; x++) {
                for (int z = centerPoint.getZ() - halfSize; z <= centerPoint.getZ() + halfSize; z++) {
                    // Exclude the southwest quarter
                    boolean inSouthwestQuarter = (x < centerPoint.getX()) && (z > centerPoint.getZ());
                    if (!inSouthwestQuarter) {
                        addRoomBlocks(x, z);
                    }
                }
            }
            break;

        case NORTH_WEST:
            // Fill most of the square, excluding the southeast quarter
            for (int x = centerPoint.getX() - halfSize; x <= centerPoint.getX() + halfSize; x++) {
                for (int z = centerPoint.getZ() - halfSize; z <= centerPoint.getZ() + halfSize; z++) {
                    // Exclude the southeast quarter
                    boolean inSoutheastQuarter = (x > centerPoint.getX()) && (z > centerPoint.getZ());
                    if (!inSoutheastQuarter) {
                        addRoomBlocks(x, z);
                    }
                }
            }
            break;

        case SOUTH_EAST:
            // Fill most of the square, excluding the northwest quarter
            for (int x = centerPoint.getX() - halfSize; x <= centerPoint.getX() + halfSize; x++) {
                for (int z = centerPoint.getZ() - halfSize; z <= centerPoint.getZ() + halfSize; z++) {
                    // Exclude the northwest quarter
                    boolean inNorthwestQuarter = (x < centerPoint.getX()) && (z < centerPoint.getZ());
                    if (!inNorthwestQuarter) {
                        addRoomBlocks(x, z);
                    }
                }
            }
            break;

        case SOUTH_WEST:
            // Fill most of the square, excluding the northeast quarter
            for (int x = centerPoint.getX() - halfSize; x <= centerPoint.getX() + halfSize; x++) {
                for (int z = centerPoint.getZ() - halfSize; z <= centerPoint.getZ() + halfSize; z++) {
                    // Exclude the northeast quarter
                    boolean inNortheastQuarter = (x > centerPoint.getX()) && (z < centerPoint.getZ());
                    if (!inNortheastQuarter) {
                        addRoomBlocks(x, z);
                    }
                }
            }
            break;
        }

        addWallsForBlocks();
    }

    @Override
    public Point3D getConnectionPoint(Direction direction) {
        int halfSize = size / 2;

        // Only allow connections at the ends of the two arms
        if (direction == arm1Direction) {
            return arm1Direction.apply(center, halfSize - 1);
        } else if (direction == arm2Direction) {
            return arm2Direction.apply(center, halfSize - 1);
        }

        // Invalid connection direction - return center as fallback
        return center;
    }

    @Override
    public boolean hasValidConnectionPoint(Direction direction) {
        // Only the two arm directions are valid
        if (direction != arm1Direction && direction != arm2Direction) {
            return false;
        }

        Point3D connectionPoint = getConnectionPoint(direction);
        Point3D floorPoint = new Point3D(connectionPoint.getX(), connectionPoint.getY() - 1, connectionPoint.getZ());

        return floorBlocks.contains(floorPoint);
    }

    @Override
    public Point3D getAlternativeConnectionPoint(Direction direction) {
        // For L-shaped rooms, we don't provide alternatives since we have specific arm
        // directions
        if (!hasValidConnectionPoint(direction)) {
            return null; // Indicate no valid connection possible
        }
        return getConnectionPoint(direction);
    }

    // New method to get available connection directions
    public Direction[] getAvailableConnectionDirections() {
        return new Direction[] { arm1Direction, arm2Direction };
    }

    // New method to check if a direction is a valid arm direction
    public boolean isArmDirection(Direction direction) {
        return direction == arm1Direction || direction == arm2Direction;
    }

    // Getters for the arm directions
    public Direction getArm1Direction() {
        return arm1Direction;
    }

    public Direction getArm2Direction() {
        return arm2Direction;
    }

    public LOrientation getOrientation() {
        return orientation;
    }

    @Override
    public RoomType getType() {
        return RoomType.L_SHAPED_ROOM;
    }
}