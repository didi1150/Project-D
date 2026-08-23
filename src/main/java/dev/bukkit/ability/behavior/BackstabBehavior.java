package dev.bukkit.ability.behavior;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.event.EventAction;

/**
 * Per-holder backstab passive. Previously {@code BackstabUtils} was polled via
 * {@code hasSetPassive} in {@code CombatListener}. Now each holder subscribes
 * and {@code BackstabUtils} can query {@code ActiveAbilityRegistry}.
 */
public class BackstabBehavior implements AbilityBehavior {
    private ActiveAbility ctx;
    public BackstabBehavior(ActiveAbility ctx) { this.ctx = ctx; }
    @Override public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        // Backstab is currently checked inline in CombatListener; per-holder subscription kept for future
        // where the behavior itself applies the multiplier, or keep as marker so hasSetPassive is unified.
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onDamage, EntityDamageByEntityEvent.class));
    }
    private void onDamage(EntityDamageByEntityEvent e) {
        // marker passive: no direct handling, CombatListener still consults registry via hasSetPassive
        // This stub ensures the holder is tracked in ActiveAbilityRegistry for unified queries.
    }
}
