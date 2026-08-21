package dev.core.ability;

/**
 * Creates the {@link Effect} that backs an ability at cast time. Binds a
 * (core) {@link Ability} definition to its in-game behavior in a single
 * registration: usage is
 * {@code AbilityRegistry.register(ability, effectFactory)} (see
 * {@code DMain.onEnable}). The factory receives the cooldown key computed for
 * this cast so item-scoped effects can track their own instance.
 */
@FunctionalInterface
public interface EffectFactory {

	Effect create(String cooldownKey);
}