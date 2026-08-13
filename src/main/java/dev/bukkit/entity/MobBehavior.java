package dev.bukkit.entity;

import org.bukkit.entity.LivingEntity;

/**
 * Java-side logic for a dungeon mob, referenced from {@code dungeon-mobs.yml} by a
 * {@code behavior} id. This is the Java half of the hybrid miniboss model: the
 * mob's stats, equipment, and spawn rules stay in config, while any unique
 * mechanics (phases, custom attacks, on-death effects — like Hypixel Skyblock's
 * Lost Adventurer) live here.
 *
 * <p>
 * Behaviors are singletons registered in {@link MobBehaviorRegistry} and shared
 * across every mob of that id, so all per-mob state must be kept on the supplied
 * {@code mob}/{@code vanilla} arguments (e.g. on the entity's metadata), never on
 * the behavior instance. All hooks are no-ops by default.
 */
public interface MobBehavior {

    /**
     * Called once right after the mob spawns, once its stats, equipment, and boss
     * bar have been applied. Use for spawn-time setup (markers, initial buffs).
     */
    default void onSpawn(MobRPGEntity mob, LivingEntity vanilla) {
    }

    /**
     * Called every game tick while the mob is alive. Use for phases, custom attack
     * cadences, or per-tick effects.
     */
    default void onTick(MobRPGEntity mob, LivingEntity vanilla, long now) {
    }

    /**
     * Called when the mob dies, before its RPG facade is removed. Use for death
     * effects, loot, or cleanup of any per-mob state.
     */
    default void onDeath(MobRPGEntity mob, LivingEntity vanilla) {
    }
}
