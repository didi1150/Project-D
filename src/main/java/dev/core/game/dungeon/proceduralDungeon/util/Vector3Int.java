package dev.core.game.dungeon.proceduralDungeon.util;

import dev.core.game.coords.Point3D;
import lombok.Getter;
import org.joml.Vector3f;

@Getter
public class Vector3Int {

    public static Vector3Int ZERO = new Vector3Int(0,0,0);
    public static Vector3Int ONE = new Vector3Int(1,1,1);

    public final int x, y, z;

    public Vector3Int(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point3D convertToPoint3D() {
        return new Point3D(x,y,z);
    }

    public static Vector3Int fromPoint3D(Point3D point3D) {
        return new Vector3Int(point3D.getX(), point3D.getY(), point3D.getZ());
    }

    public Vector3Int add(Vector3Int other) {
        return new Vector3Int(x + other.x, y + other.y, z + other.z);
    }

    public Vector3Int add(int x, int y, int z) {
        return new Vector3Int(x + this.x, y + this.y, z + this.z);
    }

    public Vector3Int sub(Vector3Int other) {
        return new Vector3Int(x - other.x, y - other.y, z - other.z);
    }

    public Vector3Int sub(int x, int y, int z) {
        return new Vector3Int(this.x - x, this.y - y, this.z - z);
    }

    public Vector3Int sub(int value) {
        return new Vector3Int(this.x - value, this.y - value, this.z - value);
    }

    public Vector3Int mul(Vector3Int other) {
        return new Vector3Int(x * other.x, y * other.y, z * other.z);
    }

    public Vector3Int mul(double value) {
        return new Vector3Int((int) (x * value), (int) (y * value), (int) (z * value));
    }

    public double getLength() {
        return Math.sqrt(x*x + y*y + z*z);
    }

    public int sum() {
        return x + y + z;
    }

    public Vector3Int normalize() {
        return new Vector3Int((int) (x / getLength()), (int) (y / getLength()), (int) (z / getLength()));
    }

    public Vector3Int getNotZerosVec() {
        return new Vector3Int(x != 0 ? 1 : 0, y != 0 ? 1 : 0, z != 0 ? 1 : 0);
    }

    public double distance(Vector3Int other) {
        return Math.sqrt((Math.pow((other.x - this.x), 2)) + Math.pow(other.y - this.y, 2) + Math.pow(other.z - this.z, 2));
    }

    public double distance2D(Vector3Int other) {
        return Math.sqrt((Math.pow((other.x - this.x), 2)) + Math.pow(other.z - this.z, 2));
    }

    public double distanceSqrd(Vector3Int other) {
        return (Math.pow((other.x - this.x), 2)) + Math.pow(other.y - this.y, 2) + Math.pow(other.z - this.z, 2);
    }

    public Vector3f toVector3f() {
        return new Vector3f(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Vector3Int)) {
            return false;
        }
        Vector3Int point = (Vector3Int) obj;
        return x == point.x && y == point.y && z == point.z;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + z;
    }

    @Override
    public String toString() {
        return String.format("(%d, %d, %d)", x, y, z);
    }
}
