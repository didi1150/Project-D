package dev.bukkit.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.core.entity.RPGEntity;
import dev.core.entity.SummonRegistry;

/**
 * Bukkit-layer team/enemy resolution shared by the damage pipeline and the
 * ability AoE filters. Mirrors {@code dev.core.entity.CombatTeamUtils} for
 * vanilla {@link Entity} objects (players and player-owned summons are on the
 * player team; everything else is not).
 */
public final class CombatRelation {

    private CombatRelation() {
    }

    public static boolean isPlayerTeam(Entity entity) {
        if (entity instanceof Player) {
            return true;
        }
        return SummonRegistry.getInstance().isSummon(entity.getUniqueId());
    }

    public static boolean isPlayerTeam(RPGEntity entity) {
        return dev.core.entity.CombatTeamUtils.isPlayerTeam(entity);
    }

    /**
     * Whether a fighter may strike the given target in a player-driven ability
     * AoE: a player or a player-owned summon only hits mobs (and vice versa).
     * Same-team targets (players, allied summons) and mob-vs-mob cases are
     * never valid.
     */
    public static boolean isEnemy(RPGEntity attacker, Entity target) {
        if (!(target instanceof LivingEntity) || target.getUniqueId().equals(attacker.getUuid())) {
            return false;
        }
        return isPlayerTeam(attacker) != isPlayerTeam(target);
    }
}