package dev.bukkit.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.passive.SetPassive;
import dev.core.entity.RPGEntity;

/**
 * Assassin set passive ("BACKSTAB"): melee and ability-cast damage is amplified
 * when the wearer strikes an enemy from behind (the victim is facing away from
 * the attacker). Like the other set passives, eligibility is queried at damage
 * time via {@code EquipmentManager.hasSetPassive("BACKSTAB")}.
 */
public class BackstabUtils {

    public static final String PASSIVE_ID = "BACKSTAB";
    // 50% more damage from behind.
    public static final double BACKSTAB_MULTIPLIER = 1.5;

    private BackstabUtils() {
    }

    /** Marker so the registry can resolve the passive id from config. */
    public static final SetPassive MARKER = new SetPassive() {
        @Override
        public String getId() {
            return PASSIVE_ID;
        }
    };

    /**
     * Returns the backstab multiplier (1.0 when it does not apply).
     */
    public static double backstabMultiplier(RPGEntity attacker, Entity victim) {
        if (attacker == null || victim == null || victim.isDead()) {
            return 1.0;
        }
        if (!(victim instanceof LivingEntity livingVictim)) {
            return 1.0;
        }
        if (!attacker.getEquipmentManager().hasSetPassive(PASSIVE_ID)) {
            return 1.0;
        }
        return isBehind(attacker, livingVictim) ? BACKSTAB_MULTIPLIER : 1.0;
    }

    /**
     * The attacker strikes from behind when the victim's facing direction points
     * away from the attacker (dot product negative, horizontal plane only).
     */
    public static boolean isBehind(RPGEntity attacker, LivingEntity victim) {
        Entity attackerBukkit = BukkitPlayerEntity.bukkitSourceOf(attacker);
        if (attackerBukkit == null) {
            return false;
        }
        Vector victimDir = victim.getLocation().getDirection().setY(0);
        Vector toAttacker = attackerBukkit.getLocation().toVector().subtract(victim.getLocation().toVector())
                .setY(0);
        return victimDir.dot(toAttacker) < 0;
    }
}
