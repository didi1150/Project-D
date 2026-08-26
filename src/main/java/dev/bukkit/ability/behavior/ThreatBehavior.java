package dev.bukkit.ability.behavior;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.event.EventAction;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.StealthRegistry;
import dev.core.ability.ActiveAbilityRegistry;

/**
 * Per-holder threat draw. Previously a single global
 * {@code ThreatPassiveSubscriber} scanned all online holders via
 * {@code hasSetPassive}; now each holder subscribes individually and is tracked
 * via {@link ActiveAbilityRegistry#holdersOf(String)}.
 */
public class ThreatBehavior implements AbilityBehavior {
    private static final Logger LOG = resolveLogger();
    private ActiveAbility ctx;

    public ThreatBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    private static Logger resolveLogger() {
        try {
            Logger logger = Bukkit.getLogger();
            logger.setLevel(Level.INFO);
            return logger;
        } catch (Throwable e) {
            return Logger.getLogger("ThreatBehavior");
        }
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTarget, EntityTargetLivingEntityEvent.class));
    }

    private void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Mob mob)) {
            LOG.info("[ThreatBehavior DEBUG] entity is not Mob: " + e.getEntity().getType() + " – skipping");
            return;
        }
        if (!(e.getTarget() instanceof Player)) {
            LOG.info("[ThreatBehavior DEBUG] mob " + mob.getType() + " target is not Player ("
                    + (e.getTarget() == null ? "null" : e.getTarget().getType()) + ") – skipping");
            return;
        }
        Player targetPlayer = (Player) e.getTarget();
        // Only act if this holder is a valid tank near the mob
        Player holderPlayer = ctx.getHolder() instanceof BukkitPlayerEntity bpe ? bpe.getPlayer().orElse(null) : null;
        if (holderPlayer == null || holderPlayer.isDead() || !holderPlayer.isOnline()) {
            LOG.info("[ThreatBehavior DEBUG] holder is null/dead/offline – skipping (mob=" + mob.getType() + ", target="
                    + targetPlayer.getName() + ")");
            return;
        }
        if (EntityManager.getInstance().isGhost(holderPlayer.getUniqueId())) {
            LOG.info("[ThreatBehavior DEBUG] holder " + holderPlayer.getName() + " is ghost – skipping");
            return;
        }
        if (holderPlayer.getWorld() != mob.getWorld()) {
            LOG.info("[ThreatBehavior DEBUG] holder " + holderPlayer.getName() + " and mob " + mob.getType()
                    + " are in different worlds – skipping");
            return;
        }
        // Orb stealth: a shrouded tank must not pull aggro to themselves
        // (deterministic check — the 20% passive roll is targeting's business).
        boolean shrouded = StealthRegistry.isShroudedDeterministic(holderPlayer);
        LOG.info("[ThreatBehavior DEBUG] mob=" + mob.getType() + " targeting=" + targetPlayer.getName() + " holder="
                + holderPlayer.getName() + " shrouded=" + shrouded);
        if (shrouded) {
            LOG.info("[ThreatBehavior DEBUG] holder " + holderPlayer.getName() + " is shrouded – NOT pulling aggro");
            return;
        }
        double distSq = holderPlayer.getLocation().distanceSquared(mob.getLocation());
        double rangeSq = 10.0 * 10.0;
        if (distSq > rangeSq) {
            LOG.info("[ThreatBehavior DEBUG] holder " + holderPlayer.getName() + " too far from " + mob.getType()
                    + " (distSq=" + String.format("%.1f", distSq) + " > rangeSq=" + rangeSq + ") – skipping");
            return;
        }
        if (e.getTarget().getUniqueId().equals(holderPlayer.getUniqueId())) {
            LOG.info("[ThreatBehavior DEBUG] mob " + mob.getType() + " already targeting holder "
                    + holderPlayer.getName() + " – skipping");
            return;
        }
        double roll = Math.random();
        LOG.info("[ThreatBehavior DEBUG] mob=" + mob.getType() + " holder=" + holderPlayer.getName() + " roll="
                + String.format("%.3f", roll) + " (<0.5 => " + (roll < 0.5) + ")");
        if (roll < 0.5)
            e.setTarget(holderPlayer);
    }
}
