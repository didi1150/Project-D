package dev.bukkit.ability;

import dev.bukkit.ability.behavior.HunterBowBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.ability.impl.BouncyArrowAbility;

/**
 * Hunter's Bow — Bouncy Arrows toggle. Shift while the bow is equipped to
 * cycle the next arrow's bounce charges; see {@link HunterBowBehavior}.
 */
public class BukkitBouncyArrowEffect extends Effect {

    public BukkitBouncyArrowEffect(String cooldownKey) {
        super(null, 1000L, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        ActiveAbility aa = ActiveAbilityRegistry.getInstance().get(caster, BouncyArrowAbility.ID).orElse(null);
        if (aa != null && aa.getBehavior() instanceof HunterBowBehavior beh) {
            playerEntity.getPlayer().ifPresent(beh::cycleBounceCharges);
            return;
        }
        // Fallback: no ActiveAbility yet (e.g. headless test) — create transient handling via HunterBowBehavior holder state
        playerEntity.getPlayer().ifPresent(p -> {
            // Maintain per-holder state even without ActiveAbility for test compat
            HunterBowBehavior holderBeh = HunterBowBehavior.forHolder(p.getUniqueId());
            if (holderBeh != null) holderBeh.cycleBounceCharges(p);
        });
    }

    @Override
    public void cancel() {
        // Instant toggle — nothing to clean up.
    }
}
