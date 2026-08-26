package dev.bukkit.event.subscribers;

import java.util.Optional;
import java.util.Random;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import dev.bukkit.utils.StealthRegistry;
import dev.core.ability.passive.SetPassive;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;
import dev.core.ability.ActiveAbilityRegistry;

/**
 * Tank set passive ("THREAT"): when a mob picks a player as its target and a
 * player wearing the tank set is close enough, the mob switches to the tank
 * with a configurable chance. Effects are checked at event time via
 * {@code EquipmentManager.hasSetPassive("THREAT")}, so breaking the set
 * immediately stops drawing aggro.
 */
@EventSubscriber
public class ThreatPassiveSubscriber {

    private static final String PASSIVE_ID = "THREAT";
    // Mob must pick its target every few seconds; a 50% reroll each time pulls
    // aggro onto the tank reliably within a couple of retargets.
    private static final int CHANCE_PERCENT = 50;
    private static final double AGGRO_RANGE = 10.0;

    private final Random random = new Random();

    /** Marker so the registry can resolve the passive id from config. */
    public static final SetPassive MARKER = new SetPassive() {
        @Override
        public String getId() {
            return PASSIVE_ID;
        }
    };

    public ThreatPassiveSubscriber() {
    }

    @Subscribe(priority = EventAction.NORMAL_PRIORITY)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        // Unified tracking guard: if any holder is tracked via ActiveAbilityRegistry (new per-holder
        // ThreatBehavior path), let the per-holder behaviors handle the event to avoid double procs.
        if (!ActiveAbilityRegistry.getInstance().holdersOf(PASSIVE_ID).isEmpty()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!(event.getTarget() instanceof Player) || event.isCancelled()) {
            return;
        }

        Player tank = nearestTank(mob);
        if (tank == null || tank.getUniqueId().equals(event.getTarget().getUniqueId())) {
            return;
        }
        if (random.nextInt(100) >= CHANCE_PERCENT) {
            return;
        }
        event.setTarget(tank);
    }

    private Player nearestTank(Mob mob) {
        Player nearest = null;
        double bestSq = AGGRO_RANGE * AGGRO_RANGE;
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.isDead() || !player.isOnline()
                    || EntityManager.getInstance().isGhost(player.getUniqueId())) {
                continue;
            }
            Optional<RPGEntity> rpg = EntityManager.getInstance().getEntity(player.getUniqueId());
            if (rpg.isEmpty() || !rpg.get().isAlive()
                    || !rpg.get().getEquipmentManager().hasSetPassive(PASSIVE_ID)) {
                continue;
            }
            // Orb stealth: shrouded tanks must not pull aggro to themselves
            // (deterministic check — the 20% passive roll is targeting's business).
            if (StealthRegistry.isShroudedDeterministic(player)) {
                continue;
            }
            double distSq = player.getLocation().distanceSquared(mob.getLocation());
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = player;
            }
        }
        return nearest;
    }
}
