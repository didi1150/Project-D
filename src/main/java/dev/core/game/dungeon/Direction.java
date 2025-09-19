package dev.core.game.dungeon;

import dev.core.game.coords.Point3D;

public enum Direction {
    NORTH(0, 0, -1), SOUTH(0, 0, 1), EAST(1, 0, 0), WEST(-1, 0, 0);

    private final int deltaX, deltaY, deltaZ;

    Direction(int deltaX, int deltaY, int deltaZ) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.deltaZ = deltaZ;
    }

    public Point3D apply(Point3D point, int distance) {
        return new Point3D(point.getX() + deltaX * distance, point.getY() + deltaY * distance,
                point.getZ() + deltaZ * distance);
    }

    public Direction opposite() {
        switch (this) {
        case NORTH:
            return SOUTH;
        case SOUTH:
            return NORTH;
        case EAST:
            return WEST;
        case WEST:
            return EAST;
        default:
            return this;
        }
    }

    /**
     * Get direction based on delta values
     */
    public static Direction getDirectionFromDelta(int deltaX, int deltaZ) {
        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            return deltaX > 0 ? EAST : WEST;
        } else {
            return deltaZ > 0 ? SOUTH : NORTH;
        }
    }
}