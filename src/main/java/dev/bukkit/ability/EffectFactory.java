package dev.bukkit.ability;

import dev.core.ability.Effect;

/**
 * Creates a Bukkit {@link Effect} for a given ability's cooldown key. The
 * single extension point for wiring a new ability id to its in-game effect;
 * see {@link BukkitEffectRegistry}.
 */
@FunctionalInterface
public interface EffectFactory {

    Effect create(String cooldownKey);
}