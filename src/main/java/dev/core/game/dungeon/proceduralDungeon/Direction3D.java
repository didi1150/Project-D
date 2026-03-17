package dev.core.game.dungeon.proceduralDungeon;

import org.checkerframework.checker.units.qual.N;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public enum Direction3D {
    UP(0,1,0), DOWN(0,-1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);

    private final int x, y, z;

    Direction3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static List<Direction3D> get2DCardinalDirections() {
        return new ArrayList<>(List.of(NORTH, EAST, SOUTH, WEST));
    }

    public static Direction3D getRandom2DCardinalDirection(Random random) {
        return get2DCardinalDirections().get(random.nextInt(0,4));
    }

    public static Direction3D getDirectionForVec(Vector3Int vec) {
        Vector3Int dirVec = vec.normalize();
        return Arrays.stream(Direction3D.values()).filter(d -> d.x == dirVec.x && d.y == dirVec.y && d.z == dirVec.z).findFirst().orElse(null);
    }

    public Vector3Int toVector3Int() {
        return new Vector3Int(x,y,z);
    }

    public Vector3Int getInverseVec() {
        return new Vector3Int((x&0b1)^0b1,(y&0b1)^0b1,(z&0b1)^0b1);
    }

    public Vector3Int getAbsoluteVec() {
        return new Vector3Int(Math.abs(x), Math.abs(y), Math.abs(z));
    }

    public Vector3Int apply(Vector3Int vec, int distance) {
        return new Vector3Int(vec.getX() + x * distance, vec.getY() + y * distance, vec.getZ() + z * distance);
    }

    public Vector3Int apply(Vector3Int vec) {
        return apply(vec, 1);
    }

    public Vector3Int applyAndUp(Vector3Int vec) {
        return applyAndUp(vec, 1);
    }

    public Vector3Int applyAndUp(Vector3Int vec, int distance) {
        return new Vector3Int(vec.getX() + x * distance, vec.getY() + (y + 1) * distance, vec.getZ() + z * distance);
    }

    public Vector3Int applyAndDown(Vector3Int vec) {
        return new Vector3Int(vec.getX() + x, vec.getY() + y + 1, vec.getZ() + z);
    }

    public Direction3D opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    public Direction3D rotateToRight() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            default -> this;
        };
    }


    @Override
    public String toString() {
        return super.toString() + String.format("(%d, %d, %d)", x, y, z);
    }
}
