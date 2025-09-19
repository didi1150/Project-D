package dev.bukkit.game.coords;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;

public class PointToLocation {

    public static Location blockToLoc(Point3D point3d) {
        if (point3d.getWorld() == null) {
            return null;
        }
        return new Location(Bukkit.getWorld(point3d.getWorld()), point3d.getX(), point3d.getY(), point3d.getZ());
    }

    public static Location viewToLoc(ViewPoint3D point3d) {
        if (point3d.getWorld() == null) {
            return null;
        }
        return new Location(Bukkit.getWorld(point3d.getWorld()), point3d.getX(), point3d.getY(), point3d.getZ(),
                point3d.getYaw(), point3d.getPitch());
    }

}
