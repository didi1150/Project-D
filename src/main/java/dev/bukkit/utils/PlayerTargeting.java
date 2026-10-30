package dev.bukkit.utils;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.core.entity.EntityManager;

/**
 * Shared "other player" targeting used by the Tic Tac Toe and Teleportation
 * Staff reward items. Only players can be targeted; the caster is excluded.
 */
public final class PlayerTargeting {

    public static final double RANGE = 15.0;
    public static final double RAY_SIZE = 0.5;

    private PlayerTargeting() {
    }

    public static Player raycastOtherPlayer(Player caster) {
        if (caster == null) {
            return null;
        }
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = caster.getWorld().rayTrace(eye, direction, RANGE, FluidCollisionMode.NEVER, true,
                RAY_SIZE, entity -> isOtherPlayer(caster, entity));
        if (result == null || !(result.getHitEntity() instanceof Player target)) {
            return null;
        }
        return isOtherPlayer(caster, target) ? target : null;
    }

    public static boolean isOtherPlayer(Player caster, Entity entity) {
        if (!(entity instanceof Player target)) {
            return false;
        }
        if (target.getUniqueId().equals(caster.getUniqueId())) {
            return false;
        }
        if (target.isDead() || !target.isValid() || !target.isOnline()) {
            return false;
        }
        return !EntityManager.getInstance().isGhost(target.getUniqueId());
    }
}
