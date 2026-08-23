package dev.bukkit.ability.behavior;

import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;

/**
 * Drain Blade passive ("STACKER"): the wielder gains +1 Attack Damage for
 * every 25 Max Health they currently have. Unlike the old query-at-damage-time
 * approach, the bonus lives <b>in the stat system</b>: this behavior keeps a
 * flat {@link StatType#ATTACK_DAMAGE} {@link StatModifier} (source
 * {@code "STACKER"}) applied to the holder and re-derives its amount from the
 * holder's current Max Health every tick, swapping the modifier whenever the
 * count crosses a full {@link #MAX_HEALTH_PER_STACK} HP step.
 *
 * <p>Because the bonus is a real stat modifier it flows through the StatEngine
 * like any other ATTACK_DAMAGE source — visible on stat panels and included in
 * every consumer of the stat (melee hits, projectiles, ability scaling) — and
 * it is removed automatically when the blade is unequipped (behavior teardown
 * calls {@link #onDeactivate}).</p>
 *
 * <p>The per-tick refresh reads Max Health outside of any StatEngine
 * aggregation (tick handlers never run inside {@code ensureComputed}), so
 * deriving stacks from another engine-managed stat is safe.</p>
 */
public class StackerBehavior implements AbilityBehavior {

    /** Max Health required per +1 Attack Damage stack. */
    public static final double MAX_HEALTH_PER_STACK = 25.0;

    /** Source id stamped on the applied modifier so it can be identified/cleaned up. */
    public static final String SOURCE_ID = "STACKER";

    private ActiveAbility ctx;
    private StatModifier applied;

    public StackerBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        refresh();
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        removeModifier();
    }

    private void onTick(TickEvent event) {
        refresh();
    }

    /**
     * Re-derive the stack count from the holder's current Max Health and swap
     * the flat ATTACK_DAMAGE modifier when it crosses a 25 HP threshold.
     * Partial health below a full stack grants nothing.
     */
    private void refresh() {
        RPGEntity holder = ctx.getHolder();
        if (holder == null || !holder.isAlive()) {
            return;
        }
        double stacks = Math.floor(holder.getMaxHealth() / MAX_HEALTH_PER_STACK);
        if (applied != null && applied.amount == stacks) {
            return;
        }
        removeModifier();
        if (stacks <= 0) {
            return;
        }
        try {
            StatModifier mod = StatModifier.builder(stacks, StatModifierType.FLAT, StatType.ATTACK_DAMAGE, SOURCE_ID)
                    .build();
            holder.addStatModifier(mod);
            applied = mod;
        } catch (Exception e) {
            System.out.println("Stacker: failed to apply ATTACK_DAMAGE modifier: " + e.getMessage());
        }
    }

    private void removeModifier() {
        if (applied == null) {
            return;
        }
        try {
            ctx.getHolder().removeStatModifier(applied);
        } catch (Exception ignored) {
            // stats may already be torn down (quit/death cleanup)
        }
        applied = null;
    }
}
