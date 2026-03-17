package dev.core.game.dungeon.proceduralDungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public enum Direction2D {
    UP(0,1), DOWN(0,-1), LEFT(-1,0), RIGHT(1,0);

    private final int x, y;

    Direction2D(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static List<Direction2D> getCardinalDirections() {
        return new ArrayList<>(List.of(UP, RIGHT, DOWN, LEFT));
    }

    public static Direction2D getRandomCardinalDirection(Random random) {
        return Direction2D.values()[random.nextInt(0,4)];
    }

    public Vector3Int apply(Vector3Int point, int distance) {
        return new Vector3Int(point.getX() + x * distance, point.getY() + y * distance, point.getZ());
    }

    public Vector3Int apply(Vector3Int point) {
        return new Vector3Int(point.getX() + x, point.getY() + y, point.getZ());
    }

    public Direction2D opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public Direction2D rotateToRight() {
        return switch (this) {
            case UP -> RIGHT;
            case DOWN -> LEFT;
            case LEFT -> UP;
            case RIGHT -> DOWN;
        };
    }
}
