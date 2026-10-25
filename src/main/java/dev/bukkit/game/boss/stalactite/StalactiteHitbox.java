package dev.bukkit.game.boss.stalactite;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Shared hitbox geometry for stalactites: a square frustum wide at the ceiling,
 * narrow at the tip. Used by
 * {@link BukkitStalactiteEntity} and
 * {@link StalactiteManager}.
 */
public final class StalactiteHitbox {

    private StalactiteHitbox() {
    }

    public static Location computeHitboxLocation(Location ceiling) {
        // pivot is now centered above player (ceiling/pivot is center, not corner)
        float scaleY = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
        return new Location(ceiling.getWorld(), ceiling.getX(), ceiling.getY() - scaleY, ceiling.getZ(),
                ceiling.getYaw(), ceiling.getPitch());
    }

    public static Location computeColliderLocation(Location ceiling) {
        float scaleY = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
        return new Location(ceiling.getWorld(), ceiling.getX(), ceiling.getY() - scaleY / 2.0, ceiling.getZ(),
                ceiling.getYaw(), ceiling.getPitch());
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static boolean isInsideTaperedStalactite(Vector point, Location ceiling, double margin) {
        double centerX = ceiling.getX();
        double centerZ = ceiling.getZ();
        double baseY = ceiling.getY();
        double tipY = baseY - StalactiteConfig.MAX_DISPLAY_SCALE_Y;
        if (point.getY() < tipY - margin || point.getY() > baseY + margin)
            return false;
        double height = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
        if (height <= 0)
            return false;
        double t = (point.getY() - tipY) / height;
        t = Math.max(0, Math.min(1, t));
        double baseHalfX = StalactiteConfig.MAX_DISPLAY_SCALE_X / 2.0;
        double baseHalfZ = StalactiteConfig.MAX_DISPLAY_SCALE_Z / 2.0;
        double tipHalfX = StalactiteConfig.HITBOX_TIP_HALF_X;
        double tipHalfZ = StalactiteConfig.HITBOX_TIP_HALF_Z;
        double halfX = lerp(tipHalfX, baseHalfX, t) + margin;
        double halfZ = lerp(tipHalfZ, baseHalfZ, t) + margin;
        double dx = Math.abs(point.getX() - centerX);
        double dz = Math.abs(point.getZ() - centerZ);
        return dx <= halfX && dz <= halfZ;
    }

    /**
     * Whether a bounding box overlaps the tapered hitbox whose base (top face) is
     * at {@code base}. The base may be the current falling display position.
     */
    public static boolean intersectsTaperedStalactite(BoundingBox box, Location base, double margin) {
        if (box == null || base == null) {
            return false;
        }
        double height = StalactiteConfig.MAX_DISPLAY_SCALE_Y;
        if (height <= 0) {
            return false;
        }
        double baseY = base.getY();
        double tipY = baseY - height;
        double low = Math.max(box.getMinY(), tipY - margin);
        double high = Math.min(box.getMaxY(), baseY + margin);
        if (low > high) {
            return false;
        }
        double baseHalfX = StalactiteConfig.MAX_DISPLAY_SCALE_X / 2.0;
        double baseHalfZ = StalactiteConfig.MAX_DISPLAY_SCALE_Z / 2.0;
        double centerX = base.getX();
        double centerZ = base.getZ();
        double step = 0.25;
        int steps = (int) Math.ceil((high - low) / step);
        for (int i = 0; i <= steps; i++) {
            double y = Math.min(low + i * step, high);
            double t = (y - tipY) / height;
            t = Math.max(0, Math.min(1, t));
            double halfX = lerp(StalactiteConfig.HITBOX_TIP_HALF_X, baseHalfX, t) + margin;
            double halfZ = lerp(StalactiteConfig.HITBOX_TIP_HALF_Z, baseHalfZ, t) + margin;
            if (box.getMaxX() >= centerX - halfX && box.getMinX() <= centerX + halfX
                    && box.getMaxZ() >= centerZ - halfZ && box.getMinZ() <= centerZ + halfZ) {
                return true;
            }
        }
        return false;
    }

    public static boolean rayIntersectsTapered(Vector start, Vector dir, double maxDist, Location ceiling,
            double margin) {
        if (maxDist <= 0)
            return isInsideTaperedStalactite(start, ceiling, margin);
        double len = dir.length();
        if (len < 1e-8)
            return isInsideTaperedStalactite(start, ceiling, margin);
        Vector nDir = dir.clone().normalize();
        double step = 0.15;
        int steps = (int) Math.ceil(maxDist / step);
        for (int i = 0; i <= steps; i++) {
            double d = Math.min(i * step, maxDist);
            Vector p = start.clone().add(nDir.clone().multiply(d));
            if (isInsideTaperedStalactite(p, ceiling, margin))
                return true;
        }
        Vector end = start.clone().add(nDir.clone().multiply(maxDist));
        return isInsideTaperedStalactite(end, ceiling, margin);
    }
}
