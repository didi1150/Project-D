package dev.bukkit.game.coords;

import org.bukkit.Location;

import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;

public class LocToPoint {

    public static Point3D locToBlock(Location location) {

        return new Point3D(location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                location.getWorld().getName());
    }

    public static ViewPoint3D viewToLoc(Location location) {
        return new ViewPoint3D(location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                location.getWorld().getName(), location.getYaw(), location.getPitch());
    }

}
