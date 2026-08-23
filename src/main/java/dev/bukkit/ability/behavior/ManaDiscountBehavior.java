package dev.bukkit.ability.behavior;

import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;

/**
 * Per-holder mana discount passive. Previously {@code ManaDiscountUtils}
 * checked {@code hasSetPassive} at cost resolution. Now the holder is tracked
 * via {@link ActiveAbilityRegistry} so the check is unified.
 * No event subscription needed; marker behavior ensures tracking.
 */
public class ManaDiscountBehavior implements AbilityBehavior {
    private ActiveAbility ctx;
    public ManaDiscountBehavior(ActiveAbility ctx) { this.ctx = ctx; }
    // marker only — presence in ActiveAbilityRegistry is the signal
}
