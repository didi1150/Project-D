package dev.bukkit.ability.behavior;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import dev.bukkit.utils.StealthRegistry;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;

/**
 * Handles Orb of Stealth 20% passive dodge on EntityTargetLivingEntityEvent.
 */
public class StealthPassiveBehavior implements AbilityBehavior {

    private ActiveAbility ctx;

    public StealthPassiveBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
        StealthRegistry.setPassiveEquipped(ctx.getHolder().getUuid(), true);
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        StealthRegistry.setPassiveEquipped(ctx.getHolder().getUuid(), true);
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTarget, EntityTargetLivingEntityEvent.class));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        StealthRegistry.setPassiveEquipped(ctx.getHolder().getUuid(), false);
    }

    private void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() == null) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (!player.getUniqueId().equals(ctx.getHolder().getUuid())) return;
        // 20% chance to cancel targeting
        if (StealthRegistry.rollPassiveDodge(player.getUniqueId())) {
            event.setCancelled(true);
            event.setTarget(null);
            if (event.getEntity() instanceof Mob mob) {
                try { mob.setTarget(null); } catch (Exception ignored) {}
            }
        }
    }
}
