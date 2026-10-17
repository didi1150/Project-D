package dev.bukkit.utils;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.core.entity.EntityManager;

/**
 * Shared ally targeting for the Utility Staff. Both the live outline
 * ({@code SupportStaffBehavior}) and the actual cast
 * ({@code BukkitSupportStaffUseEffect}) use this so they can never disagree.
 *
 * <p>
 * The trace starts at the eye instead of one block ahead: the old start point
 * sat inside (or past) a target that was very close, which made the outline
 * disappear when a player looked directly at an ally.
 * </p>
 */
public final class StaffTargeting {

    /** Maximum raycast range (blocks) for ally targeting. */
    public static final double RANGE = 15.0;
    /** Ray trace size (blocks) for entity hit detection. */
    public static final double RAY_SIZE = 0.5;

    private StaffTargeting() {
    }

    /** Finds the nearest allied living entity in the player's line of sight. */
    public static Entity raycastAlly(Player player) {
        if (player == null) {
            return null;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTrace(eye, direction, RANGE, FluidCollisionMode.NEVER, true,
                RAY_SIZE, entity -> isAlliedTarget(player, entity));
        if (result == null || result.getHitEntity() == null) {
            return null;
        }
        Entity hit = result.getHitEntity();
        if (!(hit instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
            return null;
        }
        if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
            return null;
        }
        return hit;
    }

    /** Filter for the ray trace: living, non-ghost player-team entities except the caster. */
    public static boolean isAlliedTarget(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        if (living.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        if (living.isDead() || !living.isValid()) {
            return false;
        }
        if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
            return false;
        }
        return CombatRelation.isPlayerTeam(entity);
    }
}
