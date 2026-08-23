package dev.bukkit.ability;

import dev.bukkit.ability.behavior.HunterBowBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.ability.impl.ExplosiveArrowAbility;

/**
 * Hunter's Bow — Shock Bolt toggle. Left-click while the bow is equipped to
 * arm / disarm the next arrow's explosive payload; see
 * {@link HunterBowBehavior}.
 */
public class BukkitExplosiveArrowEffect extends Effect {

    public BukkitExplosiveArrowEffect(String cooldownKey) {
        super(null, 1000L, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        ActiveAbility aa = ActiveAbilityRegistry.getInstance().get(caster, ExplosiveArrowAbility.ID).orElse(null);
        if (aa != null && aa.getBehavior() instanceof HunterBowBehavior beh) {
            playerEntity.getPlayer().ifPresent(beh::toggleExplosive);
            return;
        }
        // Fallback: no ActiveAbility yet (e.g. headless test) — transient handling via holder state
        playerEntity.getPlayer().ifPresent(p -> {
            HunterBowBehavior holderBeh = HunterBowBehavior.forHolder(p.getUniqueId());
            if (holderBeh != null) holderBeh.toggleExplosive(p);
        });
    }

    @Override
    public void cancel() {
        // Instant toggle — nothing to clean up.
    }
}
