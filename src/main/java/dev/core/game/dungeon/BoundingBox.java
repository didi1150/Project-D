package dev.core.game.dungeon;

import dev.core.game.coords.Point3D;

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

    /**
     * Create a bounding box from multiple points (useful for L-shaped
     * rooms/tunnels)
     */
    public BoundingBox(Point3D... points) {
        if (points.length == 0) {
            throw new IllegalArgumentException("At least one point required");
        }

        int minX = points[0].getX();
        int maxX = points[0].getX();
        int minY = points[0].getY();
        int maxY = points[0].getY();
        int minZ = points[0].getZ();
        int maxZ = points[0].getZ();

        for (Point3D point : points) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }

        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    /**
     * Standard intersection check - returns true if boxes overlap
     */
    public boolean intersects(BoundingBox other) {
        return !(other.minX > this.maxX || other.maxX < this.minX || other.minY > this.maxY || other.maxY < this.minY
                || other.minZ > this.maxZ || other.maxZ < this.minZ);
    }

    /**
     * Intersection check with buffer zone - useful for ensuring minimum spacing
     */
    public boolean intersectsWithBuffer(BoundingBox other, int buffer) {
        return !(other.minX > this.maxX + buffer || other.maxX < this.minX - buffer || other.minY > this.maxY + buffer
                || other.maxY < this.minY - buffer || other.minZ > this.maxZ + buffer
                || other.maxZ < this.minZ - buffer);
    }

    /**
     * Check if this box is adjacent to another (touching but not overlapping) This
     * is useful for allowing tunnels to connect to rooms
     */
    public boolean isAdjacentTo(BoundingBox other) {
        // Check if they're touching on any face but not overlapping
        boolean touchingX = (this.maxX + 1 == other.minX || this.minX - 1 == other.maxX);
        boolean touchingZ = (this.maxZ + 1 == other.minZ || this.minZ - 1 == other.maxZ);

        // X-aligned adjacency (touching on east/west faces)
        if (touchingX && this.minZ <= other.maxZ && this.maxZ >= other.minZ && this.minY <= other.maxY
                && this.maxY >= other.minY) {
            return true;
        }

        // Z-aligned adjacency (touching on north/south faces)
        if (touchingZ && this.minX <= other.maxX && this.maxX >= other.minX && this.minY <= other.maxY
                && this.maxY >= other.minY) {
            return true;
        }

        return false;
    }

    /**
     * Check if a point is contained within this bounding box
     */
    public boolean contains(Point3D point) {
        return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY
                && point.getZ() >= minZ && point.getZ() <= maxZ;
    }

    /**
     * Get the volume of this bounding box
     */
    public int getVolume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Expand this bounding box by a given amount in all directions
     */
    public BoundingBox expand(int amount) {
        Point3D p1 = new Point3D(minX - amount, minY - amount, minZ - amount);
        Point3D p2 = new Point3D(maxX + amount, maxY + amount, maxZ + amount);
        return new BoundingBox(p1, p2);
    }

    /**
     * Get the center point of this bounding box
     */
    public Point3D getCenter() {
        return new Point3D((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    /**
     * Calculate Manhattan distance between the centers of two bounding boxes
     */
    public int manhattanDistanceTo(BoundingBox other) {
        Point3D thisCenter = this.getCenter();
        Point3D otherCenter = other.getCenter();

        return Math.abs(thisCenter.getX() - otherCenter.getX()) + Math.abs(thisCenter.getY() - otherCenter.getY())
                + Math.abs(thisCenter.getZ() - otherCenter.getZ());
    }

    /**
     * Check if this bounding box is completely separate from another with minimum
     * spacing
     */
    public boolean hasMinimumSeparationFrom(BoundingBox other, int minDistance) {
        // Calculate the minimum distance between the boxes
        int dx = Math.max(0, Math.max(this.minX - other.maxX, other.minX - this.maxX));
        int dy = Math.max(0, Math.max(this.minY - other.maxY, other.minY - this.maxY));
        int dz = Math.max(0, Math.max(this.minZ - other.maxZ, other.minZ - this.maxZ));

        return Math.max(dx, Math.max(dy, dz)) >= minDistance;
    }

    @Override
    public String toString() {
        return String.format("BoundingBox[(%d,%d,%d) to (%d,%d,%d)]", minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        BoundingBox that = (BoundingBox) obj;
        return minX == that.minX && maxX == that.maxX && minY == that.minY && maxY == that.maxY && minZ == that.minZ
                && maxZ == that.maxZ;
    }

    @Override
    public int hashCode() {
        int result = minX;
        result = 31 * result + maxX;
        result = 31 * result + minY;
        result = 31 * result + maxY;
        result = 31 * result + minZ;
        result = 31 * result + maxZ;
        return result;
    }
}