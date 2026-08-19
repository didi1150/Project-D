package dev.bukkit.event.subscribers;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import dev.bukkit.utils.CombatRelation;
import dev.core.entity.SummonRegistry;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;

/**
 * Keeps summoned mobs on the player's side: a summon's vanilla AI must never
 * pick a player or an allied summon as its target - it fights enemies only
 * (the summon's own targeting loop in {@code SummonedMobRPGEntity} feeds it
 * valid targets).
 */
@EventSubscriber
public class SummonSubscriber {

    @Subscribe
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.isDead()) {
            return;
        }
        if (!SummonRegistry.getInstance().isSummon(mob.getUniqueId())) {
            return; // dungeon mobs keep their normal targeting rules
        }
        LivingEntity target = event.getTarget();
        if (target == null || !CombatRelation.isPlayerTeam(target)) {
            return; // targeting an enemy mob - allowed
        }
        event.setTarget(null);
        event.setCancelled(true);
    }
}