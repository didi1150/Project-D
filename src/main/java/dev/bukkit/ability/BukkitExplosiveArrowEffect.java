package dev.bukkit.ability;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.HunterBowManager;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;

/**
 * Hunter's Bow — Shock Bolt toggle. Left-click while the bow is equipped to
 * arm / disarm the next arrow's explosive payload; see
 * {@link HunterBowManager}.
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
        playerEntity.getPlayer().ifPresent(HunterBowManager.getInstance()::toggleExplosiveArrows);
    }

    @Override
    public void cancel() {
        // Instant toggle — nothing to clean up.
    }
}
