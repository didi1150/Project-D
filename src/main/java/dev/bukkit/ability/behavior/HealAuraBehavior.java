package dev.bukkit.ability.behavior;

import dev.bukkit.utils.HealAuraUtils;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.impl.TickEvent;

/**
 * Per-holder heal aura. Replaces {@code BukkitPlayerEntity.tick} polling
 * {@code hasSetPassive("HEAL_AURA")} with a per-{@link ActiveAbility} TickEvent
 * subscription. One behavior instance per holder wearing the set.
 *
 * <p>
 * The ring is rendered every tick (~40 particles, cheap) so the aura radius is
 * continuously visible. The actual heal fires on a 1-second interval kept by
 * {@link #lastHealMs}.
 * </p>
 */
public class HealAuraBehavior implements AbilityBehavior {
    private ActiveAbility ctx;
    private long lastHealMs = 0;

    public HealAuraBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onTick, TickEvent.class));
    }

    private void onTick(TickEvent e) {
        RPGEntity holder = ctx.getHolder();
        if (!holder.isAlive())
            return;
        // Ring renders every tick for continuous visibility.
        HealAuraUtils.renderRing(holder);
        // Heal fires once per second.
        long now = System.currentTimeMillis();
        if (now - lastHealMs >= HealAuraUtils.HEAL_INTERVAL_MS) {
            lastHealMs = now;
            HealAuraUtils.tick(holder);
        }
    }
}
