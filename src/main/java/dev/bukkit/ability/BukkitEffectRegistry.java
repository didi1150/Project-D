package dev.bukkit.ability;

import java.util.HashMap;
import java.util.Map;

import dev.core.ability.Effect;

/**
 * Maps ability ids to the Bukkit {@link Effect} implementations that back
 * them. Effect dispatch used to be a hardcoded {@code instanceof} chain in
 * {@link BukkitEffectManager}; this registry replaces that and becomes the
 * single extension point: register an {@link EffectFactory} here under an
 * ability id (see {@code DMain.onEnable}) and reference the id from
 * {@code items.yml} / {@code abilities.yml}.
 */
public final class BukkitEffectRegistry {

    private static final Map<String, EffectFactory> FACTORIES = new HashMap<>();

    private BukkitEffectRegistry() {
    }

    public static void register(String abilityId, EffectFactory factory) {
        if (abilityId != null && factory != null) {
            FACTORIES.put(abilityId, factory);
        }
    }

    public static Effect create(String abilityId, String cooldownKey) {
        EffectFactory factory = FACTORIES.get(abilityId);
        if (factory == null) {
            System.out.println("No effect registered for ability id: " + abilityId);
            return null;
        }
        return factory.create(cooldownKey);
    }
}