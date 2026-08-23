package dev.bukkit.ability.behavior;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.event.EventAction;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.ActiveAbilityRegistry;

/**
 * Per-holder threat draw. Previously a single global
 * {@code ThreatPassiveSubscriber} scanned all online holders via
 * {@code hasSetPassive}; now each holder subscribes individually and is
 * tracked via {@link ActiveAbilityRegistry#holdersOf(String)}.
 */
public class ThreatBehavior implements AbilityBehavior {
    private ActiveAbility ctx;
    public ThreatBehavior(ActiveAbility ctx) { this.ctx = ctx; }
    @Override public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTarget, EntityTargetLivingEntityEvent.class));
    }
    private void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Mob mob)) return;
        if (!(e.getTarget() instanceof Player)) return;
        // Only act if this holder is a valid tank near the mob
        Player holderPlayer = ctx.getHolder() instanceof BukkitPlayerEntity bpe
                ? bpe.getPlayer().orElse(null) : null;
        if (holderPlayer == null || holderPlayer.isDead() || !holderPlayer.isOnline()) return;
        if (EntityManager.getInstance().isGhost(holderPlayer.getUniqueId())) return;
        if (holderPlayer.getWorld() != mob.getWorld()) return;
        double distSq = holderPlayer.getLocation().distanceSquared(mob.getLocation());
        double rangeSq = 10.0 * 10.0;
        if (distSq > rangeSq) return;
        if (e.getTarget().getUniqueId().equals(holderPlayer.getUniqueId())) return;
        if (Math.random() < 0.5) e.setTarget(holderPlayer);
    }
}
