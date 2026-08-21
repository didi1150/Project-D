package dev.bukkit.ability;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.HunterBowManager;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * Hunter's Bow — Bouncy Arrows toggle. Shift while the bow is equipped to
 * cycle the next arrow's bounce charges; see {@link HunterBowManager}.
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
        playerEntity.getPlayer().ifPresent(HunterBowManager.getInstance()::cycleBounceCharges);
    }

    @Override
    public void cancel() {
        // Instant toggle — nothing to clean up.
    }
}
