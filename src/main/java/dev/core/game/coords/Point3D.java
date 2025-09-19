package dev.core.game.coords;

import lombok.Getter;

@Getter
public class Point3D {

    private final int x, y, z;
    private String world;

    public Point3D(int x, int y, int z) {
        this(x, y, z, null);
    }

    public Point3D(int x, int y, int z, String world) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
    }

    public Point3D add(Point3D other) {
        return new Point3D(x + other.x, y + other.y, z + other.z);
    }

    public double distance(Point3D other) {
        return Math.sqrt(
                (Math.pow((other.x - this.x), 2)) + Math.pow(other.y - this.y, 2) + Math.pow(other.z - this.z, 2));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Point3D)) {
            return false;
        }
        Point3D point = (Point3D) obj;
        return x == point.x && y == point.y && z == point.z;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + z;
    }

    @Override
    public String toString() {
        return String.format("%d %d %d", x, y, z) + (world == null ? "" : world);
    }
}
