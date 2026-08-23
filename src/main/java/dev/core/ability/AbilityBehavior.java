package dev.core.ability;

/**
 * Bukkit-side (or other platform) behavior for an ability/set-passive.
 * Lives in {@code dev.core} as an interface so the core ability system can
 * bind it without depending on Bukkit. Implementations reside in
 * {@code dev.bukkit.ability.behavior} and are registered at startup via
 * {@link AbilityBehaviorRegistry}.
 *
 * <p>One instance per {@link ActiveAbility} (per holder). Created at equip
 * time, destroyed at unequip/quit/death.</p>
 */
public interface AbilityBehavior {

    default void onActivate(ActiveAbility ctx) {
    }

    default void onDeactivate(ActiveAbility ctx) {
    }
}
