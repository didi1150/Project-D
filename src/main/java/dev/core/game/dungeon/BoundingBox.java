package dev.core.game.dungeon;

public class BoundingBox {

    public final int minX, minY, minZ;
    public final int maxX, maxY, maxZ;

    public BoundingBox(Point3D p1, Point3D p2) {
        this.minX = Math.min(p1.getX(), p2.getX());
        this.maxX = Math.max(p1.getX(), p2.getX());
        this.minY = Math.min(p1.getY(), p2.getY());
        this.maxY = Math.max(p1.getY(), p2.getY());
        this.minZ = Math.min(p1.getZ(), p2.getZ());
        this.maxZ = Math.max(p1.getZ(), p2.getZ());
    }

    public boolean intersects(BoundingBox other) {
        return !(other.minX > this.maxX || other.maxX < this.minX || other.minY > this.maxY || other.maxY < this.minY
                || other.minZ > this.maxZ || other.maxZ < this.minZ);
    }

}
